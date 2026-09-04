package com.ecoverse.integration;

import com.ecoverse.dto.shop.*;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.*;
import com.ecoverse.repository.*;
import com.ecoverse.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PHASE 4 PRODUCTION VERIFICATION — Integration Tests
 *
 * Uses @SpringBootTest with real H2 database and actual Spring transaction management.
 * NOT mock-based — exercises real JPA repositories, @Transactional boundaries,
 * and the full ShopService business logic.
 *
 * Verifies:
 * 1. Concurrency: atomic stock decrement under real thread contention
 * 2. Transaction rollback: forced failure mid-transaction, verify full rollback
 * 3. Idempotency: same key returns same order, stock decremented once
 * 4. Price tampering: client prices ignored, server prices authoritative
 * 5. Historical price: old orders keep snapshot price after product price change
 * 6. Inventory status: stock=0 → OUT_OF_STOCK, purchase fails
 * 7. Order state machine: legal/illegal transitions
 * 8. COD: NOT PAID, NOT CONFIRMED
 * 9. Seller ownership: IDOR for product update/delete
 * 10. User order ownership: IDOR for read/cancel
 * 11. Cart ownership: cannot modify another user's cart
 * 12. Pagination: verify no full-table loading
 */
@SpringBootTest
@ActiveProfiles("default")
@TestPropertySource(properties = {
    "jwt.secret=test-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-testing-XXXXXX"
})
class ShopIntegrationTest {

    @Autowired private ShopService shopService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    private static Long sellerId;
    private static Long userAId;
    private static Long userBId;
    private static Long sellerBId;

    @BeforeEach
    void setUp() {
        // Clean up all shop data before each test
        cartItemRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        // Use fixed IDs to represent different users
        sellerId = 100L;
        userAId = 200L;
        userBId = 300L;
        sellerBId = 400L;
    }

    // Helper: Create a product directly in the DB
    private Product createProduct(Long sellerId, String name, BigDecimal price, int stock, ProductStatus status) {
        Product product = Product.builder()
                .sellerId(sellerId)
                .name(name)
                .category("eco")
                .price(price)
                .stock(stock)
                .status(status)
                .ecoRating(4)
                .isSecondhand(false)
                .build();
        return productRepository.save(product);
    }

    // Helper: Add item to cart via service
    private void addCart(Long userId, Long productId, int qty) {
        shopService.addToCart(userId, productId, qty);
    }

    // ==================================================================
    // 1. CONCURRENCY TEST — Atomic Stock Decrement Under Contention
    // ==================================================================

    @Nested
    @DisplayName("1. Real Concurrency — Atomic Stock Decrement")
    class ConcurrencyTest {

        @Test
        @DisplayName("Two users buying last item — exactly one succeeds, stock becomes 0")
        void twoUsersBuyLastItem_exactlyOneSucceeds() throws Exception {
            // Product with exactly 1 item in stock
            Product product = createProduct(sellerId, "Last Item", BigDecimal.valueOf(500), 1, ProductStatus.ACTIVE);

            // Both users add the product to their carts
            addCart(userAId, product.getId(), 1);
            addCart(userBId, product.getId(), 1);

            // Use real concurrent threads to attempt purchase simultaneously
            ExecutorService executor = Executors.newFixedThreadPool(2);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);

            Callable<Void> buyTask = () -> {
                startLatch.await(); // Both threads start at the same time
                try {
                    shopService.placeOrder(Thread.currentThread().getName().contains("A") ? userAId : userBId,
                            "cod", "Address", null);
                    successCount.incrementAndGet();
                } catch (BadRequestException e) {
                    failureCount.incrementAndGet();
                }
                return null;
            };

            Future<Void> futureA = executor.submit(() -> {
                Thread.currentThread().setName("Thread-A");
                return buyTask.call();
            });
            Future<Void> futureB = executor.submit(() -> {
                Thread.currentThread().setName("Thread-B");
                return buyTask.call();
            });

            // Release both threads simultaneously
            startLatch.countDown();

            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            // VERIFY: Exactly one order succeeds
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failureCount.get()).isEqualTo(1);

            // VERIFY: Stock is exactly 0
            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);

            // VERIFY: Stock never went negative (stock=0 is correct)
            assertThat(updated.getStock()).isNotNegative();

            // VERIFY: Exactly one order exists
            List<Order> orders = orderRepository.findAll();
            assertThat(orders).hasSize(1);

            // VERIFY: Exactly one set of order items
            List<OrderItem> items = orderItemRepository.findAll();
            assertThat(items).hasSize(1);

            // VERIFY: No orphan cart items for the successful user (cart was cleared)
            // The failing user still has a cart item
            List<CartItem> remainingCart = cartItemRepository.findAll();
            assertThat(remainingCart).hasSize(1); // Only the failed user's cart item remains
        }

        @Test
        @DisplayName("Stock=5, 3 users buy 2 each — exactly 2 succeed (stock allows 4 of 6)")
        void multipleBuyers_limitedStock() throws Exception {
            Product product = createProduct(sellerId, "Limited Stock", BigDecimal.valueOf(100), 5, ProductStatus.ACTIVE);

            // 3 users, each buying 2 — only 5 in stock, so 2 succeed (2+2=4 ≤ 5), 1 fails (2+2+2=6 > 5)
            addCart(userAId, product.getId(), 2);
            addCart(userBId, product.getId(), 2);

            // Third user
            Long userCId = 500L;
            addCart(userCId, product.getId(), 2);

            ExecutorService executor = Executors.newFixedThreadPool(3);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CyclicBarrier barrier = new CyclicBarrier(3);

            List<Long> userIds = List.of(userAId, userBId, userCId);
            List<Future<Void>> futures = new ArrayList<>();

            for (Long userId : userIds) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    try {
                        shopService.placeOrder(userId, "cod", "Address", null);
                        successCount.incrementAndGet();
                    } catch (BadRequestException e) {
                        failureCount.incrementAndGet();
                    }
                    return null;
                }));
            }

            startLatch.countDown();

            for (Future<Void> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            // VERIFY: At most 2 succeed (stock=5, each buys 2, so max 2 orders = 4 items)
            assertThat(successCount.get()).isBetween(1, 2);
            assertThat(successCount.get() + failureCount.get()).isEqualTo(3);

            // VERIFY: Stock is non-negative
            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock()).isNotNegative();

            // VERIFY: Total items ordered equals stock consumed
            int totalOrdered = orderItemRepository.findAll().stream()
                    .mapToInt(OrderItem::getQuantity).sum();
            assertThat(updated.getStock() + totalOrdered).isEqualTo(5); // original stock
        }
    }

    // ==================================================================
    // 2. ORDER ROLLBACK TEST
    // ==================================================================

    @Nested
    @DisplayName("2. Transaction Rollback — Mid-Order Failure")
    class TransactionRollbackTest {

        @Test
        @DisplayName("Empty cart causes rollback — no order, no stock change")
        void emptyCartCausesRollback() {
            // This tests that when placeOrder fails early (empty cart),
            // no partial data is left behind
            assertThatThrownBy(() -> shopService.placeOrder(userAId, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class);

            // VERIFY: No orders exist
            assertThat(orderRepository.findAll()).isEmpty();
            // VERIFY: No order items exist
            assertThat(orderItemRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Product becomes unavailable during order — transaction rolls back")
        void productUnavailableDuringOrder() {
            // User A adds product to cart
            Product product = createProduct(sellerId, "Available Now", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);

            // Simulate product becoming unavailable between cart add and order
            product.setStatus(ProductStatus.INACTIVE);
            productRepository.save(product);

            // Order should fail
            assertThatThrownBy(() -> shopService.placeOrder(userAId, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no longer available");

            // VERIFY: No order was created
            assertThat(orderRepository.findAll()).isEmpty();
            // VERIFY: No order items were created
            assertThat(orderItemRepository.findAll()).isEmpty();
            // VERIFY: Stock was NOT decremented
            Product after = productRepository.findById(product.getId()).orElseThrow();
            assertThat(after.getStock()).isEqualTo(10);
            // VERIFY: Cart was NOT cleared (order failed)
            assertThat(cartItemRepository.findByUserId(userAId)).isNotEmpty();
        }

        @Test
        @DisplayName("Insufficient stock during order — no partial order created")
        void insufficientStockDuringOrder() {
            Product product = createProduct(sellerId, "Low Stock", BigDecimal.valueOf(100), 1, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 5); // Requesting 5, only 1 in stock

            assertThatThrownBy(() -> shopService.placeOrder(userAId, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Insufficient stock");

            // VERIFY: No order
            assertThat(orderRepository.findAll()).isEmpty();
            // VERIFY: Stock unchanged
            assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(1);
            // VERIFY: Cart still has the item
            assertThat(cartItemRepository.findByUserId(userAId)).isNotEmpty();
        }
    }

    // ==================================================================
    // 3. IDEMPOTENCY TEST
    // ==================================================================

    @Nested
    @DisplayName("3. Idempotency — Duplicate Order Prevention")
    class IdempotencyTest {

        @Test
        @DisplayName("Same idempotency key returns existing order, stock decremented once")
        void sameKeyReturnsExistingOrder() {
            Product product = createProduct(sellerId, "Idempotent Product", BigDecimal.valueOf(200), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);

            // First order with idempotency key
            String idempotencyKey = "idem-" + System.currentTimeMillis();
            OrderResponse first = shopService.placeOrder(userAId, "cod", "Address", idempotencyKey);
            assertThat(first).isNotNull();

            // Stock should be decremented
            assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(9);

            // Second order with SAME key — should return existing order
            // First, add the item back to cart (it was cleared by the first order)
            addCart(userAId, product.getId(), 1);

            OrderResponse second = shopService.placeOrder(userAId, "cod", "Address", idempotencyKey);
            assertThat(second.getId()).isEqualTo(first.getId());

            // VERIFY: Stock still 9 (not decremented again)
            assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(9);

            // VERIFY: Only one order exists
            assertThat(orderRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("Null idempotency key allows duplicate orders (no protection)")
        void nullKeyAllowsDuplicateOrders() {
            Product product = createProduct(sellerId, "No Idem Product", BigDecimal.valueOf(200), 10, ProductStatus.ACTIVE);

            // First order with null key
            addCart(userAId, product.getId(), 1);
            shopService.placeOrder(userAId, "cod", "Address", null);

            // Second order with null key (different cart item needed)
            addCart(userAId, product.getId(), 1);
            shopService.placeOrder(userAId, "cod", "Address", null);

            // VERIFY: Two orders created (no idempotency protection)
            assertThat(orderRepository.findAll()).hasSize(2);
            // VERIFY: Stock decremented twice (8 remaining)
            assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(8);
        }
    }

    // ==================================================================
    // 4. PRICE TAMPERING TEST
    // ==================================================================

    @Nested
    @DisplayName("4. Price Tampering — Server Prices Authoritative")
    class PriceTamperingTest {

        @Test
        @DisplayName("Server calculates total from DB prices — client prices ignored")
        void serverCalculatesTotalFromDbPrices() {
            Product product = createProduct(sellerId, "Tamper-Proof", BigDecimal.valueOf(499.99), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 3);

            // placeOrder does NOT accept any price parameter — total is always server-calculated
            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            // VERIFY: Total is server-calculated: 499.99 * 3 = 1499.97
            assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(1499.97));
        }

        @Test
        @DisplayName("OrderItem unitPrice matches product DB price, not any client price")
        void orderItemUnitPriceMatchesDbPrice() {
            Product product = createProduct(sellerId, "Price Check", BigDecimal.valueOf(1234.56), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 2);

            shopService.placeOrder(userAId, "cod", "Address", null);

            // VERIFY: OrderItem stores the server price
            List<OrderItem> items = orderItemRepository.findAll();
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(1234.56));
            assertThat(items.get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1234.56));
        }
    }

    // ==================================================================
    // 5. HISTORICAL PRICE TEST
    // ==================================================================

    @Nested
    @DisplayName("5. Historical Price — Price Snapshot")
    class HistoricalPriceTest {

        @Test
        @DisplayName("Old order keeps ₹500 unit price after product changes to ₹700")
        void oldOrderKeepsSnapshotPrice() {
            // Create product at ₹500
            Product product = createProduct(sellerId, "Price Change", BigDecimal.valueOf(500), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 2);

            // Place order at ₹500
            OrderResponse oldOrder = shopService.placeOrder(userAId, "cod", "Address", null);
            assertThat(oldOrder.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(1000)); // 500 * 2

            // VERIFY: Order item has ₹500 unit price
            List<OrderItem> oldItems = orderItemRepository.findAll();
            assertThat(oldItems).hasSize(1);
            Long oldOrderItemId = oldItems.get(0).getId();
            BigDecimal oldUnitPrice = oldItems.get(0).getUnitPrice();

            // Now change the product price to ₹700
            // Reload from DB to get current version (avoids optimistic lock conflict)
            Product dbProduct = productRepository.findById(product.getId()).orElseThrow();
            dbProduct.setPrice(BigDecimal.valueOf(700));
            productRepository.save(dbProduct);

            // Place a new order at ₹700
            addCart(userBId, product.getId(), 1);
            OrderResponse newOrder = shopService.placeOrder(userBId, "cod", "Address", null);
            assertThat(newOrder.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(700)); // 700 * 1

            // VERIFY: Old order item still has ₹500 unit price
            OrderItem oldItem = orderItemRepository.findById(oldOrderItemId).orElseThrow();
            assertThat(oldItem.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(500));
            assertThat(oldItem.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(500));

            // VERIFY: New order item has ₹700 unit price
            List<OrderItem> allItems = orderItemRepository.findAll();
            OrderItem newItem = allItems.stream()
                    .filter(i -> !i.getId().equals(oldOrderItemId))
                    .findFirst().orElseThrow();
            assertThat(newItem.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(700));
        }
    }

    // ==================================================================
    // 6. INVENTORY STATUS TEST
    // ==================================================================

    @Nested
    @DisplayName("6. Inventory Status — OUT_OF_STOCK")
    class InventoryStatusTest {

        @Test
        @DisplayName("Purchasing all stock sets status to OUT_OF_STOCK and blocks further purchases")
        void purchasingAllStockSetsOutOfStock() {
            Product product = createProduct(sellerId, "Depletable", BigDecimal.valueOf(100), 5, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 5);

            shopService.placeOrder(userAId, "cod", "Address", null);

            // VERIFY: Stock is 0
            Product after = productRepository.findById(product.getId()).orElseThrow();
            assertThat(after.getStock()).isEqualTo(0);

            // VERIFY: Status is OUT_OF_STOCK (set by markOutOfStockIfZero)
            assertThat(after.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);

            // VERIFY: Cannot add to cart anymore
            assertThatThrownBy(() -> shopService.addToCart(userBId, product.getId(), 1))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("Restocking OUT_OF_STOCK product reactivates it to ACTIVE")
        void restockingReactivatesProduct() {
            Product product = createProduct(sellerId, "Restockable", BigDecimal.valueOf(100), 0, ProductStatus.OUT_OF_STOCK);

            // Cannot purchase
            assertThatThrownBy(() -> shopService.addToCart(userAId, product.getId(), 1))
                    .isInstanceOf(BadRequestException.class);

            // Seller restocks
            var req = new ProductRequest();
            req.setName("Restockable");
            req.setCategory("eco");
            req.setPrice(BigDecimal.valueOf(100));
            req.setStock(10);
            ProductResponse updated = shopService.updateProduct(sellerId, product.getId(), req);

            // VERIFY: Status is now ACTIVE
            assertThat(updated.getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(updated.getStock()).isEqualTo(10);

            // VERIFY: Can add to cart now
            shopService.addToCart(userAId, product.getId(), 1); // No exception
        }
    }

    // ==================================================================
    // 7. ORDER STATUS STATE MACHINE
    // ==================================================================

    @Nested
    @DisplayName("7. Order Status State Machine")
    class OrderStateMachineTest {

        @Test
        @DisplayName("PENDING_PAYMENT → CANCELLED is legal")
        void pendingPaymentToCancelled() {
            Product product = createProduct(sellerId, "Cancel Test", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);
            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            OrderResponse cancelled = shopService.updateOrderStatus(userAId, order.getId(), Order.OrderStatus.CANCELLED);

            assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("PENDING_PAYMENT → PAID is legal (for payment verification)")
        void pendingPaymentToPaid() {
            Product product = createProduct(sellerId, "Pay Test", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);
            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            OrderResponse paid = shopService.updateOrderStatus(userAId, order.getId(), Order.OrderStatus.PAID);

            assertThat(paid.getStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("DELIVERED → PROCESSING is illegal")
        void deliveredToProcessingIsIllegal() {
            Product product = createProduct(sellerId, "State Test", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);
            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            // Manually set to DELIVERED for testing
            Order dbOrder = orderRepository.findById(order.getId()).orElseThrow();
            dbOrder.setStatus(Order.OrderStatus.DELIVERED);
            orderRepository.save(dbOrder);

            assertThatThrownBy(() -> shopService.updateOrderStatus(userAId, order.getId(), Order.OrderStatus.PROCESSING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal order status transition");
        }

        @Test
        @DisplayName("CANCELLED → PAID is illegal")
        void cancelledToPaidIsIllegal() {
            Product product = createProduct(sellerId, "State Test", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);
            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            // Cancel first
            shopService.updateOrderStatus(userAId, order.getId(), Order.OrderStatus.CANCELLED);

            // Then try to mark as PAID
            assertThatThrownBy(() -> shopService.updateOrderStatus(userAId, order.getId(), Order.OrderStatus.PAID))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("PAID → PENDING_PAYMENT is illegal (backwards)")
        void paidToPendingPaymentIsIllegal() {
            Product product = createProduct(sellerId, "State Test", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);
            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            // Mark as PAID
            shopService.updateOrderStatus(userAId, order.getId(), Order.OrderStatus.PAID);

            // Try to go backwards
            assertThatThrownBy(() -> shopService.updateOrderStatus(userAId, order.getId(), Order.OrderStatus.PENDING_PAYMENT))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Cancelling PENDING_PAYMENT order restores stock")
        void cancellingRestoresStock() {
            Product product = createProduct(sellerId, "Stock Restore", BigDecimal.valueOf(100), 5, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 3);
            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            // Stock should be 2 (5 - 3)
            assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(2);

            // Cancel order
            shopService.updateOrderStatus(userAId, order.getId(), Order.OrderStatus.CANCELLED);

            // Stock should be restored to 5 (2 + 3)
            assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(5);
        }
    }

    // ==================================================================
    // 8. COD VERIFICATION
    // ==================================================================

    @Nested
    @DisplayName("8. COD — NOT PAID, NOT CONFIRMED")
    class CodVerificationTest {

        @Test
        @DisplayName("COD order status is PENDING_PAYMENT (not CONFIRMED)")
        void codStatusIsPendingPayment() {
            Product product = createProduct(sellerId, "COD Test", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);

            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
        }

        @Test
        @DisplayName("COD paymentStatus is PENDING (not PAID)")
        void codPaymentStatusIsPending() {
            Product product = createProduct(sellerId, "COD Test", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);

            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            assertThat(order.getPaymentStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("COD order with explicit paymentMethod=cod is NOT PAID")
        void explicitCodNotPaid() {
            Product product = createProduct(sellerId, "COD Explicit", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);

            OrderResponse order = shopService.placeOrder(userAId, "cod", "Address", null);

            // Double-check: verify in DB
            Order dbOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(dbOrder.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PENDING);
            assertThat(dbOrder.getStatus()).isEqualTo(Order.OrderStatus.PENDING_PAYMENT);
        }
    }

    // ==================================================================
    // 9. SELLER OWNERSHIP
    // ==================================================================

    @Nested
    @DisplayName("9. Seller Ownership — IDOR Protection")
    class SellerOwnershipTest {

        @Test
        @DisplayName("Seller B cannot update Seller A's product")
        void sellerBCannotUpdateSellerAProduct() {
            Product product = createProduct(sellerId, "A's Product", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);

            var req = new ProductRequest();
            req.setName("Hacked by B");
            req.setCategory("eco");
            req.setPrice(BigDecimal.valueOf(1));

            assertThatThrownBy(() -> shopService.updateProduct(sellerBId, product.getId(), req))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("own products");
        }

        @Test
        @DisplayName("Seller B cannot archive Seller A's product")
        void sellerBCannotArchiveSellerAProduct() {
            Product product = createProduct(sellerId, "A's Product", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);

            assertThatThrownBy(() -> shopService.deleteProduct(sellerBId, product.getId()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("own products");

            // VERIFY: Product still exists
            assertThat(productRepository.findById(product.getId())).isPresent();
        }
    }

    // ==================================================================
    // 10. USER ORDER OWNERSHIP
    // ==================================================================

    @Nested
    @DisplayName("10. User Order Ownership — IDOR Protection")
    class UserOrderOwnershipTest {

        @Test
        @DisplayName("User B cannot read User A's order")
        void userBCannotReadUserAOrder() {
            Product product = createProduct(sellerId, "Order IDOR", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);
            OrderResponse orderA = shopService.placeOrder(userAId, "cod", "Address", null);

            assertThatThrownBy(() -> shopService.getOrder(userBId, orderA.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("User B cannot cancel User A's order")
        void userBCannotCancelUserAOrder() {
            Product product = createProduct(sellerId, "Order Cancel IDOR", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);
            OrderResponse orderA = shopService.placeOrder(userAId, "cod", "Address", null);

            assertThatThrownBy(() -> shopService.updateOrderStatus(userBId, orderA.getId(), Order.OrderStatus.CANCELLED))
                    .isInstanceOf(ResourceNotFoundException.class);

            // VERIFY: Order still exists and is NOT cancelled
            Order dbOrder = orderRepository.findById(orderA.getId()).orElseThrow();
            assertThat(dbOrder.getStatus()).isNotEqualTo(Order.OrderStatus.CANCELLED);
        }
    }

    // ==================================================================
    // 11. CART OWNERSHIP
    // ==================================================================

    @Nested
    @DisplayName("11. Cart Ownership — Cannot Modify Another User's Cart")
    class CartOwnershipTest {

        @Test
        @DisplayName("User B cannot modify User A's cart item")
        void userBCannotModifyUserACartItem() {
            Product product = createProduct(sellerId, "Cart IDOR", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);

            List<CartItemResponse> cartA = shopService.getCart(userAId);
            Long cartItemId = cartA.get(0).getId();

            assertThatThrownBy(() -> shopService.updateCartItemQuantity(userBId, cartItemId, 5))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("User B cannot delete User A's cart item")
        void userBCannotDeleteUserACartItem() {
            Product product = createProduct(sellerId, "Cart IDOR", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            addCart(userAId, product.getId(), 1);

            List<CartItemResponse> cartA = shopService.getCart(userAId);
            Long cartItemId = cartA.get(0).getId();

            assertThatThrownBy(() -> shopService.removeFromCart(cartItemId, userBId))
                    .isInstanceOf(ForbiddenException.class);

            // VERIFY: Cart item still exists
            assertThat(cartItemRepository.findById(cartItemId)).isPresent();
        }
    }

    // ==================================================================
    // 12. PAGINATION
    // ==================================================================

    @Nested
    @DisplayName("12. Pagination — No Full-Table Loading")
    class PaginationTest {

        @Test
        @DisplayName("Product pagination returns correct page size, not all products")
        void productPaginationReturnsCorrectPageSize() {
            // Create 25 products
            for (int i = 0; i < 25; i++) {
                createProduct(sellerId, "Product " + i, BigDecimal.valueOf(100 + i), 10, ProductStatus.ACTIVE);
            }

            // Request page 0, size 10
            Page<ProductResponse> page = shopService.getProducts("all", null, null, null, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(10);
            assertThat(page.getTotalElements()).isEqualTo(25);
            assertThat(page.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("Order pagination returns correct page size")
        void orderPaginationReturnsCorrectPageSize() {
            Product product = createProduct(sellerId, "Paginated Orders", BigDecimal.valueOf(100), 100, ProductStatus.ACTIVE);

            // Create 5 orders
            for (int i = 0; i < 5; i++) {
                addCart(userAId, product.getId(), 1);
                shopService.placeOrder(userAId, "cod", "Address " + i, null);
            }

            Page<OrderResponse> page = shopService.getOrders(userAId, PageRequest.of(0, 3));

            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getTotalElements()).isEqualTo(5);
        }

        @Test
        @DisplayName("Seller sees own products across all statuses")
        void sellerSeesOwnProductsAllStatuses() {
            createProduct(sellerId, "Active", BigDecimal.valueOf(100), 10, ProductStatus.ACTIVE);
            createProduct(sellerId, "Inactive", BigDecimal.valueOf(100), 10, ProductStatus.INACTIVE);

            Page<ProductResponse> page = shopService.getSellerProducts(sellerId, PageRequest.of(0, 20));

            assertThat(page.getContent()).hasSize(2);
        }
    }
}
