package com.finme.backend.controller;

import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.dto.ManualEntryRequest;
import com.finme.backend.dto.TransactionResponse;
import com.finme.backend.service.ManualEntryService;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final ManualEntryService manualEntryService;
    private final AuthenticatedUser authenticatedUser;

    public TransactionController(
            TransactionRepository transactionRepository,
            ManualEntryService manualEntryService,
            AuthenticatedUser authenticatedUser) {
        this.transactionRepository = transactionRepository;
        this.manualEntryService = manualEntryService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public List<TransactionResponse> list() {
        return transactionRepository
                .findByUserIdAndStatusOrderByDateDesc(authenticatedUser.currentUserId(), TransactionStatus.ACTIVE)
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
}
