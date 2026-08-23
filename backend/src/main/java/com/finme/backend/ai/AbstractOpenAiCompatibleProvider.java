package com.finme.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groq and OpenRouter both expose the same OpenAI-compatible chat completions shape (verified
 * against their current docs, not assumed) - this holds the request/response handling both
 * share so each subclass only supplies base URL, key, and model.
 */
abstract class AbstractOpenAiCompatibleProvider implements AiProvider {

    /**
     * The output budget is sized to the input rather than fixed, and this is a correctness
     * concern in both directions.
     * <p>
     * Too small and extraction is silently truncated: measured live on 2026-08-21, Groq's own
     * 2048 default let gpt-oss-20b spend 1947 tokens reasoning and emit a *syntactically
     * valid* JSON object holding 1 of 16 transactions, which the old code parsed happily.
     * <p>
     * Too large and the request is refused before it starts: Groq's free tier limits tokens
     * per minute to 8000 and counts prompt tokens <b>plus the requested max_tokens</b> against
     * it, so a flat 8000 meant every call asked for 8797 and was rejected with HTTP 413 -
     * whatever the statement's actual size. Reserved headroom is not free; it is spent from
     * the rate limit whether the model needs it or not.
     * <p>
     * So: estimate what this specific text needs, and cap it low enough to leave room for the
     * prompt. Anything that still overruns is caught by the finish_reason guard rather than
     * being trusted.
     */
    private static final int TOKENS_PER_EXTRACTED_ROW = 130;
    private static final int FIXED_OUTPUT_OVERHEAD = 700;
    private static final int MIN_COMPLETION_TOKENS = 1200;
    private static final int MAX_COMPLETION_TOKENS = 5000;

    /** Enough for a few short sentences plus reasoning overhead - see recommend(). */
    private static final int RECOMMENDATION_COMPLETION_TOKENS = 1500;

    /**
     * Rough but deliberately generous row count - every non-blank line is treated as a
     * potential transaction, so headers and footers only ever inflate the estimate. Estimating
     * high is the safe direction here: the cost is rate-limit headroom, while estimating low
     * costs extracted transactions.
     */
    static int estimateCompletionTokens(String text) {
        long rows = text == null ? 0 : text.lines().filter(line -> !line.isBlank()).count();
        long estimate = FIXED_OUTPUT_OVERHEAD + rows * TOKENS_PER_EXTRACTED_ROW;
        return (int) Math.clamp(estimate, MIN_COMPLETION_TOKENS, MAX_COMPLETION_TOKENS);
    }

    private static final Logger log = LoggerFactory.getLogger(AbstractOpenAiCompatibleProvider.class);

    /** "Please try again in 4.282499999s" - the wait Groq reports inside its 429 body. */
    private static final Pattern RETRY_HINT = Pattern.compile("try again in ([0-9.]+)s");
    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(20);
    private static final int MAX_RATE_LIMIT_RETRIES = 4;

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    protected AbstractOpenAiCompatibleProvider(RestClient restClient, String baseUrl, String apiKey, String model) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    protected abstract String providerName();

    /**
     * Provider-specific request fields merged into the body. Empty by default; GroqProvider
     * uses it to turn reasoning effort down on gpt-oss models.
     */
    protected Map<String, Object> extraRequestFields() {
        return Map.of();
    }

    /**
     * Posts the request, waiting and retrying when the provider says it is rate limited.
     * <p>
     * A 429 here is not a failure - it is the provider saying "not yet". Measured on 2026-08-22,
     * extracting an 80-transaction statement as 6 chunks put roughly 16,000 tokens through a
     * free tier that allows 8000 per minute, so the 2nd chunk onward came back 429 within
     * milliseconds and the whole upload failed after extracting 18 of 80 transactions. Groq
     * states exactly how long to wait ("Please try again in 4.28s"), so honouring that turns a
     * hard failure into a pause.
     * <p>
     * Only 429 is retried. A 413 means the single request is too large to ever succeed, and
     * retrying it would just burn time before failing anyway - that propagates immediately so
     * the chain can try a provider with different limits.
     */
    private String postWithRateLimitRetry(Map<String, Object> requestBody) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restClient.post()
                        .uri(baseUrl)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (attempt > MAX_RATE_LIMIT_RETRIES) {
                    throw new AiProviderException(providerName()
                            + " still rate limited after " + MAX_RATE_LIMIT_RETRIES + " retries", ex);
                }
                Duration wait = retryAfter(ex);
                log.info("{} rate limited, waiting {}s before retry {} of {}",
                        providerName(), wait.toSeconds(), attempt, MAX_RATE_LIMIT_RETRIES);
                sleep(wait);
            } catch (AiProviderException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new AiProviderException(providerName() + " request failed", ex);
            }
        }
    }

    /**
     * Prefers the standard Retry-After header; falls back to the wait Groq embeds in its error
     * message ("Please try again in 4.282499999s"), then to a fixed pause. A second is added to
     * whatever is found, because resuming at the exact boundary tends to race the provider's
     * own window and 429 again.
     */
    private static Duration retryAfter(HttpClientErrorException.TooManyRequests ex) {
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

    @Override
    public List<ExtractedTransaction> structureTransactions(String redactedText) {
        String content = chatCompletion(
                AiExtractionSupport.buildStatementPrompt(redactedText),
                estimateCompletionTokens(redactedText));
        return AiExtractionSupport.parseTransactions(content);
    }

    @Override
    public List<String> recommend(String spendFactsSummary) {
        // A fixed, modest budget: the answer is a handful of one-sentence strings regardless of
        // how much spending the summary describes, and on a per-minute token budget an
        // over-generous reservation is spent whether or not it is used.
        String content = chatCompletion(
                AiExtractionSupport.buildRecommendationPrompt(spendFactsSummary),
                RECOMMENDATION_COMPLETION_TOKENS);
        return AiExtractionSupport.parseRecommendations(content);
    }

    /** One chat-completion round trip: build, send with rate-limit retry, unwrap the content. */
    private String chatCompletion(String prompt, int maxTokens) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", maxTokens);
        requestBody.putAll(extraRequestFields());

        String responseBody = postWithRateLimitRetry(requestBody);
        return AiExtractionSupport.extractOpenAiMessageContent(responseBody, providerName());
    }
}
