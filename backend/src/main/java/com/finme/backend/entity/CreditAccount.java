package com.finme.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One manually-entered credit account (FR-2.1.2).
 * <p>
 * Money uses BigDecimal with an explicit precision and scale, matching Transaction. These
 * values are divided in Iteration 9 to produce utilization, and a figure the user is told to
 * act on should not carry binary floating-point error.
 */
@Entity
@Table(name = "credit_accounts")
@Getter
@Setter
@NoArgsConstructor
public class CreditAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_profile_id", nullable = false)
    private Long creditProfileId;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    /**
     * Named credit_limit in the database: "limit" is a reserved word in MySQL, and an unquoted
     * column of that name fails at schema creation rather than at query time - on the prod
     * profile only, which is the worst place to find out.
     */
    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNKNOWN;
}
