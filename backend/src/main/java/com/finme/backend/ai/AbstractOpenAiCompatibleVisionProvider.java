package com.finme.backend.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Groq and OpenRouter's vision models both accept the same OpenAI-compatible multi-part
 * content shape (a text block plus an image_url block) - verified against current docs, not
 * assumed, same as the text AbstractOpenAiCompatibleProvider this mirrors.
 */
abstract class AbstractOpenAiCompatibleVisionProvider implements VisionAiProvider {

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    protected AbstractOpenAiCompatibleVisionProvider(RestClient restClient, String baseUrl, String apiKey, String model) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    protected abstract String providerName();

    @Override
    public List<ExtractedTransaction> extractFromImage(byte[] imageBytes, String mimeType) {
        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", AiExtractionSupport.buildReceiptPrompt()),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                        )
                )),
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
