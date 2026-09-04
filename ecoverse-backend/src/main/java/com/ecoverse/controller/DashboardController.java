package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.dashboard.DashboardResponse;
import com.ecoverse.dto.dashboard.DashboardTrendResponse;
import com.ecoverse.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Production Dashboard Controller — Phase E.
 *
 * Endpoints:
 * - GET /api/dashboard — Full dashboard data (single API call)
 * - GET /api/dashboard/trend?period=week|month|year — Trend chart data
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * Get full dashboard data for the authenticated user.
     * Single API call returns all dashboard metrics, no N+1, no state mutation.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        Long userId = getCurrentUserId();
        DashboardResponse response = dashboardService.getDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get trend data for the dashboard carbon chart.
     * Returns daily emission + avoided data points for the specified period.
     *
     * @param period "week", "month", or "year" (default: "week")
     */
    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<DashboardTrendResponse>> getTrend(
            @RequestParam(defaultValue = "week") String period) {
        if (!isValidPeriod(period)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid period. Use: week, month, or year"));
        }
        Long userId = getCurrentUserId();
        DashboardTrendResponse response = dashboardService.getTrend(userId, period);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private boolean isValidPeriod(String period) {
        return "week".equalsIgnoreCase(period)
                || "month".equalsIgnoreCase(period)
                || "year".equalsIgnoreCase(period);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }
}
