package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.shop.UpdateOrderStatusRequest;
import com.ecoverse.model.*;
import com.ecoverse.repository.*;
import com.ecoverse.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin Control Center endpoints.
 *
 * ACCESS: ADMIN role required for ALL endpoints (class-level @PreAuthorize).
 * Every sensitive action is audited via AuditLogService.
 * Do NOT build a public route that makes a user admin.
 * Admin role is bootstrapped via AdminBootstrap (ADMIN_EMAIL env var).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    // ================================================================
    // DASHBOARD / ANALYTICS
    // ================================================================

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        Map<String, Object> analytics = adminService.getAnalytics();
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    @GetMapping("/analytics/charts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalyticsCharts() {
        Map<String, Object> data = adminService.getAnalyticsChartData();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ================================================================
    // USER MANAGEMENT
    // ================================================================

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> userPage;
        if (search != null && !search.isBlank()) {
            userPage = adminService.searchUsers(search, pageable);
        } else if (role != null && enabled != null) {
            userPage = adminService.getUsersByRoleAndEnabled(role, enabled, pageable);
        } else if (role != null) {
            userPage = adminService.getUsersByRole(role, pageable);
        } else if (enabled != null) {
            userPage = adminService.getUsersByEnabled(enabled, pageable);
        } else {
            userPage = adminService.getUsers(pageable);
        }

        Page<Map<String, Object>> safePage = userPage.map(this::toSafeUserResponse);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(safePage)));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUser(@PathVariable Long id) {
        User user = adminService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success(toSafeUserResponse(user)));
    }

    @GetMapping("/users/{id}/detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserDetail(@PathVariable Long id) {
        Map<String, Object> detail = adminService.getUserDetail(id);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/users/{id}/carbon")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserCarbon(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "entryDate"));
        Page<CarbonEntry> entries = adminService.getUserCarbonEntries(id, pageable);
        Map<String, Object> stats = adminService.getUserCarbonStats(id);

        Map<String, Object> response = new HashMap<>();
        response.put("entries", toPaginatedResponse(entries));
        response.put("stats", stats);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{id}/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserHealth(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "entryDate"));
        Page<HealthLog> logs = adminService.getUserHealthLogs(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(logs)));
    }

    @GetMapping("/users/{id}/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserOrders(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = adminService.getUserOrders(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(orders)));
    }

    @GetMapping("/users/{id}/achievements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserAchievements(@PathVariable Long id) {
        var achievements = adminService.getUserAchievements(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("achievements", achievements)));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserStatus(
            @PathVariable Long id,
            @RequestBody UserStatusRequest request) {
        Long adminId = getCurrentUserId();
        User user = adminService.updateUserStatus(adminId, id, request.isEnabled());
        return ResponseEntity.ok(ApiResponse.success("User status updated", toSafeUserResponse(user)));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserRole(
            @PathVariable Long id,
            @RequestBody UserRoleRequest request) {
        Long adminId = getCurrentUserId();
        User user = adminService.updateUserRole(adminId, id, request.getRole());
        return ResponseEntity.ok(ApiResponse.success("User role updated", toSafeUserResponse(user)));
    }

    // ================================================================
    // PRODUCT MANAGEMENT
    // ================================================================

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ProductStatus status) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Product> productPage = status != null
                ? adminService.getProductsByStatus(status, pageable)
                : adminService.getProducts(pageable);

        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(productPage)));
    }

    @PatchMapping("/products/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProductStatus(
            @PathVariable Long id,
            @RequestBody ProductStatusRequest request) {
        Long adminId = getCurrentUserId();
        Product product = adminService.updateProductStatus(adminId, id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Product status updated",
                Map.of("id", product.getId(), "status", product.getStatus())));
    }

    // ================================================================
    // ORDER MANAGEMENT
    // ================================================================

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Order.OrderStatus status) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> orderPage = status != null
                ? adminService.getOrdersByStatus(status, pageable)
                : adminService.getOrders(pageable);

        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(orderPage)));
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        Long adminId = getCurrentUserId();
        Order order = adminService.updateOrderStatus(adminId, id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Order status updated",
                Map.of("id", order.getId(), "status", order.getStatus())));
    }

    // ================================================================
    // PAYMENT EVENTS
    // ================================================================

    @GetMapping("/payments/events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentEvent> eventPage = adminService.getPaymentEvents(pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(eventPage)));
    }

    // ================================================================
    // AUDIT LOGS
    // ================================================================

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditPage = adminService.getAuditLogs(action, userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(auditPage)));
    }

    // ================================================================
    // REVIEWS
    // ================================================================

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Review.ReviewStatus status) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviewPage = adminService.getReviews(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(reviewPage)));
    }

    @PatchMapping("/reviews/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateReviewStatus(
            @PathVariable Long id,
            @RequestBody ReviewStatusRequest request) {
        Long adminId = getCurrentUserId();
        Review review = adminService.updateReviewStatus(adminId, id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Review status updated",
                Map.of("id", review.getId(), "status", review.getStatus())));
    }

    // ================================================================
    // AI USAGE
    // ================================================================

    @GetMapping("/ai-usage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAiUsage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AiUsageLog> logs = adminService.getAiUsageLogs(pageable);
        Map<String, Object> stats = adminService.getAiUsageStats();

        Map<String, Object> response = new HashMap<>();
        response.put("logs", toPaginatedResponse(logs));
        response.put("stats", stats);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ================================================================
    // SYSTEM HEALTH
    // ================================================================

    @GetMapping("/system/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth() {
        Map<String, Object> health = adminService.getSystemHealth();
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user found in security context");
        }
        return Long.parseLong(auth.getPrincipal().toString());
    }

    /**
     * Strip sensitive fields from User before sending to the frontend.
     * NEVER expose: password, emailVerificationToken, passwordResetToken,
     * refreshToken, API keys, BCrypt hash
     */
    private Map<String, Object> toSafeUserResponse(User user) {
        Map<String, Object> safe = new HashMap<>();
        safe.put("id", user.getId());
        safe.put("name", user.getName());
        safe.put("email", user.getEmail());
        safe.put("role", user.getRole());
        safe.put("enabled", user.getEnabled());
        safe.put("provider", user.getProvider());
        safe.put("country", user.getCountry());
        safe.put("city", user.getCity());
        safe.put("isPremium", user.getIsPremium());
        safe.put("createdAt", user.getCreatedAt());
        return safe;
    }

    private <T> Map<String, Object> toPaginatedResponse(Page<T> page) {
        Map<String, Object> response = new HashMap<>();
        response.put("content", page.getContent());
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalElements", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        response.put("last", page.isLast());
        return response;
    }

    // ===== Request DTOs (inner classes) =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserStatusRequest {
        private boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserRoleRequest {
        @NotNull(message = "Role is required")
        private Role role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductStatusRequest {
        @NotNull(message = "Status is required")
        private ProductStatus status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewStatusRequest {
        @NotNull(message = "Status is required")
        private Review.ReviewStatus status;
    }
}
