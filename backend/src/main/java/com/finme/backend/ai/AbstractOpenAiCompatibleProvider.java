package com.finme.backend.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Groq and OpenRouter both expose the same OpenAI-compatible chat completions shape (verified
 * against their current docs, not assumed) - this holds the request/response handling both
 * share so each subclass only supplies base URL, key, and model.
 */
abstract class AbstractOpenAiCompatibleProvider implements AiProvider {

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

    @Override
    public List<ExtractedTransaction> structureTransactions(String redactedText) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", AiExtractionSupport.buildStatementPrompt(redactedText))),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.1
        );

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
