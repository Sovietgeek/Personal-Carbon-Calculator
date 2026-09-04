package com.ecoverse.service;

import com.ecoverse.dto.shop.*;
import com.ecoverse.dto.shop.OrderResponse.OrderItemResponse;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.*;
import com.ecoverse.repository.*;
import com.ecoverse.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    /** Maximum quantity per cart item. Prevents absurd quantities. */
    private static final int MAX_CART_QUANTITY = 100;

    @Autowired private ProductRepository productRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    // ================================================================
    // PRODUCT — Read Operations
    // ================================================================

    /**
     * Get all available products (legacy, non-paginated).
     * Delegates to paginated version for consistency.
     */
    public List<ProductResponse> getProducts(String category) {
        String sanitizedCategory = InputSanitizer.sanitize(category, 50);
        List<Product> products;
        if (sanitizedCategory != null && !sanitizedCategory.isEmpty() && !"all".equals(sanitizedCategory)) {
            products = productRepository.findByCategoryAndIsAvailableTrue(sanitizedCategory);
        } else {
            products = productRepository.findByIsAvailableTrue();
        }
        return products.stream().map(this::mapToProductResponse).collect(Collectors.toList());
    }

    /**
     * Get products with full search/filter/sort capabilities (paginated).
     * All parameters are optional — null parameters are ignored.
     * Only ACTIVE products are shown in the shop.
     */
    public Page<ProductResponse> getProducts(String category, String keyword,
                                              BigDecimal minPrice, BigDecimal maxPrice,
                                              Integer ecoRating, Pageable pageable) {
        String sanitizedCategory = InputSanitizer.sanitize(category, 50);
        String sanitizedKeyword = InputSanitizer.sanitize(keyword, 100);

        // Treat empty strings as null for the query
        if (sanitizedCategory != null && (sanitizedCategory.isEmpty() || "all".equalsIgnoreCase(sanitizedCategory))) {
            sanitizedCategory = null;
        }
        if (sanitizedKeyword != null && sanitizedKeyword.isBlank()) {
            sanitizedKeyword = null;
        }

        Page<Product> productPage = productRepository.searchProducts(
                ProductStatus.ACTIVE, sanitizedCategory, sanitizedKeyword,
                minPrice, maxPrice, ecoRating, pageable);

        return productPage.map(this::mapToProductResponse);
    }

    /**
     * Get a single product by ID.
     */
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        return mapToProductResponse(product);
    }

    /**
     * Get all products by a seller (all statuses, paginated).
     * Used by sellers to manage their own products.
     */
    public Page<ProductResponse> getSellerProducts(Long sellerId, Pageable pageable) {
        Page<Product> productPage = productRepository.findAllBySellerId(sellerId, pageable);
        return productPage.map(this::mapToProductResponse);
    }

    // ================================================================
    // PRODUCT — Create/Update/Delete (Seller Operations)
    // ================================================================

    /**
     * Create a new product. Seller ID comes from authenticated user ONLY.
     * New products default to ACTIVE status.
     */
    public ProductResponse createProduct(Long userId, ProductRequest req) {
        // Sanitize inputs
        String name = InputSanitizer.sanitize(req.getName(), InputSanitizer.MAX_TITLE_LENGTH);
        String description = InputSanitizer.sanitizeText(req.getDescription());
        String category = InputSanitizer.sanitize(req.getCategory(), 50);
        String imageUrl = InputSanitizer.validateImageUrl(req.getImageUrl());

        Product product = Product.builder()
                .sellerId(userId) // Server-authoritative — from auth context only
                .name(name)
                .description(description)
                .category(category)
                .price(req.getPrice())
                .imageUrl(imageUrl)
                .ecoRating(req.getEcoRating())
                .isSecondhand(req.getIsSecondhand() != null ? req.getIsSecondhand() : false)
                .stock(req.getStock() != null ? req.getStock() : 0)
                .status(ProductStatus.ACTIVE) // Always ACTIVE on creation
                .brand(InputSanitizer.sanitize(req.getBrand(), 100))
                .mrp(req.getMrp())
                .discountPercent(req.getDiscountPercent())
                .features(req.getFeatures() != null ? 
                    com.ecoverse.util.InputSanitizer.sanitize(
                        "[" + req.getFeatures().stream()
                            .map(f -> "\"" + f.replace("\"", "") + "\"")
                            .collect(java.util.stream.Collectors.joining(",")) + "]", 
                        5000) : null)
                .highlights(InputSanitizer.sanitize(req.getHighlights(), 500))
                .tags(InputSanitizer.sanitize(req.getTags(), 500))
                .rating(req.getRating() != null ? req.getRating() : BigDecimal.valueOf(4.0))
                .ratingCount(req.getRatingCount() != null ? req.getRatingCount() : 0)
                .deliveryDays(req.getDeliveryDays() != null ? req.getDeliveryDays() : 5)
                .weightGrams(req.getWeightGrams())
                .build();

        product = productRepository.save(product);
        log.info("Product created: id={}, sellerId={}, name={}", product.getId(), userId, name);
        return mapToProductResponse(product);
    }

    /**
     * Update an existing product. Enforces seller ownership.
     * Only the seller who created the product can update it.
     * Admin can update any product (enforced at controller level).
     */
    @Transactional
    public ProductResponse updateProduct(Long userId, Long productId, ProductRequest req) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        // Ownership check — seller can only update their own products
        if (!product.getSellerId().equals(userId)) {
            log.warn("Seller {} attempted to update product {} owned by seller {}",
                    userId, productId, product.getSellerId());
            throw new ForbiddenException("You can only update your own products");
        }

        // Cannot update archived products
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new BadRequestException("Cannot update an archived product");
        }

        // Sanitize inputs
        String name = InputSanitizer.sanitize(req.getName(), InputSanitizer.MAX_TITLE_LENGTH);
        String description = InputSanitizer.sanitizeText(req.getDescription());
        String category = InputSanitizer.sanitize(req.getCategory(), 50);
        String imageUrl = InputSanitizer.validateImageUrl(req.getImageUrl());

        // Update fields
        if (name != null) product.setName(name);
        if (description != null) product.setDescription(description);
        if (category != null) product.setCategory(category);
        if (req.getPrice() != null) product.setPrice(req.getPrice());
        if (imageUrl != null) product.setImageUrl(imageUrl);
        if (req.getEcoRating() != null) product.setEcoRating(req.getEcoRating());
        if (req.getIsSecondhand() != null) product.setIsSecondhand(req.getIsSecondhand());
        if (req.getBrand() != null) product.setBrand(InputSanitizer.sanitize(req.getBrand(), 100));
        if (req.getMrp() != null) product.setMrp(req.getMrp());
        if (req.getDiscountPercent() != null) product.setDiscountPercent(req.getDiscountPercent());
        if (req.getHighlights() != null) product.setHighlights(InputSanitizer.sanitize(req.getHighlights(), 500));
        if (req.getTags() != null) product.setTags(InputSanitizer.sanitize(req.getTags(), 500));
        if (req.getRating() != null) product.setRating(req.getRating());
        if (req.getRatingCount() != null) product.setRatingCount(req.getRatingCount());
        if (req.getDeliveryDays() != null) product.setDeliveryDays(req.getDeliveryDays());
        if (req.getWeightGrams() != null) product.setWeightGrams(req.getWeightGrams());
        if (req.getStock() != null) {
            product.setStock(req.getStock());
            // Auto-set status based on stock
            if (req.getStock() > 0 && product.getStatus() == ProductStatus.OUT_OF_STOCK) {
                product.setStatus(ProductStatus.ACTIVE);
            } else if (req.getStock() == 0 && product.getStatus() == ProductStatus.ACTIVE) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            }
        }
        // Status update (seller can change DRAFT↔ACTIVE↔INACTIVE)
        if (req.getStatus() != null) {
            validateStatusTransition(product.getStatus(), req.getStatus());
            product.setStatus(req.getStatus());
        }

        product = productRepository.save(product);
        log.info("Product updated: id={}, sellerId={}", productId, userId);
        return mapToProductResponse(product);
    }

    /**
     * Archive (soft-delete) a product. Enforces seller ownership.
     * Archived products are not visible in the shop.
     */
    @Transactional
    public void deleteProduct(Long userId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        // Ownership check
        if (!product.getSellerId().equals(userId)) {
            log.warn("Seller {} attempted to delete product {} owned by seller {}",
                    userId, productId, product.getSellerId());
            throw new ForbiddenException("You can only delete your own products");
        }

        product.setStatus(ProductStatus.ARCHIVED);
        productRepository.save(product);
        log.info("Product archived: id={}, sellerId={}", productId, userId);
    }

    // ================================================================
    // CART — Server-Authoritative Operations
    // ================================================================

    /**
     * Add item to cart. Server validates product exists and is ACTIVE.
     * Quantity must be between 1 and MAX_CART_QUANTITY.
     * Stock is NOT checked at cart time (cart ≠ inventory reservation).
     */
    @Transactional
    public CartItemResponse addToCart(Long userId, Long productId, int quantity) {
        if (quantity < 1) {
            throw new BadRequestException("Quantity must be at least 1");
        }
        if (quantity > MAX_CART_QUANTITY) {
            throw new BadRequestException("Quantity cannot exceed " + MAX_CART_QUANTITY);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        // Product must be ACTIVE to be added to cart
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BadRequestException("Product is not available: " + product.getName());
        }

        CartItem existingItem = cartItemRepository.findByUserIdAndProductId(userId, productId).orElse(null);

        if (existingItem != null) {
            int newQuantity = existingItem.getQuantity() + quantity;
            if (newQuantity > MAX_CART_QUANTITY) {
                throw new BadRequestException("Total quantity in cart cannot exceed " + MAX_CART_QUANTITY);
            }
            existingItem.setQuantity(newQuantity);
            existingItem = cartItemRepository.save(existingItem);
            return mapToCartItemResponse(existingItem, product);
        } else {
            CartItem cartItem = CartItem.builder()
                    .userId(userId)
                    .productId(productId)
                    .quantity(quantity)
                    .build();
            cartItem = cartItemRepository.save(cartItem);
            return mapToCartItemResponse(cartItem, product);
        }
    }

    /**
     * Update the quantity of a cart item. Enforces ownership.
     */
    @Transactional
    public CartItemResponse updateCartItemQuantity(Long userId, Long cartItemId, int quantity) {
        if (quantity < 1) {
            throw new BadRequestException("Quantity must be at least 1");
        }
        if (quantity > MAX_CART_QUANTITY) {
            throw new BadRequestException("Quantity cannot exceed " + MAX_CART_QUANTITY);
        }

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", cartItemId));

        // Ownership check
        if (!item.getUserId().equals(userId)) {
            throw new ForbiddenException("You don't have access to this cart item");
        }

        item.setQuantity(quantity);
        item = cartItemRepository.save(item);

        // Load product for response
        Product product = productRepository.findById(item.getProductId()).orElse(null);
        return mapToCartItemResponse(item, product);
    }

    /**
     * Remove an item from the cart. Enforces ownership.
     */
    public void removeFromCart(Long cartItemId, Long userId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", cartItemId));

        if (!item.getUserId().equals(userId)) {
            throw new ForbiddenException("You don't have access to this cart item");
        }

        cartItemRepository.deleteByIdAndUserId(cartItemId, userId);
    }

    /**
     * Get all cart items for a user. Batch-loads products to fix N+1 query.
     */
    public List<CartItemResponse> getCart(Long userId) {
        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);
        if (cartItems.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch-load products (fixes N+1 query)
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productRepository.findAllByIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return cartItems.stream()
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    return mapToCartItemResponse(item, product);
                })
                .collect(Collectors.toList());
    }

    /**
     * Clear all items from the cart.
     */
    public void clearCart(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    // ================================================================
    // ORDER — Transactional Order Creation (THE CRITICAL PATH)
    // ================================================================

    /**
     * Place an order from the user's cart. FULLY TRANSACTIONAL.
     *
     * Flow:
     * 1. Idempotency check — if idempotencyKey exists, return existing order
     * 2. Load cart items — reject if empty
     * 3. Batch-load products by IDs
     * 4. Validate each: exists, status==ACTIVE, stock >= quantity
     * 5. Calculate totalPrice server-side from DB prices (BigDecimal)
     * 6. Create Order: status=PENDING_PAYMENT, paymentStatus=PENDING
     * 7. Create OrderItems with unitPrice snapshot
     * 8. Atomic stock decrement per item (concurrency-safe)
     * 9. Auto-set OUT_OF_STOCK if stock becomes 0
     * 10. Clear cart
     * 11. Save order with idempotencyKey
     *
     * If ANY step fails: @Transactional ROLLBACK ALL
     * (order, items, stock decrements, cart clearing are all rolled back)
     */
    @Transactional
    public OrderResponse placeOrder(Long userId, String paymentMethod, String shippingAddress, String idempotencyKey) {
        // Step 1: Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String sanitizedKey = InputSanitizer.sanitize(idempotencyKey, 255);
            Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(sanitizedKey);
            if (existingOrder.isPresent()) {
                log.info("Idempotent order request: key={}, returning existing order {}", sanitizedKey, existingOrder.get().getId());
                return mapToOrderResponse(existingOrder.get());
            }
        }

        // Step 2: Load cart items
        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty. Add items before placing an order.");
        }

        // Step 3: Batch-load products (fixes N+1)
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productRepository.findAllByIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Step 4: Validate each cart item
        for (CartItem item : cartItems) {
            Product product = productMap.get(item.getProductId());
            if (product == null) {
                throw new BadRequestException("Product not found: " + item.getProductId());
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BadRequestException("Product is no longer available: " + product.getName());
            }
            if (product.getStock() < item.getQuantity()) {
                throw new BadRequestException("Insufficient stock for " + product.getName() +
                        ". Available: " + product.getStock() + ", requested: " + item.getQuantity());
            }
        }

        // Step 5: Calculate total price SERVER-SIDE from DB product prices (BigDecimal)
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            Product product = productMap.get(item.getProductId());
            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalPrice = totalPrice.add(itemTotal);
        }
        totalPrice = totalPrice.setScale(2, java.math.RoundingMode.HALF_UP);

        // Step 6: Create Order
        String sanitizedPayment = InputSanitizer.sanitize(paymentMethod, 50);
        String sanitizedAddress = InputSanitizer.sanitizeAddress(shippingAddress);

        Order order = Order.builder()
                .userId(userId)
                .totalPrice(totalPrice)
                .status(Order.OrderStatus.PENDING_PAYMENT)  // NOT CONFIRMED — payment not yet verified
                .paymentMethod(sanitizedPayment)
                .shippingAddress(sanitizedAddress)
                .paymentStatus(Order.PaymentStatus.PENDING)  // NOT PAID — even for COD
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ?
                        InputSanitizer.sanitize(idempotencyKey, 255) : null)
                .build();
        order = orderRepository.save(order);

        // Step 7: Create OrderItems with price snapshot
        for (CartItem item : cartItems) {
            Product product = productMap.get(item.getProductId());
            OrderItem orderItem = OrderItem.builder()
                    .orderId(order.getId())
                    .productId(item.getProductId())
                    .productName(product.getName())
                    .quantity(item.getQuantity())
                    .price(product.getPrice())       // Legacy field — backward compat
                    .unitPrice(product.getPrice())   // Authoritative snapshot
                    .build();
            orderItemRepository.save(orderItem);
        }

        // Step 8: Atomic stock decrement (concurrency-safe)
        for (CartItem item : cartItems) {
            int affectedRows = productRepository.decrementStock(item.getProductId(), item.getQuantity());
            if (affectedRows == 0) {
                // Stock was modified between validation and decrement — abort
                throw new BadRequestException("Insufficient stock for product ID " + item.getProductId() +
                        ". Stock may have changed. Please try again.");
            }
            // Step 9: Auto-set OUT_OF_STOCK if stock becomes 0
            productRepository.markOutOfStockIfZero(item.getProductId());
        }

        // Step 10: Clear cart
        cartItemRepository.deleteByUserId(userId);

        log.info("Order created: id={}, userId={}, total={}, items={}", order.getId(), userId, totalPrice, cartItems.size());

        // Step 11: Return response (order already has idempotencyKey set)
        return mapToOrderResponse(order);
    }

    // ================================================================
    // ORDER — Read Operations
    // ================================================================

    /**
     * Get a single order by ID. Enforces ownership (IDOR protection).
     */
    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return mapToOrderResponse(order);
    }

    /**
     * Get all orders for a user (newest first).
     */
    public List<OrderResponse> getOrders(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orders.stream().map(this::mapToOrderResponse).collect(Collectors.toList());
    }

    /**
     * Get orders for a user (paginated, newest first).
     */
    public Page<OrderResponse> getOrders(Long userId, Pageable pageable) {
        Page<Order> orderPage = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return orderPage.map(this::mapToOrderResponse);
    }

    // ================================================================
    // ORDER — Status Transitions
    // ================================================================

    /**
     * Update order status. Enforces:
     * 1. Ownership: user can only update their own orders
     * 2. Legal transition: must follow the OrderStatus lifecycle
     * 3. Stock restoration: CANCELLED orders restore stock atomically
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long userId, Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Validate the transition is legal
        order.getStatus().validateTransitionTo(newStatus);

        // If cancelling, restore stock atomically
        if (newStatus == Order.OrderStatus.CANCELLED) {
            restoreStockForOrder(order);
        }

        order.setStatus(newStatus);
        order = orderRepository.save(order);

        log.info("Order status updated: id={}, {} → {}", orderId, order.getStatus(), newStatus);
        return mapToOrderResponse(order);
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Validate that a product status transition is allowed.
     * Sellers can: DRAFT→ACTIVE, ACTIVE→INACTIVE, INACTIVE→ACTIVE, OUT_OF_STOCK→ACTIVE (if stock>0)
     * System auto-sets: ACTIVE→OUT_OF_STOCK (when stock=0)
     */
    private void validateStatusTransition(ProductStatus current, ProductStatus target) {
        Set<ProductStatus> allowed;
        switch (current) {
            case DRAFT:
                allowed = Set.of(ProductStatus.ACTIVE, ProductStatus.ARCHIVED);
                break;
            case ACTIVE:
                allowed = Set.of(ProductStatus.INACTIVE, ProductStatus.OUT_OF_STOCK, ProductStatus.ARCHIVED);
                break;
            case INACTIVE:
                allowed = Set.of(ProductStatus.ACTIVE, ProductStatus.ARCHIVED);
                break;
            case OUT_OF_STOCK:
                // Can only reactivate if stock is > 0 (stock check happens in updateProduct)
                allowed = Set.of(ProductStatus.ACTIVE, ProductStatus.INACTIVE, ProductStatus.ARCHIVED);
                break;
            case ARCHIVED:
                allowed = Set.of(); // Terminal state
                break;
            default:
                allowed = Set.of();
        }
        if (!allowed.contains(target)) {
            throw new BadRequestException("Cannot change product status from " + current + " to " + target);
        }
    }

    /**
     * Restore stock for all items in an order when it is cancelled.
     * Called within the same transaction as the status update.
     */
    private void restoreStockForOrder(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            productRepository.restoreStock(item.getProductId(), item.getQuantity());
            log.info("Stock restored for product {}: +{}", item.getProductId(), item.getQuantity());
        }
    }

    private ProductResponse mapToProductResponse(Product product) {
        // Parse features JSON array to List<String>
        List<String> featureList = null;
        if (product.getFeatures() != null && !product.getFeatures().isBlank()) {
            try {
                String feat = product.getFeatures().trim();
                if (feat.startsWith("[") && feat.endsWith("]")) {
                    feat = feat.substring(1, feat.length() - 1);
                    featureList = new java.util.ArrayList<>();
                    for (String f : feat.split("\",\"")) {
                        f = f.replace("\"", "").trim();
                        if (!f.isEmpty()) featureList.add(f);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse features for product {}", product.getId());
            }
        }

        // Auto-calculate discount if mrp is set but discount is not
        Integer discount = product.getDiscountPercent();
        if (discount == null && product.getMrp() != null && product.getPrice() != null
                && product.getMrp().compareTo(product.getPrice()) > 0) {
            discount = product.getMrp().subtract(product.getPrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(product.getMrp(), 0, java.math.RoundingMode.HALF_UP).intValue();
        }

        return ProductResponse.builder()
                .id(product.getId())
                .sellerId(product.getSellerId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .ecoRating(product.getEcoRating())
                .isSecondhand(product.getIsSecondhand())
                .isAvailable(product.getIsAvailable())
                .stock(product.getStock())
                .status(product.getStatus())
                .version(product.getVersion())
                .createdAt(product.getCreatedAt())
                .brand(product.getBrand())
                .mrp(product.getMrp())
                .discountPercent(discount)
                .features(featureList)
                .highlights(product.getHighlights())
                .tags(product.getTags())
                .rating(product.getRating())
                .ratingCount(product.getRatingCount())
                .deliveryDays(product.getDeliveryDays())
                .weightGrams(product.getWeightGrams())
                .build();
    }

    private CartItemResponse mapToCartItemResponse(CartItem item, Product product) {
        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(product != null ? product.getName() : "Unknown Product")
                .price(product != null ? product.getPrice() : BigDecimal.ZERO)
                .quantity(item.getQuantity())
                .imageUrl(product != null ? product.getImageUrl() : null)
                .stock(product != null ? product.getStock() : 0)
                .productStatus(product != null ? product.getStatus() : null)
                .build();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = orderItems.stream()
                .map(oi -> OrderItemResponse.builder()
                        .id(oi.getId())
                        .productId(oi.getProductId())
                        .productName(oi.getProductName())
                        .quantity(oi.getQuantity())
                        .price(oi.getPrice())         // Legacy
                        .unitPrice(oi.getUnitPrice()) // Authoritative snapshot
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus() != null ? order.getStatus().name() : "PENDING_PAYMENT")
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "PENDING")
                .paymentMethod(order.getPaymentMethod())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
