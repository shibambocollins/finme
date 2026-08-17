package com.finme.backend.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic stand-in for a real provider call, so the full ingestion pipeline
 * (extract -> redact -> structure -> persist) can be built and tested without spending real
 * API quota on every run. Active by default and whenever ai.provider is unset or "mock"; set
 * ai.provider=chain (see AiProviderConfig) to route through the real Groq/OpenRouter/
 * Cloudflare fallback chain instead. The two conditions are mutually exclusive on the same
 * property, so there's never bean ambiguity for a plain AiProvider injection point.
 */
@Component
@Primary
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
