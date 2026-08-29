package com.finme.backend.ai;

import org.springframework.web.client.RestClient;

public class OpenRouterProvider extends AbstractOpenAiCompatibleProvider {

    private static final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";

    public OpenRouterProvider(RestClient restClient, String apiKey, String model) {
        super(restClient, BASE_URL, apiKey, model, "OpenRouter");
    }

    /**
     * A flat, generous budget rather than a row-scaled estimate like Groq's - deliberately, not
     * an oversight. A per-row formula was tried first, but repeat measurements of the same chunk
     * against this model's configured free tier disagreed by over 25%: its output length isn't a
     * variable worth fitting a tight formula to the way Groq's is. Unlike Groq, OpenRouter
     * doesn't penalise requesting more tokens than are used, so the safe design for
     * unpredictable verbosity is to stop predicting it.
     */
    @Override
    protected int estimateCompletionTokens(String text) {
        return 12000;
    }

    @Override
    protected int recommendationCompletionTokens() {
        return 3000;
    }

    @Override
    protected int manualEntryCompletionTokens() {
        return 3000;
    }
}
