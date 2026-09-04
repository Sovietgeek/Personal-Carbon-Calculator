package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.shop.*;
import com.ecoverse.model.Order;
import com.ecoverse.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Autowired
    private ShopService shopService;

    // ================================================================
    // PRODUCTS — Read (Public)
    // ================================================================

    /**
     * Get products with search/filter/sort/pagination.
     * All parameters are optional.
     * Only ACTIVE products are shown in the shop.
     */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProducts(
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer ecoRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        Pageable pageable = buildPageable(page, size, sort, direction);
        Page<ProductResponse> productPage = shopService.getProducts(category, keyword, minPrice, maxPrice, ecoRating, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(productPage)));
    }

    /**
     * Get a single product by ID.
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        ProductResponse response = shopService.getProduct(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get seller's own products (all statuses, paginated).
     * Only accessible by SELLER role.
     */
    @GetMapping("/products/seller")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSellerProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductResponse> productPage = shopService.getSellerProducts(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(productPage)));
    }

    // ================================================================
    // PRODUCTS — Create/Update/Delete (Seller Operations)
    // ================================================================

    /**
     * Create a new product. SELLER or ADMIN only.
     * Seller ID comes from authenticated user context — NEVER from request body.
     */
    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        Long userId = getCurrentUserId();
        ProductResponse response = shopService.createProduct(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    /**
     * Update an existing product. SELLER or ADMIN only.
     * Ownership enforced: seller can only update their own products.
     */
    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        Long userId = getCurrentUserId();
        ProductResponse response = shopService.updateProduct(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    /**
     * Archive (soft-delete) a product. SELLER or ADMIN only.
     * Ownership enforced: seller can only archive their own products.
     */
    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        shopService.deleteProduct(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Product archived successfully", null));
    }

    // ================================================================
    // CART — Server-Authoritative Operations
    // ================================================================

    /**
     * Add item to cart. Quantity must be between 1 and 100.
     * Stock is NOT checked at cart time (cart ≠ inventory reservation).
     */
    @PostMapping("/cart")
    public ResponseEntity<ApiResponse<CartItemResponse>> addToCart(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity) {
        Long userId = getCurrentUserId();
        CartItemResponse response = shopService.addToCart(userId, productId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", response));
    }

    /**
     * Update cart item quantity. Ownership enforced.
     */
    @PutMapping("/cart/{id}")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateCartItemQuantity(
            @PathVariable Long id,
            @RequestParam int quantity) {
        Long userId = getCurrentUserId();
        CartItemResponse response = shopService.updateCartItemQuantity(userId, id, quantity);
        return ResponseEntity.ok(ApiResponse.success("Cart item updated", response));
    }

    /**
     * Remove item from cart. Ownership enforced.
     */
    @DeleteMapping("/cart/{id}")
    public ResponseEntity<ApiResponse<Void>> removeFromCart(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        shopService.removeFromCart(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", null));
    }

    /**
     * Get user's cart. Batch-loads products (no N+1 query).
     */
    @GetMapping("/cart")
    public ResponseEntity<ApiResponse<java.util.List<CartItemResponse>>> getCart() {
        Long userId = getCurrentUserId();
        java.util.List<CartItemResponse> responses = shopService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Clear all items from cart.
     */
    @DeleteMapping("/cart")
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        Long userId = getCurrentUserId();
        shopService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared successfully", null));
    }

    // ================================================================
    // ORDERS — Transactional Order Creation
    // ================================================================

    /**
     * Place an order from the user's cart. FULLY TRANSACTIONAL.
     * Supports idempotency via X-Idempotency-Key header.
     *
     * Flow: validate cart → load products → validate status/stock →
     *       server-side price calculation → create Order+OrderItems →
     *       atomic stock decrement → clear cart → commit
     * If ANY step fails, ALL changes are rolled back.
     */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @RequestParam String paymentMethod,
            @RequestParam String shippingAddress,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = getCurrentUserId();
        OrderResponse response = shopService.placeOrder(userId, paymentMethod, shippingAddress, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", response));
    }

    // ================================================================
    // ORDERS — Read Operations (Ownership Enforced)
    // ================================================================

    /**
     * Get a single order by ID. Ownership enforced (IDOR protection).
     */
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        OrderResponse response = shopService.getOrder(userId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get user's orders (paginated, newest first).
     */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderResponse> orderPage = shopService.getOrders(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(toPaginatedResponse(orderPage)));
    }

    // ================================================================
    // ORDERS — Status Transitions
    // ================================================================

    /**
     * Update order status. Ownership enforced + legal transition validation.
     * Users can CANCEL their own PENDING_PAYMENT orders.
     */
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        Long userId = getCurrentUserId();
        OrderResponse response = shopService.updateOrderStatus(userId, id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Order status updated", response));
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getPrincipal().toString());
    }

    private Pageable buildPageable(int page, int size, String sort, String direction) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        // Whitelist sortable fields to prevent injection
        String safeSort = switch (sort) {
            case "price", "name", "ecoRating", "rating", "createdAt", "updatedAt" -> sort;
            default -> "createdAt";
        };

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(dir, safeSort));
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
