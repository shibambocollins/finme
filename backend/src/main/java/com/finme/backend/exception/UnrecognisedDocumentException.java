package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The upload was a valid file of the right format, but not the kind of document the pipeline
 * was asked to read - a payslip uploaded as a bank statement, a photo of a dog as a receipt, or
 * a scanned statement with no extractable text.
 * <p>
 * Distinct from {@link InvalidStatementFileException} (wrong file format, rejected before any
 * processing) and from {@link StatementProcessingException} (something broke). Nothing broke
 * here and nothing was malformed; the document simply is not what was expected, and the user is
 * the only one who can fix that. The messages therefore say what was actually observed and what
 * to try, rather than reporting a failure the user cannot act on.
 */
public class UnrecognisedDocumentException extends ApiException {

    public UnrecognisedDocumentException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
