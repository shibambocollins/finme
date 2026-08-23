package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Credit data that is well-formed but not usable - a zero credit limit, or a score outside the
 * profile's own scale. Distinct from bean-validation failures because these rules depend on
 * other stored values (the profile's max_score) and so cannot be expressed as annotations.
 */
public class InvalidCreditDataException extends ApiException {

    public InvalidCreditDataException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
