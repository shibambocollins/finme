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
     * The output budget is sized to the input rather than fixed - too small silently truncates
     * extraction (a cut-off but still-valid JSON object with only some transactions in it); too
     * large gets the request rejected outright on a free tier, since Groq counts prompt tokens
     * plus requested max_tokens against the per-minute limit. Defaults below are Groq's own
     * measured cost per extracted row, not a guess - overridable per subclass because
     * OpenRouter's configured model runs over 2x more verbose on identical input (see
     * OpenRouterProvider), and a shared budget tuned to Groq's numbers truncates there.
     */
    protected int tokensPerExtractedRow() {
        return 85;
    }

    protected int fixedOutputOverhead() {
        return 250;
    }

    protected int minCompletionTokens() {
        return 800;
    }

    protected int maxCompletionTokens() {
        return 4500;
    }

    /**
     * Enough for a few short sentences plus reasoning overhead - see recommend(). Overridable
     * for the same reason the extraction budget above is: a model that runs ~2.2x more verbose
     * on structured extraction is not assumed to be exactly as concise on a different task.
     */
    protected int recommendationCompletionTokens() {
        return 1500;
    }

    /** A manual entry describes one purchase, occasionally a handful. */
    protected int manualEntryCompletionTokens() {
        return 1500;
    }

    /**
     * Rough but deliberately generous row count - every non-blank line is treated as a
     * potential transaction, so headers and footers only ever inflate the estimate. Estimating
     * high is the safe direction here: the cost is rate-limit headroom, while estimating low
     * costs extracted transactions.
     */
    protected int estimateCompletionTokens(String text) {
        long rows = text == null ? 0 : text.lines().filter(line -> !line.isBlank()).count();
        long estimate = fixedOutputOverhead() + rows * tokensPerExtractedRow();
        return (int) Math.clamp(estimate, minCompletionTokens(), maxCompletionTokens());
    }

    private final ProviderHttp http;
    private final String baseUrl;
    private final String model;
    private final String providerName;

    /**
     * The provider name is passed in rather than obtained by calling an abstract method from
     * this constructor - a subclass's own fields aren't assigned until after the superclass
     * constructor returns, so a method backed by a field (rather than a string literal) would
     * see null here. Treating the name as plain data avoids the trap entirely.
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
                recommendationCompletionTokens());
        return AiExtractionSupport.parseRecommendations(content);
    }

    @Override
    public List<ExtractedTransaction> parseManualEntry(String naturalLanguage, LocalDate today) {
        // One sentence in, at most a few transactions out - a small fixed budget is plenty.
        String content = chatCompletion(
                AiExtractionSupport.buildManualEntryPrompt(naturalLanguage, today),
                manualEntryCompletionTokens());
        return AiExtractionSupport.parseTransactions(content);
    }

    @Override
    public List<String> recommendCredit(String creditFactsSummary) {
        String content = chatCompletion(
                AiExtractionSupport.buildCreditAnalysisPrompt(creditFactsSummary),
                recommendationCompletionTokens());
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
