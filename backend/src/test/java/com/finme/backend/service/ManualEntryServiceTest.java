package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.AiProviderException;
import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.ai.StubAiProvider;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.ManualEntryException;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualEntryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 20);
    private static final Clock FIXED =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final AtomicReference<LocalDate> dateGivenToProvider = new AtomicReference<>();
    private final AtomicReference<String> textGivenToProvider = new AtomicReference<>();

    private AiProvider providerReturning(BiFunction<String, LocalDate, List<ExtractedTransaction>> behaviour) {
        return new StubAiProvider() {
            @Override
            public List<ExtractedTransaction> parseManualEntry(String naturalLanguage, LocalDate today) {
                textGivenToProvider.set(naturalLanguage);
                dateGivenToProvider.set(today);
                return behaviour.apply(naturalLanguage, today);
            }
        };
    }

    private ManualEntryService serviceWith(AiProvider provider) {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new ManualEntryService(transactionRepository, provider, FIXED);
    }

    private static ExtractedTransaction extracted(String merchant, String amount, String paymentMethod, LocalDate date) {
        return new ExtractedTransaction(date, merchant, new BigDecimal(amount), "Dining",
                "lunch", paymentMethod, "DEBIT");
    }

    @Test
    void savesADescribedPurchaseAsAManualCashTransaction() {
        ManualEntryService service = serviceWith(providerReturning(
                (text, today) -> List.of(extracted("Lunch", "150.00", "CASH", today))));

        List<Transaction> saved = service.log(1L, "I bought lunch for R150 today, paid cash");

        assertThat(saved).hasSize(1);
        Transaction transaction = saved.get(0);
        assertThat(transaction.getSourceType()).isEqualTo(SourceType.MANUAL);
        assertThat(transaction.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(transaction.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.ACTIVE);
        assertThat(transaction.getAmount()).isEqualByComparingTo("150.00");
        assertThat(transaction.getSourceId()).isNull();
    }

    @Test
    void givesTheProviderTodaysDateSoRelativeDatesResolveAgainstTheAppsClock() {
        // Without this the model resolves "yesterday" against a date it guessed, and the
        // transaction lands in whatever month that guess implies.
        ManualEntryService service = serviceWith(providerReturning(
                (text, today) -> List.of(extracted("Coffee", "30.00", "CASH", today.minusDays(1)))));

        service.log(1L, "coffee R30 yesterday");

        assertThat(dateGivenToProvider.get()).isEqualTo(TODAY);
        assertThat(textGivenToProvider.get()).isEqualTo("coffee R30 yesterday");
    }

    @Test
    void defaultsToCashWhenThePaymentMethodIsNotStated() {
        // The whole point of this feature is spending that leaves no other record. Defaulting to
        // CARD would also expose it to duplicate-supersession against unrelated statement lines.
        ManualEntryService service = serviceWith(providerReturning(
                (text, today) -> List.of(extracted("Taxi", "25.00", null, today))));

        assertThat(service.log(1L, "taxi R25").get(0).getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void defaultsToCashWhenTheProviderReturnsSomethingUnrecognisable() {
        ManualEntryService service = serviceWith(providerReturning(
                (text, today) -> List.of(extracted("Taxi", "25.00", "bitcoin", today))));

        assertThat(service.log(1L, "taxi R25").get(0).getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void honoursAnExplicitlyStatedCardPayment() {
        ManualEntryService service = serviceWith(providerReturning(
                (text, today) -> List.of(extracted("Groceries", "310.00", "CARD", today))));

        assertThat(service.log(1L, "groceries R310 on my card").get(0).getPaymentMethod())
                .isEqualTo(PaymentMethod.CARD);
    }

    @Test
    void savesEveryPurchaseWhenTheUserDescribesMoreThanOne() {
        ManualEntryService service = serviceWith(providerReturning((text, today) -> List.of(
                extracted("Lunch", "150.00", "CASH", today),
                extracted("Parking", "20.00", "CASH", today))));

        assertThat(service.log(1L, "lunch R150 and parking R20")).hasSize(2);
    }

    @Test
    void rejectsBlankInputWithoutCallingAProvider() {
        ManualEntryService service = serviceWith(providerReturning((text, today) -> {
            throw new AssertionError("should not have been called");
        }));

        assertThatThrownBy(() -> service.log(1L, "   "))
                .isInstanceOf(ManualEntryException.class)
                .hasMessageContaining("Describe what you spent");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void storesNothingWhenNoPurchaseCouldBeFoundInTheText() {
        // Saving a guess would be worse than failing: a wrong transaction is harder to notice
        // and undo than an entry that plainly did not work.
        ManualEntryService service = serviceWith(providerReturning((text, today) -> List.of()));

        assertThatThrownBy(() -> service.log(1L, "what is the weather like"))
                .isInstanceOf(ManualEntryException.class)
                .hasMessageContaining("Could not find a purchase");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void reportsAProviderOutageAsSomethingTheUserCanRetry() {
        ManualEntryService service = serviceWith(providerReturning((text, today) -> {
            throw new AllAiProvidersFailedException(new AiProviderException("all down"));
        }));

        assertThatThrownBy(() -> service.log(1L, "lunch R150"))
                .isInstanceOf(ManualEntryException.class)
                .hasMessageContaining("try again");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void fallsBackToTodayWhenTheProviderOmitsADate() {
        ManualEntryService service = serviceWith(providerReturning(
                (text, today) -> List.of(new ExtractedTransaction(
                        null, "Snack", new BigDecimal("15.00"), "Dining", "d", "CASH", "DEBIT"))));

        service.log(1L, "snack R15");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(TODAY);
    }
}
