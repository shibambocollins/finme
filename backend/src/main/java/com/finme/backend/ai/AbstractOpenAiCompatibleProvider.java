package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * <p>
     * TOKENS_PER_EXTRACTED_ROW and FIXED_OUTPUT_OVERHEAD were revised on 2026-08-27 against
     * real measurements (see StatementTextChunker.ROWS_PER_CHUNK for the full table): actual
     * completion cost ran ~40-67 tokens per extracted row, not 130, and the true fixed
     * component was closer to 250 than 700. The original values were never wrong about
     * direction - "estimate high" was the right instinct to avoid truncation - but 130 was
     * roughly double the real cost, and since Groq counts the <em>requested</em> max_tokens
     * against the rate limit whether used or not, that overestimate was being paid in full on
     * every single chunk. Combined with a real 480-row statement needing 32 chunks at the old
     * ROWS_PER_CHUNK, the two overestimates compounded into a ~15-minute extraction that then
     * hit the frontend's polling timeout.
     */
    private static final int TOKENS_PER_EXTRACTED_ROW = 85;
    private static final int FIXED_OUTPUT_OVERHEAD = 250;
    private static final int MIN_COMPLETION_TOKENS = 800;
    private static final int MAX_COMPLETION_TOKENS = 4500;

    /** Enough for a few short sentences plus reasoning overhead - see recommend(). */
    private static final int RECOMMENDATION_COMPLETION_TOKENS = 1500;

    /** A manual entry describes one purchase, occasionally a handful. */
    private static final int MANUAL_ENTRY_COMPLETION_TOKENS = 1500;

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

    private final ProviderHttp http;
    private final String baseUrl;
    private final String model;
    private final String providerName;

    /**
     * The provider name is passed in rather than obtained from an abstract method. An earlier
     * shape called providerName() from this constructor to build the HTTP helper - which works
     * only because every subclass happens to return a string literal. A subclass that returned
     * a field would have seen null, because its own fields are not assigned until after the
     * superclass constructor completes. Name is data; treating it as data removes the trap.
     */
    protected AbstractOpenAiCompatibleProvider(
            RestClient restClient, String baseUrl, String apiKey, String model, String providerName) {
        this.http = new ProviderHttp(restClient, apiKey, providerName);
        this.baseUrl = baseUrl;
        this.model = model;
        this.providerName = providerName;
    }

    /**
     * Provider-specific request fields merged into the body. Empty by default; GroqProvider
     * uses it to turn reasoning effort down on gpt-oss models.
     */
    protected Map<String, Object> extraRequestFields() {
        return Map.of();
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

    @Override
    public List<ExtractedTransaction> parseManualEntry(String naturalLanguage, LocalDate today) {
        // One sentence in, at most a few transactions out - a small fixed budget is plenty.
        String content = chatCompletion(
                AiExtractionSupport.buildManualEntryPrompt(naturalLanguage, today),
                MANUAL_ENTRY_COMPLETION_TOKENS);
        return AiExtractionSupport.parseTransactions(content);
    }

    @Override
    public List<String> recommendCredit(String creditFactsSummary) {
        String content = chatCompletion(
                AiExtractionSupport.buildCreditAnalysisPrompt(creditFactsSummary),
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

        String responseBody = http.post(baseUrl, requestBody);
        return AiExtractionSupport.extractOpenAiMessageContent(responseBody, providerName);
    }
}
