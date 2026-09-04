package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.shop.OrderResponse;
import com.ecoverse.dto.shop.UpdateOrderStatusRequest;
import com.ecoverse.model.Order;
import com.ecoverse.service.SellerService;
import jakarta.validation.Valid;
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
 * Seller-specific endpoints.
 *
 * ACCESS: SELLER or ADMIN role required.
 * - Sellers can only see orders containing their products
 * - Sellers can only update order fulfillment status (PAID → PROCESSING → SHIPPED → DELIVERED)
 * - Sellers CANNOT: refund, mark paid, cancel, modify other sellers' data
 */
@RestController
@RequestMapping("/api/seller")
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
public class SellerController {

    @Autowired
    private SellerService sellerService;

    /**
     * Get orders containing this seller's products (paginated).
     * Seller sees only their items in each order.
     *
     * GET /api/seller/orders
     */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSellerOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long sellerId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderResponse> orderPage = sellerService.getSellerOrders(sellerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(orderPage)));
    }

    /**
     * Get a specific order (must contain seller's products).
     * Returns only the seller's portion of the order.
     *
     * GET /api/seller/orders/{id}
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getSellerOrder(@PathVariable Long id) {
        Long sellerId = getCurrentUserId();
        OrderResponse response = sellerService.getSellerOrder(sellerId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update order status (seller-controlled transitions only).
     * Allowed: PAID → PROCESSING → SHIPPED → DELIVERED
     *
     * PATCH /api/seller/orders/{id}/status
     */
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        Long sellerId = getCurrentUserId();
        OrderResponse response = sellerService.updateOrderStatus(sellerId, id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Order status updated", response));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user found in security context");
        }
        return Long.parseLong(auth.getPrincipal().toString());
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
}
