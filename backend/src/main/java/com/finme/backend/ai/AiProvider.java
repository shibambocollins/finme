package com.finme.backend.ai;

import java.util.List;

/**
 * AI Integration Layer boundary (docs/03-system-design.md Sec. 2). Every real-data code path
 * (statement/receipt extraction, categorization, credit narrative) goes through an
 * implementation of this interface, never a direct provider SDK call from business logic -
 * that's what keeps provider swaps and fallback ordering confined to one place.
 */
public interface AiProvider {

    List<ExtractedTransaction> structureTransactions(String redactedText);

    /**
     * Turns an already-computed spend summary into short, plain-language recommendations
     * (FR-1.7.4).
     * <p>
     * The input is finished arithmetic - totals, differences and percentages this application
     * calculated itself - and the model's job is strictly interpretation and prioritisation. It
     * is never asked to compute anything, which is what keeps FR-2.2.1 true for this feature.
     * <p>
     * This lives on AiProvider rather than in a parallel provider chain of its own so it
     * inherits what the extraction path already earned: the Groq to OpenRouter to Cloudflare
     * fallback ordering, and the rate-limit retry behaviour that free tiers make mandatory.
     */
    List<String> recommend(String spendFactsSummary);
}
