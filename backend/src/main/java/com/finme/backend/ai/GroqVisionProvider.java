package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

public class GroqVisionProvider extends AbstractOpenAiCompatibleVisionProvider {

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";

    public GroqVisionProvider(RestClient restClient, String apiKey, String model) {
        super(restClient, BASE_URL, apiKey, model, "Groq Vision");
    }
}
