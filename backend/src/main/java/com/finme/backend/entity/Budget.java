package com.finme.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A standing monthly spending target for one category.
 * <p>
 * Recurring rather than tied to a specific month, by design: a user setting "Groceries: R3000"
 * means that every month going forward, not a one-off figure they would have to re-enter on the
 * first of each month. There is exactly one budget per (user, category) - creating a second one
 * for a category the user already budgets updates the existing row rather than adding a
 * competing figure (see BudgetService).
 */
@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category"}))
@Getter
@Setter
@NoArgsConstructor
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Matched against Transaction.category case-insensitively (see BudgetService) - category is
     * free text everywhere in this app, and a user typing "groceries" one month and "Groceries"
     * the next must still hit the same budget.
     */
    @Column(nullable = false)
    private String category;

    @Column(name = "monthly_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
