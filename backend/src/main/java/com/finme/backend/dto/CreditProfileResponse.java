package com.finme.backend.dto;

import com.finme.backend.entity.CreditAccount;
import com.finme.backend.entity.CreditProfile;
import com.finme.backend.entity.CreditSnapshot;
import com.finme.backend.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
