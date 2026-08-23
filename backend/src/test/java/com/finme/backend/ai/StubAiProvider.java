package com.finme.backend.ai;

import java.time.LocalDate;
import java.util.List;

/**
 * Base for AiProvider test doubles: every method fails loudly, so a stub overrides only the one
 * capability its test is about.
 * <p>
 * This exists because AiProvider has now grown three times - extraction, then recommendations,
 * then manual entry - and each addition broke every hand-written stub across the suite, in a
 * way the compiler reported as five unrelated failures rather than one design change. Extending
 * this means the next capability added to the interface breaks exactly one file: this one.
 * <p>
 * Unimplemented methods throw rather than returning an empty list on purpose. A silent empty
 * return would let a test that accidentally calls the wrong path pass while asserting nothing,
 * which is worse than the compile error this class is replacing.
 */
public class StubAiProvider implements AiProvider {

    @Override
    public List<ExtractedTransaction> structureTransactions(String redactedText) {
        throw new AssertionError("structureTransactions() was not expected in this test");
    }

    @Override
    public List<String> recommend(String spendFactsSummary) {
        throw new AssertionError("recommend() was not expected in this test");
    }

    @Override
    public List<ExtractedTransaction> parseManualEntry(String naturalLanguage, LocalDate today) {
        throw new AssertionError("parseManualEntry() was not expected in this test");
    }

    @Override
    public List<String> recommendCredit(String creditFactsSummary) {
        throw new AssertionError("recommendCredit() was not expected in this test");
    }
}
