package com.finme.backend.service;

import com.finme.backend.dto.RecommendationsResponse;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.entity.User;
import com.finme.backend.repository.TransactionRepository;
import com.finme.backend.repository.UserRepository;
import com.finme.backend.service.WeeklySpendAnalysisService.WeeklySpendAnalysis;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyAnalysisTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmailService emailService = mock(EmailService.class);

    private final SpendAnalysisService spendAnalysisService = new SpendAnalysisService(transactionRepository);
    private final WeeklySpendAnalysisService weeklyService =
            new WeeklySpendAnalysisService(spendAnalysisService, recommendationService);

    private static Transaction debit(LocalDate date, String amount, String category) {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setSourceType(SourceType.STATEMENT);
        t.setDate(date);
        t.setMerchant("merchant");
        t.setAmount(new BigDecimal(amount));
        t.setCategory(category);
        t.setPaymentMethod(PaymentMethod.CARD);
        t.setStatus(TransactionStatus.ACTIVE);
        t.setDirection(TransactionDirection.DEBIT);
        return t;
    }

    private static Transaction credit(LocalDate date, String amount, String category) {
        Transaction t = debit(date, amount, category);
        t.setDirection(TransactionDirection.CREDIT);
        return t;
    }

    private void givenTransactions(Long userId, Transaction... transactions) {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE))
                .thenReturn(List.of(transactions));
    }

    private void givenRecommendations(Long userId, String... recommendations) {
        when(recommendationService.getRecommendations(userId))
                .thenReturn(new RecommendationsResponse(List.of(recommendations), null));
    }

    private static User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setEmailVerified(true);
        return user;
    }

    // ------------------------------------------------------------------ composition

    @Test
    void reportsTheSameFiguresTheDashboardWouldShow() {
        givenTransactions(1L,
                debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"),
                debit(LocalDate.of(2026, 7, 9), "220.00", "Dining"),
                debit(LocalDate.of(2026, 6, 4), "400.00", "Groceries"));
        givenRecommendations(1L);

        WeeklySpendAnalysis analysis = weeklyService.composeFor(1L).orElseThrow();

        assertThat(analysis.subject()).contains("2026-07");
        assertThat(analysis.body()).contains("R720.00");        // 500 + 220
        assertThat(analysis.body()).contains("R400.00");        // previous month
        assertThat(analysis.body()).contains("R320.00 (80.0%) more than last month");
    }

    @Test
    void excludesIncomeFromTheTotalExactlyAsTheDashboardDoes() {
        givenTransactions(1L,
                credit(LocalDate.of(2026, 7, 1), "18500.00", "Income"),
                debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));
        givenRecommendations(1L);

        WeeklySpendAnalysis analysis = weeklyService.composeFor(1L).orElseThrow();

        assertThat(analysis.body()).contains("R500.00");
        assertThat(analysis.body()).doesNotContain("18,500.00");
    }

    @Test
    void writesTheDirectionOfChangeInWords() {
        givenTransactions(1L,
                debit(LocalDate.of(2026, 7, 2), "300.00", "Groceries"),
                debit(LocalDate.of(2026, 6, 2), "500.00", "Groceries"));
        givenRecommendations(1L);

        assertThat(weeklyService.composeFor(1L).orElseThrow().body())
                .contains("R200.00 (40.0%) less than last month");
    }

    @Test
    void includesRecommendationsWhenTheyAreAvailable() {
        givenTransactions(1L, debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));
        givenRecommendations(1L, "Cut back on groceries.", "Set a target for next month.");

        assertThat(weeklyService.composeFor(1L).orElseThrow().body())
                .contains("What to look at:")
                .contains("Cut back on groceries.")
                .contains("Set a target for next month.");
    }

    @Test
    void stillSendsTheFiguresWhenTheAiCouldNotProduceRecommendations() {
        // The numbers are computed locally and remain correct. Withholding the whole email
        // because a third party was rate limited would be a worse outcome than a shorter one.
        givenTransactions(1L, debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));
        when(recommendationService.getRecommendations(1L))
                .thenReturn(new RecommendationsResponse(List.of(), "unavailable"));

        WeeklySpendAnalysis analysis = weeklyService.composeFor(1L).orElseThrow();

        assertThat(analysis.body()).contains("R500.00");
        assertThat(analysis.body()).doesNotContain("What to look at:");
    }

    @Test
    void sendsNothingToAUserWithNoSpending() {
        givenTransactions(2L);

        assertThat(weeklyService.composeFor(2L)).isEmpty();
    }

    // ------------------------------------------------------------------ scheduling

    private WeeklyAnalysisScheduler schedulerWith(WeeklySpendAnalysisService service) {
        return new WeeklyAnalysisScheduler(userRepository, service, emailService);
    }

    @Test
    void emailsEveryVerifiedUserWhoHasSpending() {
        WeeklySpendAnalysisService service = mock(WeeklySpendAnalysisService.class);
        when(userRepository.findByEmailVerifiedTrue())
                .thenReturn(List.of(user(1L, "a@example.com"), user(2L, "b@example.com")));
        when(service.composeFor(any())).thenReturn(Optional.of(new WeeklySpendAnalysis("subject", "body")));

        schedulerWith(service).sendWeeklyAnalyses();

        verify(emailService).sendWeeklySpendAnalysis(eq("a@example.com"), anyString(), anyString());
        verify(emailService).sendWeeklySpendAnalysis(eq("b@example.com"), anyString(), anyString());
    }

    @Test
    void neverEmailsAnUnverifiedAddress() {
        // An unverified address has not been shown to belong to the person who typed it -
        // mailing someone's spending to it would send their finances to a stranger.
        WeeklySpendAnalysisService service = mock(WeeklySpendAnalysisService.class);
        when(userRepository.findByEmailVerifiedTrue()).thenReturn(List.of());

        schedulerWith(service).sendWeeklyAnalyses();

        verify(emailService, never()).sendWeeklySpendAnalysis(anyString(), anyString(), anyString());
    }

    @Test
    void oneUsersFailureDoesNotStopTheRest() {
        WeeklySpendAnalysisService service = mock(WeeklySpendAnalysisService.class);
        when(userRepository.findByEmailVerifiedTrue()).thenReturn(List.of(
                user(1L, "first@example.com"),
                user(2L, "broken@example.com"),
                user(3L, "third@example.com")));
        when(service.composeFor(any())).thenReturn(Optional.of(new WeeklySpendAnalysis("s", "b")));
        doThrow(new RuntimeException("mail server rejected the address"))
                .when(emailService).sendWeeklySpendAnalysis(eq("broken@example.com"), anyString(), anyString());

        schedulerWith(service).sendWeeklyAnalyses();

        verify(emailService, times(3)).sendWeeklySpendAnalysis(anyString(), anyString(), anyString());
        verify(emailService).sendWeeklySpendAnalysis(eq("third@example.com"), anyString(), anyString());
    }

    @Test
    void skipsUsersWithNothingToReportWithoutTreatingItAsAFailure() {
        WeeklySpendAnalysisService service = mock(WeeklySpendAnalysisService.class);
        when(userRepository.findByEmailVerifiedTrue())
                .thenReturn(List.of(user(1L, "quiet@example.com"), user(2L, "active@example.com")));
        when(service.composeFor(1L)).thenReturn(Optional.empty());
        when(service.composeFor(2L)).thenReturn(Optional.of(new WeeklySpendAnalysis("s", "b")));

        schedulerWith(service).sendWeeklyAnalyses();

        verify(emailService, never()).sendWeeklySpendAnalysis(eq("quiet@example.com"), anyString(), anyString());
        verify(emailService).sendWeeklySpendAnalysis(eq("active@example.com"), anyString(), anyString());
    }

    @Test
    void sendsTheComposedSubjectAndBodyUnaltered() {
        WeeklySpendAnalysisService service = mock(WeeklySpendAnalysisService.class);
        when(userRepository.findByEmailVerifiedTrue()).thenReturn(List.of(user(1L, "a@example.com")));
        when(service.composeFor(1L))
                .thenReturn(Optional.of(new WeeklySpendAnalysis("Your FinMe spend summary", "the body")));

        schedulerWith(service).sendWeeklyAnalyses();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWeeklySpendAnalysis(eq("a@example.com"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("Your FinMe spend summary");
        assertThat(body.getValue()).isEqualTo("the body");
    }
}
