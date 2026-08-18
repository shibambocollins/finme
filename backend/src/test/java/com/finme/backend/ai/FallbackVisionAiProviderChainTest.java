package com.finme.backend.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackVisionAiProviderChainTest {

    private static final ExtractedTransaction SAMPLE = new ExtractedTransaction(
            LocalDate.now(), "Corner Cafe", new BigDecimal("65.00"), "Dining", "test", "CARD");

    @Test
    void returnsFirstProviderResultWhenItSucceeds() {
        VisionAiProvider succeeding = (bytes, mimeType) -> List.of(SAMPLE);
        VisionAiProvider neverCalled = (bytes, mimeType) -> {
            throw new AssertionError("should not have been called");
        };

        FallbackVisionAiProviderChain chain = new FallbackVisionAiProviderChain(List.of(succeeding, neverCalled));

        assertThat(chain.extractFromImage(new byte[0], "image/jpeg")).containsExactly(SAMPLE);
    }

    @Test
    void fallsBackToNextProviderWhenFirstFails() {
        VisionAiProvider failing = (bytes, mimeType) -> {
            throw new AiProviderException("boom");
        };
        VisionAiProvider succeeding = (bytes, mimeType) -> List.of(SAMPLE);

        FallbackVisionAiProviderChain chain = new FallbackVisionAiProviderChain(List.of(failing, succeeding));

        assertThat(chain.extractFromImage(new byte[0], "image/jpeg")).containsExactly(SAMPLE);
    }

    @Test
    void triesEveryProviderInOrderBeforeGivingUp() {
        AtomicInteger callOrder = new AtomicInteger(0);
        int[] firstCalledAt = new int[1];
        int[] secondCalledAt = new int[1];
        int[] thirdCalledAt = new int[1];

        VisionAiProvider first = (bytes, mimeType) -> {
            firstCalledAt[0] = callOrder.incrementAndGet();
            throw new AiProviderException("first failed");
        };
        VisionAiProvider second = (bytes, mimeType) -> {
            secondCalledAt[0] = callOrder.incrementAndGet();
            throw new AiProviderException("second failed");
        };
        VisionAiProvider third = (bytes, mimeType) -> {
            thirdCalledAt[0] = callOrder.incrementAndGet();
            return List.of(SAMPLE);
        };

        FallbackVisionAiProviderChain chain = new FallbackVisionAiProviderChain(List.of(first, second, third));

        assertThat(chain.extractFromImage(new byte[0], "image/jpeg")).containsExactly(SAMPLE);
        assertThat(firstCalledAt[0]).isEqualTo(1);
        assertThat(secondCalledAt[0]).isEqualTo(2);
        assertThat(thirdCalledAt[0]).isEqualTo(3);
    }

    @Test
    void throwsAllFailedExceptionWhenEveryProviderFails() {
        VisionAiProvider failingA = (bytes, mimeType) -> {
            throw new AiProviderException("A failed");
        };
        VisionAiProvider failingB = (bytes, mimeType) -> {
            throw new AiProviderException("B failed");
        };

        FallbackVisionAiProviderChain chain = new FallbackVisionAiProviderChain(List.of(failingA, failingB));

        assertThatThrownBy(() -> chain.extractFromImage(new byte[0], "image/jpeg"))
                .isInstanceOf(AllAiProvidersFailedException.class)
                .hasCauseInstanceOf(AiProviderException.class)
                .cause().hasMessageContaining("B failed");
    }

    @Test
    void rejectsAnEmptyProviderList() {
        assertThatThrownBy(() -> new FallbackVisionAiProviderChain(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
