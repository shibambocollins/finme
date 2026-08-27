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
 * One POST, with the provider's rate limit respected.
 * <p>
 * This is a single class because the AI package previously had four independent
 * {@code restClient.post()} call sites - text, vision, and both Cloudflare variants - and only
 * one of them knew anything about rate limits. That is exactly how the vision path ended up
 * without the retry behaviour the text path had: the fix was applied where the bug was seen,
 * and the three copies elsewhere were invisible. Collapsing them means the next lesson learned
 * about a provider's behaviour is learned once, everywhere.
 */
final class ProviderHttp {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttp.class);

    /** "Please try again in 4.282499999s" - the wait Groq reports inside its 429 body. */
    private static final Pattern RETRY_HINT = Pattern.compile("try again in ([0-9.]+)s");
    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(20);
    private static final int MAX_RATE_LIMIT_RETRIES = 4;

    /**
     * The longest wait this class will ever sleep through in one attempt. Discovered
     * 2026-08-27: Groq enforces two independent 429 limits on two different clocks - a per-
     * <b>minute</b> token budget (the one the rest of this class was built around, header
     * {@code x-ratelimit-limit-tokens: 8000}, resets in well under a minute) and a separate
     * per-<b>hour</b> request-count budget ({@code x-ratelimit-limit-requests: 1000}, seen
     * resetting over an hour away). Every retry attempt is itself another request, so a chain
     * of 429s can push an account toward the hourly ceiling even while comfortably under the
     * token one - and when that happens, Groq's reported wait is tied to the slow clock, not
     * the fast one. Measured live: a 480-row statement's 12th chunk was told to wait 638
     * seconds - blowing past the frontend's own polling timeout for one chunk out of twelve.
     * <p>
     * Honouring an arbitrarily long wait defeats the fallback chain's entire purpose. The chain
     * exists so that a provider having a bad day costs a handoff to the next one, not a stall -
     * so a wait this class cannot honour quickly is treated as failure, not patience: the
     * exception propagates immediately and FallbackAiProviderChain moves on to OpenRouter,
     * which has its own, independent budget.
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
     * Posts the body, waiting and retrying when the provider says it is rate limited.
     * <p>
     * A 429 is not a failure - it is the provider saying "not yet". Measured on 2026-08-22,
     * extracting an 80-transaction statement as 6 chunks put roughly 16,000 tokens through a
     * free tier that allows 8000 per minute, so every chunk after the first came back 429
     * within milliseconds and the upload failed having extracted 18 of 80 transactions. Groq
     * states exactly how long to wait, so honouring that turns a hard failure into a pause.
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
     * body ("Please try again in 4.282499999s"), then to a fixed pause. A second is added to
     * whatever is found, because resuming exactly on the boundary tends to race the provider's
     * own window and 429 again.
     */
    /** Package-private (not private) purely so ProviderHttpTest can exercise it directly. */
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
