package com.finme.backend.controller;

import com.finme.backend.dto.BudgetStatusResponse;
import com.finme.backend.dto.CreateOrUpdateBudgetRequest;
import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final AuthenticatedUser authenticatedUser;

    public BudgetController(BudgetService budgetService, AuthenticatedUser authenticatedUser) {
        this.budgetService = budgetService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public List<BudgetStatusResponse> list() {
        return budgetService.list(authenticatedUser.currentUserId());
    }

    @PostMapping
    public ResponseEntity<BudgetStatusResponse> createOrUpdate(@Valid @RequestBody CreateOrUpdateBudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(budgetService.createOrUpdate(authenticatedUser.currentUserId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        budgetService.delete(authenticatedUser.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
