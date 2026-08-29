package com.finme.backend.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic stand-in for a real provider call, so the full ingestion pipeline
 * (extract -> redact -> structure -> persist) can be built and tested without spending real API
 * quota on every run. Active by default and whenever ai.provider is unset or "mock"; set
 * ai.provider=chain (see AiProviderConfig) to route through the real Groq/OpenRouter/Cloudflare
 * fallback chain instead.
 */
@Component
@Primary
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    @Override
    public List<ExtractedTransaction> parseManualEntry(String naturalLanguage, java.time.LocalDate today) {
        if (naturalLanguage == null || naturalLanguage.isBlank()) {
            return List.of();
        }
        return List.of(new ExtractedTransaction(
                today, "Mock Cash Purchase", new BigDecimal("50.00"),
                "Other", naturalLanguage, "CASH", "DEBIT"));
    }

    @Override
    public List<String> recommendCredit(String creditFactsSummary) {
        if (creditFactsSummary == null || creditFactsSummary.isBlank()) {
            return List.of();
        }
        return List.of(
                "Mock credit step: bring your most stretched account down first.",
                "Mock credit step: keep every account paid on time.");
    }

    @Override
    public List<String> recommend(String spendFactsSummary) {
        if (spendFactsSummary == null || spendFactsSummary.isBlank()) {
            return List.of();
        }
        return List.of(
                "Mock recommendation: your largest category grew this month - review it first.",
                "Mock recommendation: set a target for next month and track against it.");
    }

    @Override
    public List<ExtractedTransaction> structureTransactions(String redactedText) {
        if (redactedText == null || redactedText.isBlank()) {
            return List.of();
        }
        return List.of(
                new ExtractedTransaction(
                        LocalDate.now().minusDays(3), "Woolworths", new BigDecimal("450.00"),
                        "Groceries", "Mock-extracted transaction"),
                new ExtractedTransaction(
                        LocalDate.now().minusDays(1), "Uber", new BigDecimal("85.50"),
                        "Transport", "Mock-extracted transaction")
        );
    }
}
