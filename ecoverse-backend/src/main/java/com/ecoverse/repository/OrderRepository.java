package com.ecoverse.repository;

import com.ecoverse.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Find an order by ID and user ID — ownership-scoped lookup.
     * Used for IDOR protection: users can only access their own orders.
     * Returns empty if the order doesn't exist or belongs to another user.
     */
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    /**
     * Find an order by idempotency key.
     * Used for duplicate order prevention: if the same idempotency key
     * is seen again, the existing order is returned instead of creating a new one.
     * Null keys are not looked up (they bypass idempotency).
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    // ===== SELLER QUERIES =====

    /**
     * Find orders containing products from a specific seller.
     * Joins through order_items → products to find orders where
     * at least one product belongs to the seller.
     */
    @Query("SELECT DISTINCT o FROM Order o JOIN OrderItem oi ON o.id = oi.orderId " +
           "JOIN Product p ON oi.productId = p.id " +
           "WHERE p.sellerId = :sellerId " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findOrdersContainingSellerProducts(@Param("sellerId") Long sellerId, Pageable pageable);

    /**
     * Find a specific order that contains products from a given seller.
     * Used for seller order detail access — seller can only see orders
     * that contain their products.
     */
    @Query("SELECT DISTINCT o FROM Order o JOIN OrderItem oi ON o.id = oi.orderId " +
           "JOIN Product p ON oi.productId = p.id " +
           "WHERE o.id = :orderId AND p.sellerId = :sellerId")
    Optional<Order> findByIdAndSellerProduct(@Param("orderId") Long orderId, @Param("sellerId") Long sellerId);

    // ===== ADMIN QUERIES =====

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(Order.OrderStatus status, Pageable pageable);

    // ===== ADMIN AGGREGATE QUERIES =====

    long countByStatus(Order.OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status IN :statuses")
    java.math.BigDecimal sumRevenueByStatuses(@Param("statuses") List<Order.OrderStatus> statuses);

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupByStatus();

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.userId = :userId")
    java.math.BigDecimal sumSpendingByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, Order.OrderStatus status);

    // ===== PAYMENT EXPIRY QUERIES =====

    /**
     * Find expired PENDING_PAYMENT orders (online payments only).
     * Used by PaymentExpiryScheduler to release stock for abandoned orders.
     * Excludes COD orders (they stay PENDING_PAYMENT until delivery or manual cancel).
     */
    @Query("SELECT o FROM Order o WHERE o.status = :status " +
           "AND o.paymentMethod NOT IN :excludeMethods " +
           "AND o.createdAt < :cutoff")
    List<Order> findExpiredPendingOrders(@Param("status") Order.OrderStatus status,
                                          @Param("excludeMethods") List<String> excludeMethods,
                                          @Param("cutoff") LocalDateTime cutoff);
}
