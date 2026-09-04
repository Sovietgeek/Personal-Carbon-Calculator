package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.carbon.*;
import com.ecoverse.model.EmissionFactor;
import com.ecoverse.repository.EmissionFactorRepository;
import com.ecoverse.service.AuditLogService;
import com.ecoverse.service.CarbonService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/carbon")
public class CarbonController {

    @Autowired
    private CarbonService carbonService;

    @Autowired
    private EmissionFactorRepository emissionFactorRepository;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * Preview/estimate calculation. Does NOT create an entry.
     * Returns calculated CO2 for the user to review before submitting.
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<EmissionCalcResponse>> calculateEmission(@Valid @RequestBody EmissionCalcRequest request) {
        EmissionCalcResponse response = carbonService.calculateEmission(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Add a new carbon entry. Server calculates CO2 authoritatively.
     * Client sends input values (distance, consumption, etc.) — NOT CO2.
     */
    @PostMapping("/entries")
    public ResponseEntity<ApiResponse<CarbonEntryResponse>> addEntry(@Valid @RequestBody CarbonEntryRequest request) {
        Long userId = getCurrentUserId();
        CarbonEntryResponse response = carbonService.addEntry(userId, request);
        auditLogService.log(userId, "CARBON_ENTRY_CREATE", "/api/carbon/entries");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Carbon entry added successfully", response));
    }

    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<List<CarbonEntryResponse>>> getEntries(
            @RequestParam(defaultValue = "today") String period) {
        Long userId = getCurrentUserId();
        List<CarbonEntryResponse> responses = carbonService.getEntries(userId, period);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEntry(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        carbonService.deleteEntry(id, userId);
        auditLogService.log(userId, "CARBON_ENTRY_DELETE", "/api/carbon/entries/" + id);
        return ResponseEntity.ok(ApiResponse.success("Carbon entry deleted successfully", null));
    }

    @DeleteMapping("/entries/today/clear")
    public ResponseEntity<ApiResponse<Void>> clearTodayEntries() {
        Long userId = getCurrentUserId();
        carbonService.clearTodayEntries(userId);
        return ResponseEntity.ok(ApiResponse.success("Today's carbon entries cleared successfully", null));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CarbonSummaryResponse>> getSummary() {
        Long userId = getCurrentUserId();
        CarbonSummaryResponse response = carbonService.getSummary(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/risk")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> getRiskAssessment() {
        Long userId = getCurrentUserId();
        RiskAssessmentResponse response = carbonService.getRiskAssessment(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/breakdown")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> getCategoryBreakdown() {
        Long userId = getCurrentUserId();
        Map<String, BigDecimal> breakdown = carbonService.getCategoryBreakdown(userId);
        return ResponseEntity.ok(ApiResponse.success(breakdown));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSuggestions() {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> suggestions = carbonService.getSuggestions(userId);
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }

    /**
     * Get available emission factor types for a category.
     * Used by frontend to populate dropdown options.
     */
    @GetMapping("/factors")
    public ResponseEntity<ApiResponse<List<EmissionFactorSummary>>> getFactors(
            @RequestParam String category) {
        List<EmissionFactor> factors = emissionFactorRepository.findByCategoryAndActiveTrueOrderByType(category);
        List<EmissionFactorSummary> summaries = factors.stream()
                .map(f -> EmissionFactorSummary.builder()
                        .type(f.getType())
                        .factor(f.getFactor())
                        .unit(f.getUnit())
                        .inputUnit(f.getInputUnit())
                        .verificationStatus(f.getVerificationStatus())
                        .uncertainty(f.getUncertainty())
                        .sourceName(f.getSourceName())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }

    /**
     * Summary DTO for emission factor listing (no internal IDs or admin fields).
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class EmissionFactorSummary {
        private String type;
        private BigDecimal factor;
        private String unit;
        private String inputUnit;
        private String verificationStatus;
        private String uncertainty;
        private String sourceName;
    }
}
