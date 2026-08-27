package com.finme.backend.controller;

import com.finme.backend.dto.CalendarResponse;
import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.dto.RecommendationsResponse;
import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.service.DashboardService;
import com.finme.backend.service.RecommendationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final RecommendationService recommendationService;
    private final AuthenticatedUser authenticatedUser;

    public DashboardController(
            DashboardService dashboardService,
            RecommendationService recommendationService,
            AuthenticatedUser authenticatedUser) {
        this.dashboardService = dashboardService;
        this.recommendationService = recommendationService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {
        return dashboardService.getSummary(authenticatedUser.currentUserId());
    }

    /**
     * Served separately from /summary rather than embedded in it, because the two have very
     * different failure profiles: the summary is deterministic local arithmetic that always
     * succeeds, while this may call out to rate-limited third-party providers. Keeping them
     * apart means a provider outage costs the user their recommendations, not their dashboard.
     */
    @GetMapping("/recommendations")
    public RecommendationsResponse recommendations() {
        return recommendationService.getRecommendations(authenticatedUser.currentUserId());
    }

    /** One day per calendar tile, including zero-spend days. Defaults to the current month. */
    @GetMapping("/calendar")
    public CalendarResponse calendar(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return dashboardService.getCalendar(authenticatedUser.currentUserId(), month);
    }
}
