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
import java.time.LocalDate;

/**
 * Full schema from docs/03-system-design.md Sec. 3. sourceId/supersededBy exist now so
 * receipt ingestion and duplicate detection (later iterations) don't need a schema change -
 * this iteration only ever writes sourceType=STATEMENT, status=ACTIVE, supersededBy=null.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String merchant;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    private String category;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.ACTIVE;

    @Column(name = "superseded_by")
    private Long supersededBy;

    /**
     * Only ever populated from a receipt photo's printed address (statement text has no
     * address to extract) - null means "not geocoded", not "geocoding failed", since
     * docs/03-system-design.md Sec.2 already scopes map coordinates as "where available".
     */
    private String address;

    private Double latitude;

    private Double longitude;
}
