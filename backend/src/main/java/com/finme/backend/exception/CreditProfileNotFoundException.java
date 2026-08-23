package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The user has not created a credit profile. 404, and phrased as a next step rather than a
 * fault: the credit module is optional (FR-2.1.1), so not having one is a normal state for most
 * users, not an error they made.
 */
public class CreditProfileNotFoundException extends ApiException {

    public CreditProfileNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No credit profile yet. Create one to start tracking your credit position.");
    }
}
