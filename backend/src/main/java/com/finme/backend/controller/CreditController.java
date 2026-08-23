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

/**
 * Credit profile management (FR-2.1.1 to FR-2.1.4).
 * <p>
 * No endpoint accepts a profile or user id. Every one resolves the profile from the
 * authenticated user, so there is no request shape in which a caller can name whose credit data
 * to read or change - the ownership check is structural rather than something each handler has
 * to remember.
 */
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

    /** Body is optional - posting nothing takes the documented bureau and scale defaults. */
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

    /**
     * These return the whole profile rather than just the changed account. The credit position
     * is only meaningful as a whole - one account's balance means nothing without the others -
     * so a client always needs the full picture after any change, and returning it here saves a
     * follow-up request that could otherwise render a briefly inconsistent view.
     */
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

    /** Calculated utilization plus a prioritised plan and the mandatory disclaimer. */
    @GetMapping("/analysis")
    public CreditAnalysisResponse analysis() {
        return creditAnalysisService.analyse(authenticatedUser.currentUserId());
    }

    /**
     * A what-if (FR-2.3.2). POST rather than GET because it carries a body, but it writes
     * nothing - the stored balance is untouched.
     */
    @PostMapping("/simulate")
    public UtilizationSimulationResponse simulate(@Valid @RequestBody UtilizationSimulationRequest request) {
        return creditAnalysisService.simulate(authenticatedUser.currentUserId(), request);
    }

    /** Current score against an earlier reading; defaults to the one immediately before it. */
    @GetMapping("/score/comparison")
    public ScoreComparisonResponse scoreComparison(@RequestParam(required = false) Long againstSnapshotId) {
        return creditAnalysisService.compareScores(authenticatedUser.currentUserId(), againstSnapshotId);
    }
}
