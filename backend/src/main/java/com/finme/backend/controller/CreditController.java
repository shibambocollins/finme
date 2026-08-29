package com.finme.backend.controller;

import com.finme.backend.dto.CreateCreditProfileRequest;
import com.finme.backend.dto.CreditAccountRequest;
import com.finme.backend.dto.CreditProfileResponse;
import com.finme.backend.dto.CreditAnalysisResponse;
import com.finme.backend.dto.RecordCreditScoreRequest;
import com.finme.backend.dto.ScoreComparisonResponse;
import com.finme.backend.dto.UtilizationSimulationRequest;
import com.finme.backend.dto.UtilizationSimulationResponse;
import com.finme.backend.entity.CreditSnapshot;
import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.service.CreditAnalysisService;
import com.finme.backend.service.CreditProfileService;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/credit")
public class CreditController {

    private final CreditProfileService creditProfileService;
    private final CreditAnalysisService creditAnalysisService;
    private final AuthenticatedUser authenticatedUser;

    public CreditController(CreditProfileService creditProfileService,
                            CreditAnalysisService creditAnalysisService,
                            AuthenticatedUser authenticatedUser) {
        this.creditProfileService = creditProfileService;
        this.creditAnalysisService = creditAnalysisService;
        this.authenticatedUser = authenticatedUser;
    }

    @PostMapping("/profile")
    public ResponseEntity<CreditProfileResponse> createProfile(
            @Valid @RequestBody(required = false) CreateCreditProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditProfileService.createProfile(authenticatedUser.currentUserId(), request));
    }

    @GetMapping("/profile")
    public CreditProfileResponse getProfile() {
        return creditProfileService.getProfile(authenticatedUser.currentUserId());
    }

    @DeleteMapping("/profile")
    public ResponseEntity<Void> deleteProfile() {
        creditProfileService.deleteProfile(authenticatedUser.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts")
    public ResponseEntity<CreditProfileResponse> addAccount(@Valid @RequestBody CreditAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditProfileService.addAccount(authenticatedUser.currentUserId(), request));
    }

    @PutMapping("/accounts/{id}")
    public CreditProfileResponse updateAccount(@PathVariable Long id,
                                               @Valid @RequestBody CreditAccountRequest request) {
        return creditProfileService.updateAccount(authenticatedUser.currentUserId(), id, request);
    }

    @DeleteMapping("/accounts/{id}")
    public CreditProfileResponse deleteAccount(@PathVariable Long id) {
        return creditProfileService.deleteAccount(authenticatedUser.currentUserId(), id);
    }

    @PostMapping("/score")
    public ResponseEntity<CreditProfileResponse> recordScore(@Valid @RequestBody RecordCreditScoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditProfileService.recordScore(authenticatedUser.currentUserId(), request));
    }

    @GetMapping("/score/history")
    public List<CreditSnapshot> scoreHistory() {
        return creditProfileService.scoreHistory(authenticatedUser.currentUserId());
    }

    @GetMapping("/analysis")
    public CreditAnalysisResponse analysis() {
        return creditAnalysisService.analyse(authenticatedUser.currentUserId());
    }

    @PostMapping("/simulate")
    public UtilizationSimulationResponse simulate(@Valid @RequestBody UtilizationSimulationRequest request) {
        return creditAnalysisService.simulate(authenticatedUser.currentUserId(), request);
    }

    @GetMapping("/score/comparison")
    public ScoreComparisonResponse scoreComparison(@RequestParam(required = false) Long againstSnapshotId) {
        return creditAnalysisService.compareScores(authenticatedUser.currentUserId(), againstSnapshotId);
    }
}
