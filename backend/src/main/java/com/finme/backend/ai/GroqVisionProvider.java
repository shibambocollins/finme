package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

/**
 * Verified against console.groq.com/docs/vision as of 2026-08 - qwen/qwen3.6-27b is the only
 * currently-documented vision-capable model on Groq, same OpenAI-compatible image_url shape
 * as the text chat completions API.
 */
public class GroqVisionProvider extends AbstractOpenAiCompatibleVisionProvider {

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";

    public GroqVisionProvider(RestClient restClient, String apiKey, String model) {
        super(restClient, BASE_URL, apiKey, model);
    }

    @Override
    protected String providerName() {
        return "Groq Vision";
    }
}
