package com.finme.backend.dto;

import com.finme.backend.entity.CreditAccount;
import com.finme.backend.entity.CreditProfile;
import com.finme.backend.entity.CreditSnapshot;
import com.finme.backend.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The whole credit position in one response (FR-2.1.1 to FR-2.1.3).
 * <p>
 * It deliberately carries no utilization figure. That calculation is FR-2.2.1 and arrives in
 * Iteration 9, computed in code - putting a placeholder here now would invite the frontend to
 * start deriving it client-side, which is precisely the split this project keeps on the server.
 *
 * @param currentScore     the most recent snapshot's score, or null before any has been recorded
 * @param scoreRecordedAt  when that reading was taken - a score with no date is not much use
 * @param maxScore         echoed back so a client can render "620 of 740" without assuming a scale
 */
public record CreditProfileResponse(
        Long id,
        String bureau,
        int maxScore,
        Instant createdAt,
        Integer currentScore,
        Instant scoreRecordedAt,
        List<CreditAccountResponse> accounts) {

    public record CreditAccountResponse(
            Long id,
            String accountName,
            BigDecimal balance,
            BigDecimal creditLimit,
            PaymentStatus paymentStatus) {

        public static CreditAccountResponse from(CreditAccount account) {
            return new CreditAccountResponse(
                    account.getId(),
                    account.getAccountName(),
                    account.getBalance(),
                    account.getCreditLimit(),
                    account.getPaymentStatus());
        }
    }

    public static CreditProfileResponse from(
            CreditProfile profile, List<CreditAccount> accounts, CreditSnapshot latestSnapshot) {
        return new CreditProfileResponse(
                profile.getId(),
                profile.getBureau(),
                profile.getMaxScore(),
                profile.getCreatedAt(),
                latestSnapshot == null ? null : latestSnapshot.getScore(),
                latestSnapshot == null ? null : latestSnapshot.getRecordedAt(),
                accounts.stream().map(CreditAccountResponse::from).toList());
    }
}
