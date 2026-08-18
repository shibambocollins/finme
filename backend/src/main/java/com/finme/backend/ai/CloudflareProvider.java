package com.finme.backend.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Verified against the real API, 2026-08-18: a plain {"prompt": "..."} request routes this
 * model through a raw-completion endpoint that does NOT reliably follow instructions - in
 * testing it ignored the "respond with only JSON" instruction entirely and free-associated
 * unrelated statement-like lines instead of extracting the given text. Switching to a
 * {"messages": [...]} chat-format request (still not the OpenAI-compatible shape other
 * providers use - no "model" field, no /v1/ path, different response envelope - so this still
 * doesn't extend AbstractOpenAiCompatibleProvider) made it follow the instruction reliably.
 * Model defaults to @cf/meta/llama-3.1-8b-instruct, a Cloudflare-hosted model (not a proxied
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
        Map<String, Object> requestBody = Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", AiExtractionSupport.buildStatementPrompt(redactedText)
                ))
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
            throw new AiProviderException("Cloudflare Workers AI request failed", ex);
        }

        String resultText = AiExtractionSupport.extractCloudflareResult(responseBody);
        String jsonObject = AiExtractionSupport.extractJsonObject(resultText);
        return AiExtractionSupport.parseTransactions(jsonObject);
    }
}
