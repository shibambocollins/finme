package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq and OpenRouter's vision models both accept the same OpenAI-compatible multi-part
 * content shape (a text block plus an image_url block) - verified against current docs, not
 * assumed, same as the text AbstractOpenAiCompatibleProvider this mirrors.
 */
abstract class AbstractOpenAiCompatibleVisionProvider implements VisionAiProvider {

    /**
     * A receipt yields exactly one transaction, so a small fixed budget is enough - unlike
     * statement extraction, nothing here scales with input size. Set explicitly rather than
     * left unset, since an unset budget is what let a reasoning model burn its whole allowance
     * "thinking" before writing any JSON on the text path (see GroqProvider). 2000 leaves ample
     * room for one transaction while staying well under the free-tier per-minute ceiling.
     */
    private static final int RECEIPT_COMPLETION_TOKENS = 2000;

    private final ProviderHttp http;
    private final String baseUrl;
    private final String model;
    private final String providerName;

    protected AbstractOpenAiCompatibleVisionProvider(
            RestClient restClient, String baseUrl, String apiKey, String model, String providerName) {
        this.http = new ProviderHttp(restClient, apiKey, providerName);
        this.baseUrl = baseUrl;
        this.model = model;
        this.providerName = providerName;
    }

    /** Provider-specific request fields merged into the body. Empty by default. */
    protected Map<String, Object> extraRequestFields() {
        return Map.of();
    }

    @Override
    public List<ExtractedTransaction> extractFromImage(byte[] imageBytes, String mimeType) {
        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", AiExtractionSupport.buildReceiptPrompt()),
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                )
        )));
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", RECEIPT_COMPLETION_TOKENS);
        requestBody.putAll(extraRequestFields());

        String responseBody = http.post(baseUrl, requestBody);
        String content = AiExtractionSupport.extractOpenAiMessageContent(responseBody, providerName);
        return AiExtractionSupport.parseTransactions(content);
    }
}
