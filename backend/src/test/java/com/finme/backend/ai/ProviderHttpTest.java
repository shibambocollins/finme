package com.finme.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /** A RestClient whose fluent chain always throws the given exception from body(String.class). */
    @SuppressWarnings("unchecked")
    private static RestClient throwingClient(RuntimeException toThrow) {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        when(restClient.post().uri(any(String.class)).header(any(), any())
                .contentType(any()).body((Map<String, Object>) any())
                .retrieve().body(String.class))
                .thenThrow(toThrow);
        return restClient;
    }

    @Test
    void waitsOutAShortRateLimitRatherThanFailing() {
        // Fails with TooManyRequests exactly once, then a distinct RuntimeException on the
        // retry - proving post() actually slept and tried again, not that it gave up
        // immediately for an unrelated reason.
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "0");
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        RuntimeException secondCallMarker = new RuntimeException("second call reached here");
        when(restClient.post().uri(any(String.class)).header(any(), any())
                .contentType(any()).body((Map<String, Object>) any())
                .retrieve().body(String.class))
                .thenThrow(tooManyRequests(headers, "{}"))
                .thenThrow(secondCallMarker);

        ProviderHttp http = new ProviderHttp(restClient, "key", "TestProvider");

        assertThatThrownBy(() -> http.post("http://example.test", Map.of()))
                .as("a short wait should be slept through, reaching the second call")
                .isSameAs(secondCallMarker);
    }

    @Test
    void failsImmediatelyRatherThanSleepingThroughAWaitLongerThanTheCap() {
        // The actual bug fix: previously this slept for the full reported duration, trapping
        // execution inside one provider well past what the fallback chain should ever wait for
        // a single provider before moving on.
        String body = "{\"error\":{\"message\":\"Please try again in 638s\"}}";
        RestClient restClient = throwingClient(tooManyRequests(new HttpHeaders(), body));
        ProviderHttp http = new ProviderHttp(restClient, "key", "TestProvider");

        long startedAt = System.currentTimeMillis();
        assertThatThrownBy(() -> http.post("http://example.test", Map.of()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("too long to wait out");
        long elapsedMs = System.currentTimeMillis() - startedAt;

        assertThat(elapsedMs)
                .as("a 638s wait must never actually be slept through")
                .isLessThan(2000);
    }

    @Test
    void aWaitRightAtTheCapIsStillHonoured() {
        // The cap is a "too long", not "not exactly this value" - confirms the boundary is
        // exclusive of the cap itself rather than accidentally off by one in either direction.
        String body = "{\"error\":{\"message\":\"Please try again in 89s\"}}"; // -> 90s after +1s
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        RuntimeException secondCallMarker = new RuntimeException("second call reached here");
        when(restClient.post().uri(any(String.class)).header(any(), any())
                .contentType(any()).body((Map<String, Object>) any())
                .retrieve().body(String.class))
                .thenThrow(tooManyRequests(new HttpHeaders(), body))
                .thenThrow(secondCallMarker);

        ProviderHttp http = new ProviderHttp(restClient, "key", "TestProvider");

        assertThatThrownBy(() -> http.post("http://example.test", Map.of()))
                .as("90s is the cap itself, not over it - still worth waiting out")
                .isSameAs(secondCallMarker);
    }
}
