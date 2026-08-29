package com.finme.backend.entity;

public enum TransactionDirection {

    DEBIT,

    CREDIT;

    public static TransactionDirection fromExtracted(String value) {
        return value != null && "CREDIT".equalsIgnoreCase(value.trim()) ? CREDIT : DEBIT;
    }
}
