package com.finme.backend.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackAiProviderChainTest {

    private static final ExtractedTransaction SAMPLE = new ExtractedTransaction(
            LocalDate.now(), "Test Merchant", new BigDecimal("10.00"), "Other", "test");

    /**
     * Stubs the extraction half of AiProvider. These were plain lambdas until the interface
     * gained recommend() and stopped being functional; recommend() fails loudly here so a test
     * that reaches it by accident says so rather than quietly passing.
     */
    private static AiProvider extractsWith(Function<String, List<ExtractedTransaction>> behaviour) {
        return new AiProvider() {
            @Override
            public List<ExtractedTransaction> structureTransactions(String redactedText) {
                return behaviour.apply(redactedText);
            }

            @Override
            public List<String> recommend(String spendFactsSummary) {
                throw new AssertionError("recommend() should not have been called");
            }
        };
    }

    /** Stubs the narration half, with the same guard in the opposite direction. */
    private static AiProvider recommendsWith(Function<String, List<String>> behaviour) {
        return new AiProvider() {
            @Override
            public List<ExtractedTransaction> structureTransactions(String redactedText) {
                throw new AssertionError("structureTransactions() should not have been called");
            }

            @Override
            public List<String> recommend(String spendFactsSummary) {
                return behaviour.apply(spendFactsSummary);
            }
        };
    }

    @Test
    void returnsFirstProviderResultWhenItSucceeds() {
        AiProvider succeeding = extractsWith(redactedText -> List.of(SAMPLE));
        AiProvider neverCalled = extractsWith(redactedText -> {
            throw new AssertionError("should not have been called");
        });

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(succeeding, neverCalled));

        assertThat(chain.structureTransactions("text")).containsExactly(SAMPLE);
    }

    @Test
    void fallsBackToNextProviderWhenFirstFails() {
        AiProvider failing = extractsWith(redactedText -> {
            throw new AiProviderException("boom");
        });
        AiProvider succeeding = extractsWith(redactedText -> List.of(SAMPLE));

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(failing, succeeding));

        assertThat(chain.structureTransactions("text")).containsExactly(SAMPLE);
    }

    @Test
    void triesEveryProviderInOrderBeforeGivingUp() {
        AtomicInteger callOrder = new AtomicInteger(0);
        int[] firstCalledAt = new int[1];
        int[] secondCalledAt = new int[1];
        int[] thirdCalledAt = new int[1];

        AiProvider first = extractsWith(redactedText -> {
            firstCalledAt[0] = callOrder.incrementAndGet();
            throw new AiProviderException("first failed");
        });
        AiProvider second = extractsWith(redactedText -> {
            secondCalledAt[0] = callOrder.incrementAndGet();
            throw new AiProviderException("second failed");
        });
        AiProvider third = extractsWith(redactedText -> {
            thirdCalledAt[0] = callOrder.incrementAndGet();
            return List.of(SAMPLE);
        });

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(first, second, third));

        assertThat(chain.structureTransactions("text")).containsExactly(SAMPLE);
        assertThat(firstCalledAt[0]).isEqualTo(1);
        assertThat(secondCalledAt[0]).isEqualTo(2);
        assertThat(thirdCalledAt[0]).isEqualTo(3);
    }

    @Test
    void throwsAllFailedExceptionWhenEveryProviderFails() {
        AiProvider failingA = extractsWith(redactedText -> {
            throw new AiProviderException("A failed");
        });
        AiProvider failingB = extractsWith(redactedText -> {
            throw new AiProviderException("B failed");
        });

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(failingA, failingB));

        assertThatThrownBy(() -> chain.structureTransactions("text"))
                .isInstanceOf(AllAiProvidersFailedException.class)
                .hasCauseInstanceOf(AiProviderException.class)
                .cause().hasMessageContaining("B failed");
    }

    @Test
    void recommendationsFallBackThroughTheChainToo() {
        AiProvider failing = recommendsWith(summary -> {
            throw new AiProviderException("recommend failed");
        });
        AiProvider succeeding = recommendsWith(summary -> List.of("Spend less on takeaways."));

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(failing, succeeding));

        assertThat(chain.recommend("facts")).containsExactly("Spend less on takeaways.");
    }

    @Test
    void throwsAllFailedExceptionWhenEveryProviderFailsToRecommend() {
        AiProvider failingA = recommendsWith(summary -> {
            throw new AiProviderException("A failed");
        });
        AiProvider failingB = recommendsWith(summary -> {
            throw new AiProviderException("B failed");
        });

        FallbackAiProviderChain chain = new FallbackAiProviderChain(List.of(failingA, failingB));

        assertThatThrownBy(() -> chain.recommend("facts"))
                .isInstanceOf(AllAiProvidersFailedException.class);
    }

    @Test
    void rejectsAnEmptyProviderList() {
        assertThatThrownBy(() -> new FallbackAiProviderChain(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
