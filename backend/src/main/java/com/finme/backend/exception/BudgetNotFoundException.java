package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class BudgetNotFoundException extends ApiException {

    public BudgetNotFoundException(Long budgetId) {
        super(HttpStatus.NOT_FOUND, "Budget " + budgetId + " was not found");
    }
}
