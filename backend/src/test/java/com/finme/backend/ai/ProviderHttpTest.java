package com.finme.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Groq enforces two independent 429 limits - a per-minute token budget the retry loop was built
 * to wait out, and a separate, far-slower per-hour request-count budget discovered 2026-08-27
 * when a real statement's chunk was told to wait 638 seconds. These tests are what would have
 * caught that before it reached a live account: they do not depend on actually triggering the
 * rare, slow-clock limit.
 */
class ProviderHttpTest {

    private static HttpClientErrorException.TooManyRequests tooManyRequests(HttpHeaders headers, String body) {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        // HttpClientErrorException.create() returns the specific subclass for a known status -
        // asserting that here means a Spring upgrade that changed this would fail loudly in a
        // test, not silently produce a ClassCastException deep inside retryAfter().
        assertThat(ex).isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        return (HttpClientErrorException.TooManyRequests) ex;
    }

    // ------------------------------------------------------------------ retryAfter() parsing

    @Test
    void prefersTheRetryAfterHeaderWhenPresent() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "5");

        Duration wait = ProviderHttp.retryAfter(tooManyRequests(headers, "{}"));

        // A second is added on top of whatever is reported - resuming exactly on the reported
        // boundary tends to race the provider's own window and 429 again immediately.
        assertThat(wait).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void fallsBackToTheWaitEmbeddedInTheErrorBodyWhenNoHeaderIsPresent() {
        String body = "{\"error\":{\"message\":\"Please try again in 4.28s\"}}";

        Duration wait = ProviderHttp.retryAfter(tooManyRequests(new HttpHeaders(), body));

        assertThat(wait).isCloseTo(Duration.ofMillis(5280), Duration.ofMillis(50));
    }

    @Test
    void fallsBackToADefaultWaitWhenNeitherHeaderNorBodyStatesOne() {
        Duration wait = ProviderHttp.retryAfter(tooManyRequests(new HttpHeaders(), "{}"));

        assertThat(wait).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void parsesTheRealHourlyLimitWaitObservedLiveOnAnActualAccount() {
        // The exact shape of the failure this class exists to catch: a wait far longer than any
        // per-minute token budget could ever produce, because it is the hourly request-count
        // budget's clock, not the per-minute one.
        String body = "{\"error\":{\"message\":\"Rate limit reached ... Please try again in 638s\"}}";

        Duration wait = ProviderHttp.retryAfter(tooManyRequests(new HttpHeaders(), body));

        assertThat(wait).isCloseTo(Duration.ofSeconds(639), Duration.ofMillis(50));
    }

    // ------------------------------------------------------------------ the cap, end to end
    //
    // A real RestClient bound to Spring's own MockRestServiceServer, not a Mockito deep-stub of
    // RestClient's fluent interface. That was tried first and failed for reasons unrelated to
    // this class's logic: Mockito's RETURNS_DEEP_STUBS does not correctly chain through
    // RestClient.RequestBodySpec.header(String, String...), a varargs method - the mocked chain
    // silently returned null partway through, breaking every test that exercised a real
    // request. Driving an actual (mocked-server) HTTP exchange sidesteps that entirely, and is
    // more faithful besides: the 429-to-TooManyRequests translation comes from Spring's real
    // error handling, not from an exception this test hand-assembled to look like it.

    @Test
    void waitsOutAShortRateLimitRatherThanFailing() {
        // 429 once, then success - proving post() actually slept and retried, not that it
        // returned the first response verbatim or failed outright.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://example.test"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));
        server.expect(requestTo("http://example.test"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        ProviderHttp http = new ProviderHttp(builder.build(), "key", "TestProvider");

        assertThat(http.post("http://example.test", Map.of())).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void failsImmediatelyRatherThanSleepingThroughAWaitLongerThanTheCap() {
        // The actual bug fix: previously this slept for the full reported duration, trapping
        // execution inside one provider well past what the fallback chain should ever wait for
        // a single provider before moving on. Only one request is expected - proving post()
        // never even tries a second time once it decides the wait is too long.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://example.test"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Please try again in 638s\"}}"));

        ProviderHttp http = new ProviderHttp(builder.build(), "key", "TestProvider");

        long startedAt = System.currentTimeMillis();
        assertThatThrownBy(() -> http.post("http://example.test", Map.of()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("too long to wait out");
        long elapsedMs = System.currentTimeMillis() - startedAt;

        assertThat(elapsedMs)
                .as("a 638s wait must never actually be slept through")
                .isLessThan(2000);
        server.verify();
    }

    // No test drives post() through a wait that is honoured right at the 90s cap boundary -
    // ProviderHttp.sleep() performs a real Thread.sleep(), so proving that case end to end
    // would mean an actual 90-second unit test. The boundary comparison itself
    // (wait.compareTo(MAX_SINGLE_RATE_LIMIT_WAIT) > 0) is a single unambiguous operator with no
    // off-by-one risk worth a 90-second test to guard; failsImmediatelyRatherThanSleeping...
    // above proves the "too long" side, and waitsOutAShortRateLimit... proves the "honoured"
    // side at a wait small enough to actually run.
}
