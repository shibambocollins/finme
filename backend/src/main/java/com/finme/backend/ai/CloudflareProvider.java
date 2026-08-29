package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Uses a {"messages": [...]} chat-format request, not the plain {"prompt": "..."} shape - the
 * latter routes this model through a raw-completion endpoint that doesn't reliably follow
 * instructions. Still not the OpenAI-compatible shape other providers use (no "model" field, no
 * /v1/ path, different response envelope), so this doesn't extend
 * AbstractOpenAiCompatibleProvider.
 */
public class CloudflareProvider implements AiProvider {

    /**
     * Without an explicit max_tokens, this call ran on whatever Cloudflare's own unstated
     * default is - fine for a small chunk, but a larger one came back truncated as invalid JSON.
     */
    private static final int MAX_TOKENS = 4000;

    private final ProviderHttp http;
    private final String accountId;
    private final String model;

    public CloudflareProvider(RestClient restClient, String accountId, String apiToken, String model) {
        this.http = new ProviderHttp(restClient, apiToken, "Cloudflare Workers AI");
        this.accountId = accountId;
        this.model = model;
    }

    @Override
    public List<ExtractedTransaction> structureTransactions(String redactedText) {
        String json = run(AiExtractionSupport.buildStatementPrompt(redactedText));
        return AiExtractionSupport.parseTransactions(json);
    }

    @Override
    public List<String> recommend(String spendFactsSummary) {
        String json = run(AiExtractionSupport.buildRecommendationPrompt(spendFactsSummary));
        return AiExtractionSupport.parseRecommendations(json);
    }

    @Override
    public List<ExtractedTransaction> parseManualEntry(String naturalLanguage, LocalDate today) {
        String json = run(AiExtractionSupport.buildManualEntryPrompt(naturalLanguage, today));
        return AiExtractionSupport.parseTransactions(json);
    }

    @Override
    public List<String> recommendCredit(String creditFactsSummary) {
        String json = run(AiExtractionSupport.buildCreditAnalysisPrompt(creditFactsSummary));
        return AiExtractionSupport.parseRecommendations(json);
    }

    private String run(String prompt) {
        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/" + model;
        Map<String, Object> requestBody = Map.of(
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "max_tokens", MAX_TOKENS
        );

        String responseBody = http.post(url, requestBody);

        String resultText = AiExtractionSupport.extractCloudflareResult(responseBody);
        return AiExtractionSupport.extractJsonObject(resultText);
    }
}
