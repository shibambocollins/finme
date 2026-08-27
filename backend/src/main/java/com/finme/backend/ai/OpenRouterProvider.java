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
     * an oversight.
     * <p>
     * A per-row formula was tried first: one measurement of a 40-row chunk (nemotron-3-super-120b,
     * this project's configured free model) showed 5324 completion tokens against Groq's 2380 for
     * identical content, so tokensPerExtractedRow() was set to 150 (250 + 40*150 = 6250, comfortable
     * margin over 5324 - or so it seemed). Applied live on 2026-08-27 against a real statement, every
     * single chunk still truncated (finish_reason=length). Re-measured with the exact real merchant
     * text (the first calibration had used shorter synthetic names, e.g. "WOOLWORTHS SANDTON"
     * instead of the real "WOOLWORTHS SANDTON CITY") and the true figure was <b>6701</b> - both
     * higher than the first measurement AND higher than the 6250 the formula had been computing.
     * <p>
     * Two measurements of the "same" 40-row chunk disagreeing by 26% is the real finding: this
     * model's output length is not a variable worth fitting a tight formula to from one or two
     * samples the way Groq's was (Groq's own formula came from six data points spanning 15-60
     * rows, not two). Unlike Groq, nothing here shows OpenRouter penalising a request for more
     * tokens than it uses - Groq's penalty was specific to Groq's TPM/TPD accounting, not a
     * general property of "requesting a large max_tokens". So the safe design for a model whose
     * verbosity is not reliably predictable is to stop trying to predict it: request generously
     * every time. 12000 clears the measured 6701 need by nearly 2x, and was confirmed live
     * (2026-08-27) as an OpenRouter-accepted value with no error at up to 16000.
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
