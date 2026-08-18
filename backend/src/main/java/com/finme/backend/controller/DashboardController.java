package com.finme.backend.controller;

import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthenticatedUser authenticatedUser;

    public DashboardController(DashboardService dashboardService, AuthenticatedUser authenticatedUser) {
        this.dashboardService = dashboardService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {
        return dashboardService.getSummary(authenticatedUser.currentUserId());
    }
}
