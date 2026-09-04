package com.ecoverse.repository;

import com.ecoverse.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Find order items for a specific order that belong to a specific seller.
     * Used by seller order view to show only the seller's items in an order.
     */
    @Query("SELECT oi FROM OrderItem oi JOIN Product p ON oi.productId = p.id " +
           "WHERE oi.orderId = :orderId AND p.sellerId = :sellerId")
    List<OrderItem> findByOrderIdAndSellerId(@Param("orderId") Long orderId, @Param("sellerId") Long sellerId);

    /**
     * Find all order items for products from a specific seller.
     * Returns only the seller's items (not other sellers' items in multi-seller orders).
     */
    @Query("SELECT oi FROM OrderItem oi JOIN Product p ON oi.productId = p.id " +
           "WHERE p.sellerId = :sellerId ORDER BY oi.createdAt DESC")
    List<OrderItem> findByProductSellerId(@Param("sellerId") Long sellerId);
}
