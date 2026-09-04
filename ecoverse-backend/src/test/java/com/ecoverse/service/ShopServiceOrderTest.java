package com.ecoverse.service;

import com.ecoverse.dto.shop.OrderResponse;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.*;
import com.ecoverse.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ShopService order creation — the critical transactional path.
 *
 * Verifies:
 * - Server-side price calculation (frontend prices NEVER trusted)
 * - Stock validation and decrement
 * - Price snapshot (unitPrice stored at purchase time)
 * - Idempotency (duplicate key returns existing order)
 * - COD is NOT falsely marked as CONFIRMED/PAID
 * - Order ownership (IDOR protection)
 * - Empty cart rejection
 * - Product availability validation
 * - Insufficient stock rejection
 * - Cart clearing on successful order
 * - Order status transition enforcement
 */
@ExtendWith(MockitoExtension.class)
class ShopServiceOrderTest {

    @Mock private ProductRepository productRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;

    private ShopService shopService;

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;

    @BeforeEach
    void setUp() {
        shopService = new ShopService();
        injectField(shopService, "productRepository", productRepository);
        injectField(shopService, "cartItemRepository", cartItemRepository);
        injectField(shopService, "orderRepository", orderRepository);
        injectField(shopService, "orderItemRepository", orderItemRepository);
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }

    // Helper: Create a product with defaults
    private Product createProduct(Long id, String name, BigDecimal price, int stock, ProductStatus status) {
        return Product.builder()
                .id(id).name(name).price(price).stock(stock).status(status)
                .sellerId(1L).category("eco").build();
    }

    // Helper: Create a cart item
    private CartItem createCartItem(Long id, Long productId, int quantity) {
        return CartItem.builder().id(id).userId(USER_ID).productId(productId).quantity(quantity).build();
    }

    // Helper: Setup a successful order placement
    private void setupSuccessfulOrder(Product product, CartItem cartItem) {
        when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cartItem));
        when(productRepository.findAllByIdIn(List.of(cartItem.getProductId()))).thenReturn(List.of(product));
        when(productRepository.decrementStock(eq(product.getId()), eq(cartItem.getQuantity()))).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(1L);
            return order;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================================================================
    // ORDER CREATION — HAPPY PATH
    // ==================================================================

    @Nested
    @DisplayName("Order Creation — Happy Path")
    class OrderCreationHappyPath {

        @Test
        @DisplayName("Successful order: stock decremented, cart cleared, prices calculated")
        void successfulOrder() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 2);
            setupSuccessfulOrder(product, cartItem);

            OrderResponse response = shopService.placeOrder(USER_ID, "cod", "123 Main St", null);

            assertThat(response).isNotNull();
            assertThat(response.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(599.98));
            assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");

            // Verify stock was decremented
            verify(productRepository).decrementStock(10L, 2);
            // Verify cart was cleared
            verify(cartItemRepository).deleteByUserId(USER_ID);
        }

        @Test
        @DisplayName("Order status is PENDING_PAYMENT (not CONFIRMED)")
        void orderStatusIsPendingPayment() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 1);
            setupSuccessfulOrder(product, cartItem);

            OrderResponse response = shopService.placeOrder(USER_ID, "cod", "123 Main St", null);

            assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");
        }

        @Test
        @DisplayName("COD order: paymentStatus is PENDING (NOT PAID)")
        void codPaymentStatusIsPending() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 1);
            setupSuccessfulOrder(product, cartItem);

            OrderResponse response = shopService.placeOrder(USER_ID, "cod", "123 Main St", null);

            assertThat(response.getPaymentStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Server calculates total price (BigDecimal, no floating-point)")
        void serverCalculatesTotalPrice() {
            Product p1 = createProduct(10L, "Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.ACTIVE);
            Product p2 = createProduct(11L, "Bag", BigDecimal.valueOf(449.50), 30, ProductStatus.ACTIVE);
            CartItem c1 = createCartItem(1L, 10L, 2);
            CartItem c2 = createCartItem(2L, 11L, 1);

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(c1, c2));
            when(productRepository.findAllByIdIn(List.of(10L, 11L))).thenReturn(List.of(p1, p2));
            when(productRepository.decrementStock(anyLong(), anyInt())).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(1L);
                return order;
            });
            when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = shopService.placeOrder(USER_ID, "cod", "Address", null);

            // 299.99 * 2 + 449.50 * 1 = 1049.48
            assertThat(response.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(1049.48));
        }
    }

    // ==================================================================
    // PRICE SNAPSHOT
    // ==================================================================

    @Nested
    @DisplayName("Price Snapshot — unitPrice stored at purchase time")
    class PriceSnapshot {

        @Test
        @DisplayName("OrderItem stores unitPrice from product price at purchase time")
        void orderItemStoresUnitPrice() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 3);
            setupSuccessfulOrder(product, cartItem);

            shopService.placeOrder(USER_ID, "cod", "Address", null);

            // Verify order item was saved with the correct unitPrice
            verify(orderItemRepository).save(argThat(item ->
                    item.getUnitPrice().compareTo(BigDecimal.valueOf(299.99)) == 0 &&
                    item.getProductId().equals(10L) &&
                    item.getQuantity() == 3
            ));
        }

        @Test
        @DisplayName("OrderItem stores both price and unitPrice (backward compat)")
        void orderItemStoresBothPrices() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 1);
            setupSuccessfulOrder(product, cartItem);

            shopService.placeOrder(USER_ID, "cod", "Address", null);

            verify(orderItemRepository).save(argThat(item ->
                    item.getPrice().compareTo(item.getUnitPrice()) == 0
            ));
        }
    }

    // ==================================================================
    // STOCK VALIDATION
    // ==================================================================

    @Nested
    @DisplayName("Stock Validation")
    class StockValidation {

        @Test
        @DisplayName("Insufficient stock rejects order")
        void insufficientStockRejectsOrder() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 1, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 5); // Requesting 5, only 1 in stock

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cartItem));
            when(productRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(product));

            assertThatThrownBy(() -> shopService.placeOrder(USER_ID, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Insufficient stock");

            // Order must NOT be saved
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Zero stock product rejects order")
        void zeroStockRejectsOrder() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 0, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 1);

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cartItem));
            when(productRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(product));

            assertThatThrownBy(() -> shopService.placeOrder(USER_ID, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Insufficient stock");
        }

        @Test
        @DisplayName("Atomic stock decrement failure rejects order")
        void atomicDecrementFailureRejectsOrder() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 2);

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cartItem));
            when(productRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(product));
            when(productRepository.decrementStock(10L, 2)).thenReturn(0); // Stock changed between check and decrement!
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(1L);
                return order;
            });
            when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> shopService.placeOrder(USER_ID, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Insufficient stock");
        }
    }

    // ==================================================================
    // PRODUCT AVAILABILITY
    // ==================================================================

    @Nested
    @DisplayName("Product Availability")
    class ProductAvailability {

        @Test
        @DisplayName("Product not found rejects order")
        void productNotFoundRejectsOrder() {
            CartItem cartItem = createCartItem(1L, 999L, 1);

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cartItem));
            when(productRepository.findAllByIdIn(List.of(999L))).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> shopService.placeOrder(USER_ID, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Product not found");
        }

        @Test
        @DisplayName("INACTIVE product rejects order")
        void inactiveProductRejectsOrder() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.INACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 1);

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cartItem));
            when(productRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(product));

            assertThatThrownBy(() -> shopService.placeOrder(USER_ID, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no longer available");
        }

        @Test
        @DisplayName("DRAFT product rejects order")
        void draftProductRejectsOrder() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.DRAFT);
            CartItem cartItem = createCartItem(1L, 10L, 1);

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cartItem));
            when(productRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(product));

            assertThatThrownBy(() -> shopService.placeOrder(USER_ID, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no longer available");
        }

        @Test
        @DisplayName("OUT_OF_STOCK product rejects order")
        void outOfStockProductRejectsOrder() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 0, ProductStatus.OUT_OF_STOCK);
            CartItem cartItem = createCartItem(1L, 10L, 1);

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(cartItem));
            when(productRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(product));

            assertThatThrownBy(() -> shopService.placeOrder(USER_ID, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no longer available");
        }

        @Test
        @DisplayName("Empty cart rejects order")
        void emptyCartRejectsOrder() {
            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> shopService.placeOrder(USER_ID, "cod", "Address", null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Cart is empty");
        }
    }

    // ==================================================================
    // IDEMPOTENCY
    // ==================================================================

    @Nested
    @DisplayName("Idempotency — duplicate key prevention")
    class Idempotency {

        @Test
        @DisplayName("Same idempotency key returns existing order")
        void sameIdempotencyKeyReturnsExistingOrder() {
            Order existingOrder = Order.builder()
                    .id(1L).userId(USER_ID).totalPrice(BigDecimal.valueOf(299.99))
                    .status(Order.OrderStatus.PENDING_PAYMENT).paymentMethod("cod")
                    .shippingAddress("Address").paymentStatus(Order.PaymentStatus.PENDING)
                    .idempotencyKey("key-123").build();

            when(orderRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existingOrder));

            OrderResponse response = shopService.placeOrder(USER_ID, "cod", "Address", "key-123");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);

            // No new order should be created
            verify(orderRepository, times(1)).findByIdempotencyKey("key-123");
            verify(orderRepository, never()).save(any());
            verify(cartItemRepository, never()).deleteByUserId(any());
        }

        @Test
        @DisplayName("Null idempotency key bypasses check")
        void nullIdempotencyKeyBypassesCheck() {
            Product product = createProduct(10L, "Eco Bottle", BigDecimal.valueOf(299.99), 50, ProductStatus.ACTIVE);
            CartItem cartItem = createCartItem(1L, 10L, 1);
            setupSuccessfulOrder(product, cartItem);

            OrderResponse response = shopService.placeOrder(USER_ID, "cod", "Address", null);

            assertThat(response).isNotNull();
            // Idempotency key lookup should not be called for null
            verify(orderRepository, never()).findByIdempotencyKey(any());
        }
    }

    // ==================================================================
    // ORDER OWNERSHIP (IDOR)
    // ==================================================================

    @Nested
    @DisplayName("Order Ownership — IDOR Protection")
    class OrderOwnership {

        @Test
        @DisplayName("getOrder with ownership returns order")
        void getOrderWithOwnership() {
            Order order = Order.builder().id(1L).userId(USER_ID).build();
            when(orderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderId(1L)).thenReturn(Collections.emptyList());

            OrderResponse response = shopService.getOrder(USER_ID, 1L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("User cannot read another user's order (IDOR)")
        void userCannotReadAnotherUsersOrder() {
            when(orderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.getOrder(USER_ID, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("updateOrderStatus enforces ownership")
        void updateOrderStatusEnforcesOwnership() {
            when(orderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.updateOrderStatus(USER_ID, 1L, Order.OrderStatus.CANCELLED))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================================================================
    // ORDER STATUS TRANSITIONS
    // ==================================================================

    @Nested
    @DisplayName("Order Status Transitions")
    class OrderStatusTransitions {

        @Test
        @DisplayName("User can cancel PENDING_PAYMENT order")
        void userCanCancelPendingPaymentOrder() {
            Order order = Order.builder().id(1L).userId(USER_ID)
                    .status(Order.OrderStatus.PENDING_PAYMENT).build();
            when(orderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(
                    OrderItem.builder().productId(10L).quantity(2).build()));
            when(productRepository.restoreStock(10L, 2)).thenReturn(1);

            OrderResponse response = shopService.updateOrderStatus(USER_ID, 1L, Order.OrderStatus.CANCELLED);

            assertThat(response.getStatus()).isEqualTo("CANCELLED");
            // Stock should be restored
            verify(productRepository).restoreStock(10L, 2);
        }

        @Test
        @DisplayName("Illegal status transition throws")
        void illegalStatusTransitionThrows() {
            Order order = Order.builder().id(1L).userId(USER_ID)
                    .status(Order.OrderStatus.DELIVERED).build();
            when(orderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> shopService.updateOrderStatus(USER_ID, 1L, Order.OrderStatus.PROCESSING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal order status transition");
        }

        @Test
        @DisplayName("PAID order cannot transition to PENDING_PAYMENT")
        void paidCannotTransitionToPendingPayment() {
            Order order = Order.builder().id(1L).userId(USER_ID)
                    .status(Order.OrderStatus.PAID).build();
            when(orderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> shopService.updateOrderStatus(USER_ID, 1L, Order.OrderStatus.PENDING_PAYMENT))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ==================================================================
    // STOCK RESTORE ON CANCEL
    // ==================================================================

    @Nested
    @DisplayName("Stock Restore on Cancel")
    class StockRestoreOnCancel {

        @Test
        @DisplayName("Cancelling PENDING_PAYMENT order restores stock atomically")
        void cancelRestoresStock() {
            Order order = Order.builder().id(1L).userId(USER_ID)
                    .status(Order.OrderStatus.PENDING_PAYMENT).build();
            OrderItem item1 = OrderItem.builder().productId(10L).quantity(2).build();
            OrderItem item2 = OrderItem.builder().productId(11L).quantity(1).build();

            when(orderRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item1, item2));
            when(productRepository.restoreStock(anyLong(), anyInt())).thenReturn(1);

            shopService.updateOrderStatus(USER_ID, 1L, Order.OrderStatus.CANCELLED);

            // Both products' stock should be restored
            verify(productRepository).restoreStock(10L, 2);
            verify(productRepository).restoreStock(11L, 1);
        }
    }
}
