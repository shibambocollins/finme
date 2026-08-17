package com.finme.backend.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic stand-in for a real provider call. Lets the full ingestion pipeline
 * (extract -> redact -> structure -> persist) be built and tested end-to-end before a Groq
 * API key exists (see docs/07-tech-stack.md - AI provider is an open implementation choice
 * pending real key + rate-limit testing). Active by default; a GroqProvider implementing the
 * same AiProvider interface, selected via ai.provider=groq, is a fast-follow once a key exists
 * - never call an unverified provider API from here in the meantime.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

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
