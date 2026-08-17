package com.finme.backend.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Verified against developers.cloudflare.com/workers-ai as of 2026-08. Different shape from
 * the OpenAI-compatible providers - a plain "prompt" string, not a messages array, and no
 * confirmed JSON-mode - so this doesn't extend AbstractOpenAiCompatibleProvider. Model
 * defaults to @cf/meta/llama-3.1-8b-instruct, a Cloudflare-hosted model (not a proxied
 * third-party one), per docs/07-tech-stack.md's guidance to stay within the free Neuron
 * allocation.
 */
public class CloudflareProvider implements AiProvider {

    private final RestClient restClient;
    private final String accountId;
    private final String apiToken;
    private final String model;

    public CloudflareProvider(RestClient restClient, String accountId, String apiToken, String model) {
        this.restClient = restClient;
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.model = model;
    }

    @Override
    public List<ExtractedTransaction> structureTransactions(String redactedText) {
        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/" + model;
        Map<String, Object> requestBody = Map.of("prompt", AiExtractionSupport.buildPrompt(redactedText));

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
            throw new AiProviderException("Cloudflare Workers AI request failed", ex);
        }

        String resultText = AiExtractionSupport.extractCloudflareResult(responseBody);
        String jsonObject = AiExtractionSupport.extractJsonObject(resultText);
        return AiExtractionSupport.parseTransactions(jsonObject);
    }
}
