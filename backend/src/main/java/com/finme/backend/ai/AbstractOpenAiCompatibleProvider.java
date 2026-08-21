package com.finme.backend.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

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
     * Without an explicit cap the provider applies its own default, which on Groq is 2048 -
     * and that default silently destroyed extractions. Verified live against Groq
     * (openai/gpt-oss-20b, 2026-08-21) on a 16-transaction statement: the model spent 1947 of
     * its 2048 completion tokens on internal reasoning, emitted a *syntactically valid* JSON
     * object containing only the first transaction, and returned finish_reason "length". The
     * old code parsed that happily - no exception, no fallback - so a 16-transaction statement
     * became 1. 8000 leaves room for roughly 90-100 transactions after reasoning overhead; the
     * finish_reason guard below catches anything past that instead of trusting a short answer.
     */
    private static final int MAX_COMPLETION_TOKENS = 8000;

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

    @Override
    public List<ExtractedTransaction> structureTransactions(String redactedText) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", AiExtractionSupport.buildStatementPrompt(redactedText))));
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", MAX_COMPLETION_TOKENS);
        requestBody.putAll(extraRequestFields());

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new AiProviderException(providerName() + " request failed", ex);
        }

        String content = AiExtractionSupport.extractOpenAiMessageContent(responseBody, providerName());
        return AiExtractionSupport.parseTransactions(content);
    }
}
