package com.finme.backend.controller;

import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.dto.TransactionResponse;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final AuthenticatedUser authenticatedUser;

    public TransactionController(TransactionRepository transactionRepository, AuthenticatedUser authenticatedUser) {
        this.transactionRepository = transactionRepository;
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
}
