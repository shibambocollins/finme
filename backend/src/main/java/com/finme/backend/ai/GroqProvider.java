package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Verified against console.groq.com/docs as of 2026-08 - api.groq.com/openai/v1 is
 * OpenAI-compatible. Model defaults to openai/gpt-oss-20b (llama-3.1-8b-instant and
 * llama-3.3-70b-versatile were deprecated by Groq in June 2026), configurable via
 * groq.model / GROQ_MODEL if that changes again.
 */
public class GroqProvider extends AbstractOpenAiCompatibleProvider {

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final String model;

    public GroqProvider(RestClient restClient, String apiKey, String model) {
        super(restClient, BASE_URL, apiKey, model, "Groq");
        this.model = model;
    }

    /**
     * gpt-oss is a reasoning model, and its default reasoning budget is the single biggest
     * threat to a complete extraction - at default effort it can burn most of the token cap
     * "thinking" before it ever writes an answer. Transaction extraction is a transcription
     * task, not a reasoning one, so that budget is pure overhead; turning it down measurably
     * improved both completeness and speed. Sent only for gpt-oss models, since
     * reasoning_effort isn't a universal Groq parameter and would risk a 400 on others.
     */
    @Override
    protected Map<String, Object> extraRequestFields() {
        return model != null && model.contains("gpt-oss")
                ? Map.of("reasoning_effort", "low")
                : Map.of();
    }
}
