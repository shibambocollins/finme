package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.AiProviderException;
import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.StubAiProvider;
import com.finme.backend.dto.RecommendationsResponse;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final SpendAnalysisService spendAnalysisService = new SpendAnalysisService(transactionRepository);

    private final AtomicInteger recommendCalls = new AtomicInteger();
    private final AtomicReference<String> lastPrompt = new AtomicReference<>();

    private AiProvider recordingProvider(List<String> reply) {
        return new StubAiProvider() {
            @Override
            public List<String> recommend(String spendFactsSummary) {
                recommendCalls.incrementAndGet();
                lastPrompt.set(spendFactsSummary);
                return reply;
            }
        };
    }

    private AiProvider failingProvider() {
        return new StubAiProvider() {
            @Override
            public List<String> recommend(String spendFactsSummary) {
                throw new AllAiProvidersFailedException(new AiProviderException("all down"));
            }
        };
    }

    private static Transaction debit(LocalDate date, String amount, String category) {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setSourceType(SourceType.STATEMENT);
        t.setDate(date);
        t.setMerchant("Woolworths Sandton");
        t.setAmount(new BigDecimal(amount));
        t.setCategory(category);
        t.setPaymentMethod(PaymentMethod.CARD);
        t.setStatus(TransactionStatus.ACTIVE);
        t.setDirection(TransactionDirection.DEBIT);
        return t;
    }

    private void givenTransactions(Transaction... transactions) {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(transactions));
    }

    @Test
    void returnsRecommendationsFromTheProvider() {
        givenTransactions(debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));
        RecommendationService service = new RecommendationService(
                spendAnalysisService, recordingProvider(List.of("Cut groceries.", "Set a target.")));

        RecommendationsResponse response = service.getRecommendations(1L);

        assertThat(response.recommendations()).containsExactly("Cut groceries.", "Set a target.");
        assertThat(response.unavailableReason()).isNull();
    }

    @Test
    void neverSendsRawTransactionDetailToTheProvider() {
        givenTransactions(debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));
        RecommendationService service = new RecommendationService(
                spendAnalysisService, recordingProvider(List.of("ok")));

        service.getRecommendations(1L);

        assertThat(lastPrompt.get()).contains("Groceries", "500.00");
        assertThat(lastPrompt.get()).doesNotContain("Woolworths Sandton");
    }

    @Test
    void doesNotCallTheProviderAgainWhileTheUnderlyingFiguresAreUnchanged() {
        // The dashboard requests these on every page load, and the free tiers this runs on
        // share one per-minute token budget with statement extraction.
        givenTransactions(debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));
        RecommendationService service = new RecommendationService(
                spendAnalysisService, recordingProvider(List.of("Cut groceries.")));

        service.getRecommendations(1L);
        service.getRecommendations(1L);
        service.getRecommendations(1L);

        assertThat(recommendCalls.get()).isEqualTo(1);
    }

    @Test
    void regeneratesWhenTheFiguresChange() {
        givenTransactions(debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));
        RecommendationService service = new RecommendationService(
                spendAnalysisService, recordingProvider(List.of("Cut groceries.")));
        service.getRecommendations(1L);

        givenTransactions(
                debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"),
                debit(LocalDate.of(2026, 7, 9), "220.00", "Dining"));
        service.getRecommendations(1L);

        assertThat(recommendCalls.get()).isEqualTo(2);
    }

    @Test
    void explainsItselfWhenThereIsNoDataYet() {
        givenTransactions();
        RecommendationService service = new RecommendationService(
                spendAnalysisService, recordingProvider(List.of("never reached")));

        RecommendationsResponse response = service.getRecommendations(1L);

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.unavailableReason()).contains("Upload a statement");
        assertThat(recommendCalls.get()).isZero();
    }

    @Test
    void degradesInsteadOfFailingWhenEveryProviderIsDown() {
        // The dashboard's own figures are computed locally and stay correct; losing the
        // commentary must not take the page with it.
        givenTransactions(debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));
        RecommendationService service = new RecommendationService(spendAnalysisService, failingProvider());

        RecommendationsResponse response = service.getRecommendations(1L);

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.unavailableReason()).contains("unavailable");
    }
}
