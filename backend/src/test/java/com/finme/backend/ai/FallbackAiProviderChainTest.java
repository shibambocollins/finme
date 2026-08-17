package com.finme.backend.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackAiProviderChainTest {

    private static final ExtractedTransaction SAMPLE = new ExtractedTransaction(
            LocalDate.now(), "Test Merchant", new BigDecimal("10.00"), "Other", "test");

    @Test
    void returnsFirstProviderResultWhenItSucceeds() {
        AiProvider succeeding = redactedText -> List.of(SAMPLE);
        AiProvider neverCalled = redactedText -> {
            throw new AssertionError("should not have been called");
        };

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(succeeding, neverCalled));

        assertThat(chain.structureTransactions("text")).containsExactly(SAMPLE);
    }

    @Test
    void fallsBackToNextProviderWhenFirstFails() {
        AiProvider failing = redactedText -> {
            throw new AiProviderException("boom");
        };
        AiProvider succeeding = redactedText -> List.of(SAMPLE);

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(failing, succeeding));

        assertThat(chain.structureTransactions("text")).containsExactly(SAMPLE);
    }

    @Test
    void triesEveryProviderInOrderBeforeGivingUp() {
        AtomicInteger callOrder = new AtomicInteger(0);
        int[] firstCalledAt = new int[1];
        int[] secondCalledAt = new int[1];
        int[] thirdCalledAt = new int[1];

        AiProvider first = redactedText -> {
            firstCalledAt[0] = callOrder.incrementAndGet();
            throw new AiProviderException("first failed");
        };
        AiProvider second = redactedText -> {
            secondCalledAt[0] = callOrder.incrementAndGet();
            throw new AiProviderException("second failed");
        };
        AiProvider third = redactedText -> {
            thirdCalledAt[0] = callOrder.incrementAndGet();
            return List.of(SAMPLE);
        };

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(first, second, third));

        assertThat(chain.structureTransactions("text")).containsExactly(SAMPLE);
        assertThat(firstCalledAt[0]).isEqualTo(1);
        assertThat(secondCalledAt[0]).isEqualTo(2);
        assertThat(thirdCalledAt[0]).isEqualTo(3);
    }

    @Test
    void throwsAllFailedExceptionWhenEveryProviderFails() {
        AiProvider failingA = redactedText -> {
            throw new AiProviderException("A failed");
        };
        AiProvider failingB = redactedText -> {
            throw new AiProviderException("B failed");
        };

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(failingA, failingB));

        assertThatThrownBy(() -> chain.structureTransactions("text"))
                .isInstanceOf(AllAiProvidersFailedException.class)
                .hasCauseInstanceOf(AiProviderException.class)
                .cause().hasMessageContaining("B failed");
    }

    @Test
    void rejectsAnEmptyProviderList() {
        assertThatThrownBy(() -> new FallbackAiProviderChain(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
