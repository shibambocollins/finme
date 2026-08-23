package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * A manual entry that could not be turned into a transaction (FR-1.5.1).
 * <p>
 * 400 rather than 500 even when the cause is a provider failure, because from the user's side
 * the outcome is the same and actionable: nothing was saved, and rephrasing or retrying is what
 * to do next. The message is written to be shown as-is in the UI, so it says what to try rather
 * than naming an internal component.
 */
public class ManualEntryException extends ApiException {

    public ManualEntryException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
