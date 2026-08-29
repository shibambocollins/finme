package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq and OpenRouter both expose the same OpenAI-compatible chat completions shape - this holds
 * the request/response handling both share so each subclass only supplies base URL, key, and
 * model.
 */
abstract class AbstractOpenAiCompatibleProvider implements AiProvider {

    /**
     * The output budget is sized to the input rather than fixed - too small silently truncates
     * extraction (a cut-off but still-valid JSON object with only some transactions in it); too
     * large gets the request rejected outright on a free tier, since Groq counts prompt tokens
     * plus requested max_tokens against the per-minute limit. Defaults below are Groq's own
     * measured cost per extracted row - overridable per subclass because OpenRouter's configured
     * model runs over 2x more verbose on identical input (see OpenRouterProvider), and a shared
     * budget tuned to Groq's numbers truncates there.
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

    protected int recommendationCompletionTokens() {
        return 1500;
    }

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

    protected AbstractOpenAiCompatibleProvider(
            RestClient restClient, String baseUrl, String apiKey, String model, String providerName) {
        this.http = new ProviderHttp(restClient, apiKey, providerName);
        this.baseUrl = baseUrl;
        this.model = model;
        this.providerName = providerName;
    }

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
        String content = chatCompletion(
                AiExtractionSupport.buildRecommendationPrompt(spendFactsSummary),
                recommendationCompletionTokens());
        return AiExtractionSupport.parseRecommendations(content);
    }

    @Override
    public List<ExtractedTransaction> parseManualEntry(String naturalLanguage, LocalDate today) {
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
