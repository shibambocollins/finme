package com.finme.backend.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic stand-in for a real vision provider call - same role as MockAiProvider, same
 * ai.provider switch (mock/chain), so both chains switch together rather than needing two
 * separate toggles.
 */
@Component
@Primary
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockVisionAiProvider implements VisionAiProvider {

    @Override
    public List<ExtractedTransaction> extractFromImage(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return List.of();
        }
        return List.of(new ExtractedTransaction(
                LocalDate.now(), "Corner Cafe", new BigDecimal("65.00"),
                "Dining", "Mock-extracted receipt", "CARD"));
    }
}
