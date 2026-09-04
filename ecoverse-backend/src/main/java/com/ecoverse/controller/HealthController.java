package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.health.BMIRequest;
import com.ecoverse.dto.health.BMIResponse;
import com.ecoverse.dto.health.HealthLogRequest;
import com.ecoverse.dto.health.HealthLogResponse;
import com.ecoverse.dto.health.HealthScoreResponse;
import com.ecoverse.service.AuditLogService;
import com.ecoverse.service.HealthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health")
@Validated
public class HealthController {

    @Autowired
    private HealthService healthService;

    @Autowired
    private AuditLogService auditLogService;

    @PostMapping("/log")
    public ResponseEntity<ApiResponse<HealthLogResponse>> logHealth(@Valid @RequestBody HealthLogRequest request) {
        Long userId = getCurrentUserId();
        HealthLogResponse response = healthService.logHealth(userId, request);
        auditLogService.log(userId, "HEALTH_LOG_CREATE", "/api/health/log");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Health log added successfully", response));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<HealthLogResponse>>> getHealthLogs(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long userId = getCurrentUserId();
        Page<HealthLogResponse> responses = healthService.getHealthLogs(userId, type, period, page, size);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping("/bmi")
    public ResponseEntity<ApiResponse<BMIResponse>> calculateBMI(@Valid @RequestBody BMIRequest request) {
        BMIResponse response = healthService.calculateBMI(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/score")
    public ResponseEntity<ApiResponse<HealthScoreResponse>> getHealthScore() {
        Long userId = getCurrentUserId();
        HealthScoreResponse response = healthService.getHealthScore(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/streak")
    public ResponseEntity<ApiResponse<Integer>> calculateStreak() {
        Long userId = getCurrentUserId();
        Integer streak = healthService.calculateStreak(userId);
        return ResponseEntity.ok(ApiResponse.success(streak));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }
}
