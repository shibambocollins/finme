package com.finme.backend.ai;

import com.finme.backend.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown only when every provider in the fallback chain has failed. Caught alongside
 * IOException in StatementIngestionService so the statement still ends up marked FAILED, not a
 * raw 500.
 */
public class AllAiProvidersFailedException extends ApiException {

    public AllAiProvidersFailedException(Throwable lastCause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "All AI providers in the fallback chain failed", lastCause);
    }
}
