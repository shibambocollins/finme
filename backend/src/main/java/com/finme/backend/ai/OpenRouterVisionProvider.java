package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

public class OpenRouterVisionProvider extends AbstractOpenAiCompatibleVisionProvider {

    private static final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";

    public OpenRouterVisionProvider(RestClient restClient, String apiKey, String model) {
        super(restClient, BASE_URL, apiKey, model, "OpenRouter Vision");
    }
}
