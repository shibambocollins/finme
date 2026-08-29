package com.finme.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One POST, with the provider's rate limit respected - shared by every provider so the retry
 * behaviour is applied everywhere the same way, not just where it was first needed.
 */
final class ProviderHttp {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttp.class);

    private static final Pattern RETRY_HINT = Pattern.compile("try again in ([0-9.]+)s");
    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(20);
    private static final int MAX_RATE_LIMIT_RETRIES = 4;

    /**
     * The longest wait this class will ever sleep through in one attempt. Groq enforces two
     * independent 429 limits on two different clocks - a fast per-minute token budget and a
     * much slower per-hour request-count budget - and a chain of retries is itself a chain of
     * requests, so it can trip the slow limit even while comfortably under the fast one. When
     * that happens the reported wait can run into minutes. Honouring an arbitrarily long wait
     * defeats the fallback chain's purpose - a provider having a bad day should cost a handoff,
     * not a stall - so a wait longer than this is treated as failure: it propagates immediately
     * and FallbackAiProviderChain moves on to a provider with its own, independent budget.
     */
    private static final Duration MAX_SINGLE_RATE_LIMIT_WAIT = Duration.ofSeconds(90);

    private final RestClient restClient;
    private final String apiKey;
    private final String providerName;

    ProviderHttp(RestClient restClient, String apiKey, String providerName) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.providerName = providerName;
    }

    /**
     * A 429 is not a failure - it's the provider saying "not yet", and it states exactly how
     * long to wait, so honouring that turns a hard failure into a pause. A free tier's
     * per-minute budget makes this routine on a multi-chunk statement, not an edge case.
     * <p>
     * Only 429 is retried. A 413 means this single request can never succeed as sent, so
     * retrying it would burn time before failing anyway - it propagates immediately, letting
     * the fallback chain try a provider with different limits.
     */
    String post(String url, Map<String, Object> requestBody) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restClient.post()
                        .uri(url)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (attempt > MAX_RATE_LIMIT_RETRIES) {
                    throw new AiProviderException(providerName
                            + " still rate limited after " + MAX_RATE_LIMIT_RETRIES + " retries", ex);
                }
                Duration wait = retryAfter(ex);
                if (wait.compareTo(MAX_SINGLE_RATE_LIMIT_WAIT) > 0) {
                    // A wait this long means the fast per-minute budget is not what tripped -
                    // sleeping through it would trap this call inside a provider the fallback
                    // chain exists specifically to move past.
                    log.info("{} rate limited for {}s - longer than this app will wait for one "
                                    + "provider, handing off to the next one instead",
                            providerName, wait.toSeconds());
                    throw new AiProviderException(providerName + " rate limited for "
                            + wait.toSeconds() + "s, too long to wait out on this provider", ex);
                }
                log.info("{} rate limited, waiting {}s before retry {} of {}",
                        providerName, wait.toSeconds(), attempt, MAX_RATE_LIMIT_RETRIES);
                sleep(wait);
            } catch (AiProviderException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new AiProviderException(providerName + " request failed", ex);
            }
        }
    }

    /**
     * Prefers the standard Retry-After header; falls back to the wait embedded in the error
     * body, then to a fixed pause. A second is added to whatever is found, because resuming
     * exactly on the boundary tends to race the provider's own window and 429 again.
     */
    static Duration retryAfter(HttpClientErrorException.TooManyRequests ex) {
        String header = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
        if (header != null) {
            try {
                return Duration.ofSeconds(Long.parseLong(header.trim()) + 1);
            } catch (NumberFormatException ignored) {
                // Retry-After may be an HTTP date; fall through to the body and default below.
            }
        }
        Matcher matcher = RETRY_HINT.matcher(ex.getResponseBodyAsString());
        if (matcher.find()) {
            double seconds = Double.parseDouble(matcher.group(1));
            return Duration.ofMillis((long) Math.ceil(seconds * 1000) + 1000);
        }
        return DEFAULT_RATE_LIMIT_WAIT;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while waiting out a provider rate limit", ex);
        }
    }
}
