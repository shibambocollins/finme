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
        super(restClient, BASE_URL, apiKey, model);
        this.model = model;
    }

    /**
     * gpt-oss is a reasoning model, and on this task its default reasoning budget is the
     * single biggest threat to a complete extraction. Measured live on a 16-transaction
     * statement (2026-08-21): at the default effort it burned 1947 completion tokens thinking
     * and got 1 transaction out; at "low" it used 489 and got all 16, in less wall-clock time.
     * Transaction extraction is a transcription task, not a reasoning one - there is nothing
     * here worth deliberating over, so the reasoning budget is pure overhead competing with
     * the answer for the same token cap.
     * <p>
     * Sent only for gpt-oss models: reasoning_effort is not a universal Groq parameter, and
     * blindly attaching it to a non-reasoning model (e.g. a qwen build) risks a 400 that would
     * knock this provider out of the chain for no reason.
     */
    @Override
    protected Map<String, Object> extraRequestFields() {
        return model != null && model.contains("gpt-oss")
                ? Map.of("reasoning_effort", "low")
                : Map.of();
    }

    @Override
    protected String providerName() {
        return "Groq";
    }
}
