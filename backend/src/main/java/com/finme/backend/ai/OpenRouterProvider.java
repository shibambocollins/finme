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
     * Larger than Groq's default. Measured live 2026-08-27 against the free-tier model
     * configured for this project (nemotron-3-super-120b): the same 40-row chunk Groq's
     * gpt-oss-20b completed in 2380 tokens needed <b>5324</b> here - more than double. Groq's
     * shared budget truncated this provider mid-extraction (finish_reason=length) the moment a
     * chunk grew large enough to expose the gap; that failure is what led to sizing this
     * separately rather than trusting one number across two different models.
     * <p>
     * This is a single-point calibration, not a fitted curve like Groq's - only one chunk size
     * was measured here, not a range. fixedOutputOverhead is left at Groq's value on the
     * assumption it mostly reflects JSON-envelope structure common to both models, and the
     * per-row rate is solved from the one measurement then rounded up for margin the fitted
     * Groq numbers did not need: (5324 - 250) / 40 rows &asymp; 127/row, rounded up to 150.
     */
    @Override
    protected int tokensPerExtractedRow() {
        return 150;
    }

    @Override
    protected int maxCompletionTokens() {
        return 7500;
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
