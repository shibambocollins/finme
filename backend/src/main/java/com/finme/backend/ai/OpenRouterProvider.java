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

    /**
     * A flat, generous budget rather than a row-scaled estimate like Groq's - deliberately, not
     * an oversight. A per-row formula was tried first, but repeat measurements of the same
     * chunk against this model's configured free tier disagreed by over 25%: its output length
     * isn't a variable worth fitting a tight formula to the way Groq's is. Unlike Groq,
     * OpenRouter doesn't penalise requesting more tokens than are used, so the safe design for
     * unpredictable verbosity is to stop predicting it - 12000 clears the highest measured need
     * with comfortable margin, confirmed as an OpenRouter-accepted value.
     */
    @Override
    protected int estimateCompletionTokens(String text) {
        return 12000;
    }

    /**
     * Unmeasured for this specific output shape - recommendations were not part of the 2026-08-27
     * measurement, which covered statement extraction only. Doubled from Groq's budget as a
     * precaution consistent with the extraction finding, not a verified number; if this model
     * still truncates a recommendation, that is the signal to go measure it properly rather than
     * guess again.
     */
    @Override
    protected int recommendationCompletionTokens() {
        return 3000;
    }

    @Override
    protected int manualEntryCompletionTokens() {
        return 3000;
    }
}
