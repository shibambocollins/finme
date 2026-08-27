package com.finme.backend.controller;

import com.finme.backend.dto.ManualEntryRequest;
import com.finme.backend.dto.TransactionResponse;
import com.finme.backend.dto.UpdateTransactionRequest;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.service.ManualEntryService;
import com.finme.backend.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final ManualEntryService manualEntryService;
    private final AuthenticatedUser authenticatedUser;

    public TransactionController(
            TransactionService transactionService,
            ManualEntryService manualEntryService,
            AuthenticatedUser authenticatedUser) {
        this.transactionService = transactionService;
        this.manualEntryService = manualEntryService;
        this.authenticatedUser = authenticatedUser;
    }

    /**
     * Every filter is optional and additive. Filtering happens over the user's own transactions
     * only - the same ownership scoping every other endpoint in this app uses, so there is no
     * parameter combination that reaches another user's data.
     */
    @GetMapping
    public List<TransactionResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) TransactionDirection direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {
        return transactionService
                .search(authenticatedUser.currentUserId(), category, sourceType, direction, from, to, q)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    /**
     * Logs spending described in plain language (FR-1.5.1). Synchronous, unlike statement
     * upload: this is one short sentence and one provider call, and the user is waiting to see
     * the transaction they just described appear.
     */
    @PostMapping("/manual")
    public ResponseEntity<List<TransactionResponse>> logManualEntry(@Valid @RequestBody ManualEntryRequest request) {
        List<TransactionResponse> created =
                manualEntryService.log(authenticatedUser.currentUserId(), request.text()).stream()
                        .map(TransactionResponse::from)
                        .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Corrects a miscategorised or wrong-amount extraction. Every field must be resent. */
    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTransactionRequest request) {
        return TransactionResponse.from(
                transactionService.update(authenticatedUser.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.delete(authenticatedUser.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
