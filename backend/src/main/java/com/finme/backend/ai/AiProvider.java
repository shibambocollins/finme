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
}
