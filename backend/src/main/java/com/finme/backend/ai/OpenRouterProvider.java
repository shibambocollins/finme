package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

/**
 * Verified against openrouter.ai/docs as of 2026-08 - OpenAI-compatible chat completions.
 * Free-tier model slugs (":free" suffix) churn weekly per OpenRouter's own docs, so
 * openrouter.model / OPENROUTER_MODEL should be reconfirmed at openrouter.ai/models
 * periodically rather than trusted indefinitely - the default here is a snapshot, not a
 * guarantee.
 */
public class OpenRouterProvider extends AbstractOpenAiCompatibleProvider {

    private static final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";

    public OpenRouterProvider(RestClient restClient, String apiKey, String model) {
        super(restClient, BASE_URL, apiKey, model, "OpenRouter");
    }
}
