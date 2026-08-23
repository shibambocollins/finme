package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised for a statement id that does not exist <em>or</em> belongs to another user - the same
 * 404 either way, deliberately. Distinguishing "not yours" from "not here" would let anyone
 * probe ids to learn how many statements other accounts hold.
 */
public class StatementNotFoundException extends ApiException {

    public StatementNotFoundException(Long statementId) {
        super(HttpStatus.NOT_FOUND, "Statement " + statementId + " was not found");
    }
}
