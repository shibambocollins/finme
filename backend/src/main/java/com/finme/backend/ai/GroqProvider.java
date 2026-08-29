package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

import java.util.Map;

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
     * task, not a reasoning one, so turning that budget down measurably improved both
     * completeness and speed. Sent only for gpt-oss models, since reasoning_effort isn't a
     * universal Groq parameter and would risk a 400 on others.
     */
    @Override
    protected Map<String, Object> extraRequestFields() {
        return model != null && model.contains("gpt-oss")
                ? Map.of("reasoning_effort", "low")
                : Map.of();
    }
}
