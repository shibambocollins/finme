package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

import java.time.LocalDate;
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

    /**
     * Discovered missing entirely on 2026-08-27, not merely mistuned: this request carried no
     * {@code max_tokens} field at all, so every call ran on whatever Cloudflare's own unstated
     * default is for this model. A 15-row chunk apparently stayed under it; a 40-row chunk did
     * not, and the truncated response came back as invalid JSON ("Unexpected end-of-input"),
     * which is exactly what a cut-off array looks like. Verified Cloudflare honours the
     * parameter at all with a throwaway prompt (a 500-word request dropped from 1356 to 263
     * characters once max_tokens:50 was added) before trusting it here.
     * <p>
     * The real number: the same 40-row chunk that needed 2380 tokens on Groq and 5324 on
     * OpenRouter needed only <b>2153</b> here - close to Groq's, comfortably under this cap.
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

    /** One Workers AI round trip, returning the balanced JSON object found in the reply. */
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
