package com.finme.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns every failure into the same JSON shape.
 * <p>
 * The handlers below the first two exist because anything not handled here falls through to
 * Spring's default {@code /error} page, which returns a differently-shaped body - so a client
 * reading {@code message} gets nothing, and the user sees a blank or generic failure. These are
 * the everyday ways a request goes wrong: a file over the size limit, a malformed JSON body, a
 * missing form field. Each deserves a sentence saying what to do, not a stack trace or silence.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Validation failed: " + fieldErrors));
    }

    /**
     * Spring's own message names a byte limit and the servlet container; the configured limit is
     * what the user needs, in the unit they think in.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        "That file is too large. The limit is 10MB - try a single statement "
                                + "period, or a smaller photo."));
    }

    /** No file part at all - typically a form posted without choosing a file. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                        "No file was included in the upload. Choose a file and try again."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                        "Missing required parameter: " + ex.getParameterName()));
    }

    /** Malformed or empty JSON body. The parser's own message leaks internal type names. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                        "Request body was missing or not valid JSON."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                        "Invalid value for '" + ex.getName() + "'."));
    }

    /**
     * Last resort. The response deliberately says nothing specific - an unexpected exception's
     * message can carry internal detail, and occasionally data, that should not reach a client.
     * The full stack trace goes to the log, where it is useful and private.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Something went wrong on our side. Please try again."));
    }

    /**
     * timestamp is a pre-formatted ISO-8601 String, not an Instant, and that is deliberate.
     * <p>
     * Serializing an Instant threw InvalidDefinitionException ("Java 8 date/time type not
     * supported by default") at the moment the error body was being written - for every error
     * this application produced. The HTTP status had already been committed by then, so callers
     * received a status code with an empty body and no explanation; an upload that failed
     * looked to the browser like it returned nothing at all. Formatting here removes the
     * dependency on which Jackson version is active and which date modules it has registered.
     */
    public record ErrorResponse(int status, String message, String timestamp) {

        public static ErrorResponse of(int status, String message) {
            return new ErrorResponse(status, message, Instant.now().toString());
        }
    }
}
