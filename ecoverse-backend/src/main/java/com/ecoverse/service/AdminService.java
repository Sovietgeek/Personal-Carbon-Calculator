package com.ecoverse.service;

import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.*;
import com.ecoverse.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin-specific business logic.
 *
 * SECURITY:
 * - ALL methods require ADMIN role (enforced at controller level via @PreAuthorize)
 * - Every sensitive action is audited via AuditLogService
 * - Admin CANNOT promote to ADMIN (only AdminBootstrap via ADMIN_EMAIL env var)
 * - Admin CANNOT change own role
 * - Admin CANNOT demote the last admin
 * - Blocking a user revokes their refresh tokens immediately
 * - JWT filter already rejects disabled users on every request
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentEventRepository paymentEventRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private CarbonEntryRepository carbonEntryRepository;
    @Autowired private HealthLogRepository healthLogRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private AiUsageLogRepository aiUsageLogRepository;
    @Autowired private UserAchievementRepository userAchievementRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AuditLogService auditLogService;

    @Value("${spring.ai.google-genai.api-key:}")
    private String geminiApiKey;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    // ================================================================
    // ENHANCED ANALYTICS — All from DB aggregates, zero fake data
    // ================================================================

    public Map<String, Object> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();

        // User counts
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByEnabled(true);
        long blockedUsers = userRepository.countByEnabled(false);
        long sellerCount = userRepository.countByRole(Role.SELLER);
        long adminCount = userRepository.countByRole(Role.ADMIN);
        long newUsers = userRepository.countCreatedSince(LocalDateTime.now().minusDays(30));

        analytics.put("totalUsers", totalUsers);
        analytics.put("activeUsers", activeUsers);
        analytics.put("blockedUsers", blockedUsers);
        analytics.put("sellerCount", sellerCount);
        analytics.put("adminCount", adminCount);
        analytics.put("customerCount", totalUsers - sellerCount - adminCount);
        analytics.put("newUsers", newUsers);

        // Carbon stats
        long totalCarbonEntries = carbonEntryRepository.count();
        BigDecimal totalCo2 = carbonEntryRepository.sumTotalEmissions();
        analytics.put("totalCarbonEntries", totalCarbonEntries);
        analytics.put("totalCo2", totalCo2);

        // Health stats
        long totalHealthRecords = healthLogRepository.count();
        analytics.put("totalHealthRecords", totalHealthRecords);

        // Product stats
        long totalProducts = productRepository.count();
        long activeProducts = productRepository.countByStatus(ProductStatus.ACTIVE);
        long outOfStockProducts = productRepository.countByStatus(ProductStatus.OUT_OF_STOCK);
        analytics.put("totalProducts", totalProducts);
        analytics.put("activeProducts", activeProducts);
        analytics.put("outOfStockProducts", outOfStockProducts);

        // Order stats
        long totalOrders = orderRepository.count();
        long pendingOrders = orderRepository.countByStatus(Order.OrderStatus.PENDING_PAYMENT)
                + orderRepository.countByStatus(Order.OrderStatus.PAID);
        long completedOrders = orderRepository.countByStatus(Order.OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatus(Order.OrderStatus.CANCELLED);

        List<Order.OrderStatus> revenueStatuses = Arrays.asList(
                Order.OrderStatus.PAID, Order.OrderStatus.PROCESSING,
                Order.OrderStatus.SHIPPED, Order.OrderStatus.DELIVERED);
        BigDecimal totalRevenue = orderRepository.sumRevenueByStatuses(revenueStatuses);

        analytics.put("totalOrders", totalOrders);
        analytics.put("pendingOrders", pendingOrders);
        analytics.put("completedOrders", completedOrders);
        analytics.put("cancelledOrders", cancelledOrders);
        analytics.put("totalRevenue", totalRevenue);

        // AI usage stats
        long totalAiRequests = aiUsageLogRepository.count();
        long failedAiRequests = aiUsageLogRepository.countBySuccess(false);
        analytics.put("totalAiRequests", totalAiRequests);
        analytics.put("failedAiRequests", failedAiRequests);

        // Review stats
        long pendingReviews = reviewRepository.countByStatus(Review.ReviewStatus.PENDING);
        analytics.put("pendingReviews", pendingReviews);

        return analytics;
    }

    // ================================================================
    // USER MANAGEMENT
    // ================================================================

    public Page<User> getUsers(Pageable pageable) {
        return userRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<User> searchUsers(String query, Pageable pageable) {
        return userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                query, query, pageable);
    }

    public Page<User> getUsersByRole(Role role, Pageable pageable) {
        return userRepository.findByRole(role, pageable);
    }

    public Page<User> getUsersByEnabled(boolean enabled, Pageable pageable) {
        return userRepository.findByEnabledOrderByCreatedAtDesc(enabled, pageable);
    }

    public Page<User> getUsersByRoleAndEnabled(Role role, boolean enabled, Pageable pageable) {
        return userRepository.findByRoleAndEnabledOrderByCreatedAtDesc(role, enabled, pageable);
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    /**
     * Block/unblock user. When blocking:
     * 1. Set enabled=false → JWT filter rejects on next request
     * 2. Revoke all refresh tokens → user cannot get new access tokens
     */
    @Transactional
    public User updateUserStatus(Long adminId, Long userId, boolean enabled) {
        User user = getUser(userId);
        boolean wasEnabled = user.getEnabled();
        user.setEnabled(enabled);
        user = userRepository.save(user);

        if (!enabled) {
            // Revoke all refresh tokens so user cannot get new access tokens
            refreshTokenRepository.revokeAllByUserId(userId);
        }

        auditLogService.log(adminId, enabled ? "ACCOUNT_ENABLE" : "ACCOUNT_DISABLE",
                "User/" + userId,
                "Admin " + adminId + " " + (enabled ? "enabled" : "disabled") + " user " + userId);

        log.info("Admin {} {} user {}", adminId, enabled ? "enabled" : "disabled", userId);
        return user;
    }

    /**
     * Change user role with strict security restrictions:
     * - Cannot promote to ADMIN (only AdminBootstrap via ADMIN_EMAIL can)
     * - Cannot change own role
     * - Cannot demote the last admin
     */
    @Transactional
    public User updateUserRole(Long adminId, Long userId, Role newRole) {
        // SECURITY: Cannot promote to ADMIN via this endpoint
        if (newRole == Role.ADMIN) {
            throw new BadRequestException("Cannot promote to ADMIN. Use ADMIN_EMAIL environment variable.");
        }

        // SECURITY: Cannot change own role
        if (userId.equals(adminId)) {
            throw new BadRequestException("Cannot change your own role.");
        }

        User user = getUser(userId);
        Role previousRole = user.getRole();

        if (previousRole == newRole) {
            return user; // No change needed
        }

        // SECURITY: Cannot demote the last admin
        if (previousRole == Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new BadRequestException("Cannot demote the last admin. Assign another admin first via ADMIN_EMAIL.");
            }
        }

        user.setRole(newRole);
        user = userRepository.save(user);

        auditLogService.log(adminId, "ROLE_CHANGE",
                "User/" + userId,
                "Admin " + adminId + " changed user " + userId + " role from " + previousRole + " to " + newRole);

        log.info("Admin {} changed user {} role: {} → {}", adminId, userId, previousRole, newRole);
        return user;
    }

    // ================================================================
    // USER 360° PROFILE
    // ================================================================

    public Map<String, Object> getUserDetail(Long userId) {
        User user = getUser(userId);
        Map<String, Object> detail = new HashMap<>();

        // Basic info (safe — no passwords/tokens)
        detail.put("id", user.getId());
        detail.put("name", user.getName());
        detail.put("email", user.getEmail());
        detail.put("role", user.getRole());
        detail.put("enabled", user.getEnabled());
        detail.put("provider", user.getProvider());
        detail.put("country", user.getCountry());
        detail.put("city", user.getCity());
        detail.put("isPremium", user.getIsPremium());
        detail.put("createdAt", user.getCreatedAt());

        // Carbon summary
        BigDecimal totalEmissions = carbonEntryRepository.sumTotalEmissionsByUserId(userId);
        BigDecimal totalAvoided = carbonEntryRepository.sumTotalAvoidedByUserId(userId);
        long carbonEntryCount = carbonEntryRepository.countByUserId(userId);
        List<Object[]> categoryBreakdown = carbonEntryRepository.categoryBreakdownWithTypeByUserId(userId);

        Map<String, Object> carbonSummary = new HashMap<>();
        carbonSummary.put("totalEmissions", totalEmissions);
        carbonSummary.put("totalAvoided", totalAvoided);
        carbonSummary.put("entryCount", carbonEntryCount);
        carbonSummary.put("categoryBreakdown", categoryBreakdown);
        detail.put("carbonSummary", carbonSummary);

        // Health summary
        long healthLogCount = healthLogRepository.countByUserId(userId);
        Map<String, Object> healthSummary = new HashMap<>();
        healthSummary.put("entryCount", healthLogCount);
        detail.put("healthSummary", healthSummary);

        // Shop summary
        long orderCount = orderRepository.countByUserId(userId);
        long pendingOrderCount = orderRepository.countByUserIdAndStatus(userId, Order.OrderStatus.PENDING_PAYMENT)
                + orderRepository.countByUserIdAndStatus(userId, Order.OrderStatus.PAID);
        long completedOrderCount = orderRepository.countByUserIdAndStatus(userId, Order.OrderStatus.DELIVERED);
        BigDecimal totalSpending = orderRepository.sumSpendingByUserId(userId);

        Map<String, Object> shopSummary = new HashMap<>();
        shopSummary.put("orderCount", orderCount);
        shopSummary.put("pendingOrders", pendingOrderCount);
        shopSummary.put("completedOrders", completedOrderCount);
        shopSummary.put("totalSpending", totalSpending);
        detail.put("shopSummary", shopSummary);

        // Achievements
        List<UserAchievement> achievements = userAchievementRepository.findByUserId(userId);
        detail.put("achievementCount", achievements.size());

        // AI usage summary
        long aiRequestCount = aiUsageLogRepository.countByUserId(userId);
        long aiFailedCount = aiUsageLogRepository.countByUserIdAndSuccess(userId, false);
        Optional<AiUsageLog> lastAiRequest = aiUsageLogRepository.findTopByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Object> aiSummary = new HashMap<>();
        aiSummary.put("requestCount", aiRequestCount);
        aiSummary.put("failedCount", aiFailedCount);
        aiSummary.put("lastRequest", lastAiRequest.map(AiUsageLog::getCreatedAt).orElse(null));
        aiSummary.put("lastProvider", lastAiRequest.map(AiUsageLog::getProvider).orElse(null));
        detail.put("aiSummary", aiSummary);

        // Notes count
        long notesCount = noteRepository.countByUserId(userId);
        detail.put("notesCount", notesCount);

        return detail;
    }

    public Page<CarbonEntry> getUserCarbonEntries(Long userId, Pageable pageable) {
        getUser(userId); // Verify user exists
        return carbonEntryRepository.findByUserIdOrderByEntryDateDesc(userId, pageable);
    }

    public Map<String, Object> getUserCarbonStats(Long userId) {
        getUser(userId);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEmissions", carbonEntryRepository.sumTotalEmissionsByUserId(userId));
        stats.put("totalAvoided", carbonEntryRepository.sumTotalAvoidedByUserId(userId));
        stats.put("entryCount", carbonEntryRepository.countByUserId(userId));
        stats.put("categoryBreakdown", carbonEntryRepository.categoryBreakdownWithTypeByUserId(userId));
        return stats;
    }

    public Page<HealthLog> getUserHealthLogs(Long userId, Pageable pageable) {
        getUser(userId);
        return healthLogRepository.findByUserIdAndEntryDateBetween(
                userId, Instant.EPOCH, Instant.now(), pageable);
    }

    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        getUser(userId);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public List<UserAchievement> getUserAchievements(Long userId) {
        getUser(userId);
        return userAchievementRepository.findByUserId(userId);
    }

    // ================================================================
    // PRODUCT MANAGEMENT
    // ================================================================

    public Page<Product> getProducts(Pageable pageable) {
        return productRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<Product> getProductsByStatus(ProductStatus status, Pageable pageable) {
        return productRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional
    public Product updateProductStatus(Long adminId, Long productId, ProductStatus newStatus) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        ProductStatus previousStatus = product.getStatus();
        product.setStatus(newStatus);
        product = productRepository.save(product);

        auditLogService.log(adminId, "PRODUCT_STATUS_CHANGE",
                "Product/" + productId,
                "Admin " + adminId + " changed product " + productId + " status from " + previousStatus + " to " + newStatus);

        log.info("Admin {} changed product {} status: {} → {}", adminId, productId, previousStatus, newStatus);
        return product;
    }

    // ================================================================
    // ORDER MANAGEMENT
    // ================================================================

    public Page<Order> getOrders(Pageable pageable) {
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<Order> getOrdersByStatus(Order.OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional
    public Order updateOrderStatus(Long adminId, Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        Order.OrderStatus previousStatus = order.getStatus();
        order.getStatus().validateTransitionTo(newStatus);

        // If cancelling, restore stock
        if (newStatus == Order.OrderStatus.CANCELLED) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                productRepository.restoreStock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(newStatus);
        order = orderRepository.save(order);

        auditLogService.log(adminId, "ORDER_STATE_OVERRIDE",
                "Order/" + orderId,
                "Admin " + adminId + " changed order " + orderId + " status from " + previousStatus + " to " + newStatus);

        log.info("Admin {} changed order {} status: {} → {}", adminId, orderId, previousStatus, newStatus);
        return order;
    }

    // ================================================================
    // PAYMENT EVENTS
    // ================================================================

    public Page<PaymentEvent> getPaymentEvents(Pageable pageable) {
        return paymentEventRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    // ================================================================
    // AUDIT LOGS
    // ================================================================

    public Page<AuditLog> getAuditLogs(String action, Long userId, Pageable pageable) {
        if (action != null && userId != null) {
            return auditLogRepository.findByActionAndUserIdOrderByCreatedAtDesc(action, userId, pageable);
        } else if (action != null) {
            return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
        } else if (userId != null) {
            return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public List<AuditLog> getRecentAuditLogs(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).getContent();
    }

    // ================================================================
    // REVIEWS
    // ================================================================

    public Page<Review> getReviews(Review.ReviewStatus status, Pageable pageable) {
        if (status != null) {
            return reviewRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return reviewRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public Review updateReviewStatus(Long adminId, Long reviewId, Review.ReviewStatus newStatus) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        Review.ReviewStatus previousStatus = review.getStatus();
        review.setStatus(newStatus);
        review = reviewRepository.save(review);

        // Recalculate product rating when review is approved/hidden
        if (newStatus == Review.ReviewStatus.APPROVED || previousStatus == Review.ReviewStatus.APPROVED) {
            recalculateProductRating(review.getProductId());
        }

        auditLogService.log(adminId, "REVIEW_STATUS_CHANGE",
                "Review/" + reviewId,
                "Admin " + adminId + " changed review " + reviewId + " status from " + previousStatus + " to " + newStatus);

        log.info("Admin {} changed review {} status: {} → {}", adminId, reviewId, previousStatus, newStatus);
        return review;
    }

    /**
     * Recalculate a product's average rating from approved reviews only.
     * Hidden/flagged/pending reviews are excluded.
     */
    private void recalculateProductRating(Long productId) {
        try {
            Double avgRating = reviewRepository.avgRatingByProductId(productId);
            long approvedCount = reviewRepository.countByProductIdAndStatus(productId, Review.ReviewStatus.APPROVED);

            Product product = productRepository.findById(productId).orElse(null);
            if (product != null) {
                product.setRating(avgRating != null ? BigDecimal.valueOf(avgRating) : BigDecimal.ZERO);
                product.setRatingCount((int) approvedCount);
                productRepository.save(product);
            }
        } catch (Exception e) {
            log.error("Failed to recalculate product rating for product {}: {}", productId, e.getMessage());
        }
    }

    // ================================================================
    // AI USAGE
    // ================================================================

    public Page<AiUsageLog> getAiUsageLogs(Pageable pageable) {
        return aiUsageLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Map<String, Object> getAiUsageStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", aiUsageLogRepository.count());
        stats.put("failedRequests", aiUsageLogRepository.countBySuccess(false));
        stats.put("providerBreakdown", aiUsageLogRepository.countByProvider());
        return stats;
    }

    // ================================================================
    // SYSTEM HEALTH
    // ================================================================

    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();

        // Database
        try {
            long userCount = userRepository.count();
            health.put("database", Map.of("status", "HEALTHY", "details", "PostgreSQL connected, " + userCount + " users"));
        } catch (Exception e) {
            health.put("database", Map.of("status", "FAILED", "details", "Connection error: " + e.getMessage()));
        }

        // AI Provider
        String aiProvider = "none";
        String aiStatus = "NOT_CONFIGURED";
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            aiProvider = "gemini";
            aiStatus = "CONFIGURED";
        } else if (openaiApiKey != null && !openaiApiKey.isBlank()) {
            aiProvider = "openai";
            aiStatus = "CONFIGURED";
        }
        health.put("ai", Map.of("status", aiStatus, "provider", aiProvider));

        // Email / SMTP
        String emailStatus = (mailHost != null && !mailHost.isBlank()) ? "CONFIGURED" : "NOT_CONFIGURED";
        health.put("email", Map.of("status", emailStatus, "host", mailHost != null ? mailHost : ""));

        // Payment (Razorpay)
        String paymentStatus = (razorpayKeyId != null && !razorpayKeyId.isBlank()) ? "CONFIGURED" : "NOT_CONFIGURED";
        health.put("payment", Map.of("status", paymentStatus));

        // Weather API (always available via Open-Meteo, no key needed)
        health.put("weather", Map.of("status", "CONFIGURED", "provider", "Open-Meteo"));

        // News API (GNews with server proxy)
        health.put("news", Map.of("status", "CONFIGURED", "provider", "GNews/RSS"));

        return health;
    }

    // ================================================================
    // ANALYTICS CHARTS DATA
    // ================================================================

    public Map<String, Object> getAnalyticsChartData() {
        Map<String, Object> data = new HashMap<>();

        // Carbon daily emissions (last 30 days)
        Instant thirtyDaysAgo = Instant.now().minusSeconds(30 * 86400L);
        List<Object[]> carbonTrend = carbonEntryRepository.dailyEmissionsByPeriod(thirtyDaysAgo, Instant.now());
        data.put("carbonTrend", carbonTrend);

        // Carbon category breakdown (global)
        List<Object[]> carbonBreakdown = carbonEntryRepository.categoryEmissionBreakdown();
        data.put("carbonCategoryBreakdown", carbonBreakdown);

        // Order status distribution
        List<Object[]> orderStatusDist = orderRepository.countGroupByStatus();
        data.put("orderStatusDistribution", orderStatusDist);

        // AI usage daily (last 30 days)
        List<Object[]> aiDaily = aiUsageLogRepository.dailyCountSince(LocalDateTime.now().minusDays(30));
        data.put("aiDailyUsage", aiDaily);

        // Review status distribution
        Map<String, Long> reviewStats = new HashMap<>();
        for (Review.ReviewStatus s : Review.ReviewStatus.values()) {
            reviewStats.put(s.name(), reviewRepository.countByStatus(s));
        }
        data.put("reviewStatusDistribution", reviewStats);

        return data;
    }
}
