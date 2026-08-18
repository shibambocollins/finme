package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

/**
 * Verified against console.groq.com/docs as of 2026-08 - api.groq.com/openai/v1 is
 * OpenAI-compatible. Model defaults to openai/gpt-oss-20b (llama-3.1-8b-instant and
 * llama-3.3-70b-versatile were deprecated by Groq in June 2026), configurable via
 * groq.model / GROQ_MODEL if that changes again.
 */
public class GroqProvider extends AbstractOpenAiCompatibleProvider {

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";

    public GroqProvider(RestClient restClient, String apiKey, String model) {
        super(restClient, BASE_URL, apiKey, model);
    }

    @Override
    protected String providerName() {
        return "Groq";
    }
}
