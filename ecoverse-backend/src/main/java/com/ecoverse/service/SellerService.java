package com.ecoverse.service;

import com.ecoverse.dto.shop.OrderResponse;
import com.ecoverse.dto.shop.OrderResponse.OrderItemResponse;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.Order;
import com.ecoverse.model.OrderItem;
import com.ecoverse.model.User;
import com.ecoverse.repository.OrderItemRepository;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Seller-specific business logic.
 *
 * SECURITY:
 * - Seller can ONLY see orders containing their products
 * - Seller can ONLY update status of orders containing their products
 * - Seller can ONLY transition: PAID → PROCESSING → SHIPPED → DELIVERED
 * - Seller CANNOT: refund, mark paid, change payment state, cancel orders, modify other sellers' data
 *
 * MULTI-SELLER ORDERS:
 * - If one order contains products from multiple sellers, each seller sees only THEIR items
 * - Seller does NOT see full order total (only their items' total)
 * - Seller does NOT see other sellers' items or private customer data beyond fulfillment needs
 */
@Service
public class SellerService {

    private static final Logger log = LoggerFactory.getLogger(SellerService.class);

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private EmailService emailService;
    @Autowired private UserRepository userRepository;

    /**
     * Get orders containing products from this seller (paginated).
     * Seller sees only orders where at least one product belongs to them.
     */
    public Page<OrderResponse> getSellerOrders(Long sellerId, Pageable pageable) {
        Page<Order> orders = orderRepository.findOrdersContainingSellerProducts(sellerId, pageable);
        return orders.map(order -> mapToSellerOrderResponse(order, sellerId));
    }

    /**
     * Get a specific order that contains this seller's products.
     * Returns only the seller's portion of the order.
     * If the order doesn't contain any of the seller's products → 404.
     */
    public OrderResponse getSellerOrder(Long sellerId, Long orderId) {
        Order order = orderRepository.findByIdAndSellerProduct(orderId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return mapToSellerOrderResponse(order, sellerId);
    }

    /**
     * Update order status (seller-controlled transitions only).
     *
     * Allowed transitions:
     *   PAID → PROCESSING (seller starts preparing)
     *   PROCESSING → SHIPPED (seller ships the order)
     *   SHIPPED → DELIVERED (order delivered)
     *
     * NOT allowed:
     *   - Any refund-related transitions
     *   - Marking payment as paid
     *   - Cancelling orders
     *   - Any transition backward (e.g., PROCESSING → PAID)
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long sellerId, Long orderId, Order.OrderStatus newStatus) {
        // Verify order contains this seller's products
        Order order = orderRepository.findByIdAndSellerProduct(orderId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate the seller is allowed to make this transition
        validateSellerTransition(order.getStatus(), newStatus);

        // Apply the transition
        order.getStatus().validateTransitionTo(newStatus);
        order.setStatus(newStatus);
        order = orderRepository.save(order);

        log.info("Seller {} updated order {} status: {} → {}", sellerId, orderId, order.getStatus(), newStatus);

        // Send order status notification email (async)
        try {
            User orderUser = userRepository.findById(order.getUserId()).orElse(null);
            if (orderUser != null) {
                if (newStatus == Order.OrderStatus.SHIPPED) {
                    emailService.sendOrderShipped(orderUser.getEmail(), order.getId());
                } else if (newStatus == Order.OrderStatus.DELIVERED) {
                    emailService.sendOrderDelivered(orderUser.getEmail(), order.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send order status email for orderId={}: {}", order.getId(), e.getMessage());
        }

        return mapToSellerOrderResponse(order, sellerId);
    }

    /**
     * Validate that a seller is allowed to make this status transition.
     * Sellers can only move orders forward in the fulfillment pipeline.
     */
    private void validateSellerTransition(Order.OrderStatus current, Order.OrderStatus target) {
        // Allowed seller transitions
        if (current == Order.OrderStatus.PAID && target == Order.OrderStatus.PROCESSING) return;
        if (current == Order.OrderStatus.PROCESSING && target == Order.OrderStatus.SHIPPED) return;
        if (current == Order.OrderStatus.SHIPPED && target == Order.OrderStatus.DELIVERED) return;

        // Same status is always allowed (idempotent)
        if (current == target) return;

        throw new ForbiddenException(
                "Sellers can only transition: PAID → PROCESSING → SHIPPED → DELIVERED. " +
                "Requested: " + current + " → " + target);
    }

    /**
     * Map an order to a seller-scoped response.
     * Only includes items belonging to this seller.
     * Does NOT expose other sellers' items or full order total.
     */
    private OrderResponse mapToSellerOrderResponse(Order order, Long sellerId) {
        // Get only this seller's items
        List<OrderItem> sellerItems = orderItemRepository.findByOrderIdAndSellerId(order.getId(), sellerId);

        // Calculate seller's portion of the total
        java.math.BigDecimal sellerTotal = sellerItems.stream()
                .map(item -> item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        List<OrderItemResponse> itemResponses = sellerItems.stream()
                .map(oi -> OrderItemResponse.builder()
                        .id(oi.getId())
                        .productId(oi.getProductId())
                        .productName(oi.getProductName())
                        .quantity(oi.getQuantity())
                        .price(oi.getPrice())
                        .unitPrice(oi.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .totalPrice(sellerTotal) // Seller sees only their portion
                .status(order.getStatus() != null ? order.getStatus().name() : "PENDING_PAYMENT")
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "PENDING")
                .paymentMethod(order.getPaymentMethod())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
