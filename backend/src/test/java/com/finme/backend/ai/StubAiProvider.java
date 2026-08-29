package com.finme.backend.ai;

import java.time.LocalDate;
import java.util.List;

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
