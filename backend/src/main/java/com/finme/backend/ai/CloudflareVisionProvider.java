package com.finme.backend.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Verified against the real API, 2026-08-18. Request shape confirmed working exactly as a
 * flagged Cloudflare docs GitHub issue (developers/cloudflare-docs#19185) described: "image" as
 * a JSON array of unsigned byte values (not base64), and "prompt" (not "messages") - correctly
 * extracted a real synthetic receipt's merchant/date/amount/paymentMethod in testing. Response
 * shape confirmed too: "result.response" is already a parsed JSON object matching our schema,
 * not a string to re-parse - see AiExtractionSupport.extractCloudflareResult.
 * <p>
 * One-time account gate discovered during testing, not code-fixable: this model requires
 * accepting Meta's license before first use - the API returns 403 "Model Agreement" until a
 * one-off {"prompt": "agree"} request is sent to this same model/account. Already done for
 * this project's Cloudflare account; a fresh account/model swap would need it repeated once.
 * Model defaults to @cf/meta/llama-3.2-11b-vision-instruct, a Cloudflare-hosted model, matching
 * the text CloudflareProvider's free-Neuron-allocation reasoning.
 */
public class CloudflareVisionProvider implements VisionAiProvider {

    private final RestClient restClient;
    private final String accountId;
    private final String apiToken;
    private final String model;

    public CloudflareVisionProvider(RestClient restClient, String accountId, String apiToken, String model) {
        this.restClient = restClient;
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.model = model;
    }

    @Override
    public List<ExtractedTransaction> extractFromImage(byte[] imageBytes, String mimeType) {
        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/" + model;
        Map<String, Object> requestBody = Map.of(
                "image", toUnsignedByteArray(imageBytes),
                "prompt", AiExtractionSupport.buildReceiptPrompt()
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new AiProviderException("Cloudflare Vision request failed", ex);
        }

        String resultText = AiExtractionSupport.extractCloudflareResult(responseBody);
        String jsonObject = AiExtractionSupport.extractJsonObject(resultText);
        return AiExtractionSupport.parseTransactions(jsonObject);
    }

    private static int[] toUnsignedByteArray(byte[] bytes) {
        int[] result = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = bytes[i] & 0xFF;
        }
        return result;
    }
}
