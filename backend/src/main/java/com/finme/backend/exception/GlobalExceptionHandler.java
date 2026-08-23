package com.finme.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
