package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Request shape matches a flagged Cloudflare docs GitHub issue (developers/cloudflare-docs#19185),
 * not the (wrong) published docs: "image" as a JSON array of unsigned byte values, not base64,
 * and "prompt" rather than "messages". Response is parsed the same way as the text
 * CloudflareProvider - see AiExtractionSupport.extractCloudflareResult.
 * <p>
 * This model requires accepting Meta's license before first use - the API returns 403 "Model
 * Agreement" until a one-off {"prompt": "agree"} request is sent to this same model/account.
 * Not code-fixable, and not a recurring issue once done; a fresh account or model swap would
 * need it repeated once. Model defaults to @cf/meta/llama-3.2-11b-vision-instruct, a
 * Cloudflare-hosted model, matching the text CloudflareProvider's free-allocation reasoning.
 */
public class CloudflareVisionProvider implements VisionAiProvider {

    private final ProviderHttp http;
    private final String accountId;
    private final String model;

    public CloudflareVisionProvider(RestClient restClient, String accountId, String apiToken, String model) {
        this.http = new ProviderHttp(restClient, apiToken, "Cloudflare Vision");
        this.accountId = accountId;
        this.model = model;
    }

    @Override
    public List<ExtractedTransaction> extractFromImage(byte[] imageBytes, String mimeType) {
        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/" + model;
        Map<String, Object> requestBody = Map.of(
                "image", toUnsignedByteArray(imageBytes),
                "prompt", AiExtractionSupport.buildReceiptPrompt()
        );

        String responseBody = http.post(url, requestBody);

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
