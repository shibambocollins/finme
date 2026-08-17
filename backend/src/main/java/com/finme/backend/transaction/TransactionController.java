package com.finme.backend.transaction;

import com.finme.backend.auth.AuthenticatedUser;
import com.finme.backend.transaction.dto.TransactionResponse;
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
        return transactionRepository.findByUserIdOrderByDateDesc(authenticatedUser.currentUserId())
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }
}
