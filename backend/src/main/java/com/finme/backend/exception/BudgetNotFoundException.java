package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/** Same 404-either-way pattern as TransactionNotFoundException and CreditAccountNotFoundException. */
public class BudgetNotFoundException extends ApiException {

    public BudgetNotFoundException(Long budgetId) {
        super(HttpStatus.NOT_FOUND, "Budget " + budgetId + " was not found");
    }
}
