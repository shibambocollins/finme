package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

/**
 * Verified against openrouter.ai/collections/vision-models as of 2026-08 - OpenAI-compatible
 * image_url shape. Free-tier model slugs churn weekly per OpenRouter's own docs (same caveat
 * as the text OpenRouterProvider) - reconfirm at openrouter.ai/models periodically.
 */
public class OpenRouterVisionProvider extends AbstractOpenAiCompatibleVisionProvider {

    private static final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";

    public OpenRouterVisionProvider(RestClient restClient, String apiKey, String model) {
        super(restClient, BASE_URL, apiKey, model, "OpenRouter Vision");
    }
}
