package com.ecoverse.service;

import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.Order;
import com.ecoverse.model.OrderItem;
import com.ecoverse.model.Product;
import com.ecoverse.repository.OrderItemRepository;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * SellerService tests — seller order access, IDOR protection,
 * status transition enforcement, and multi-seller order scoping.
 */
@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;

    private SellerService sellerService;

    @BeforeEach
    void setUp() {
        sellerService = new SellerService();
        injectField(sellerService, "orderRepository", orderRepository);
        injectField(sellerService, "orderItemRepository", orderItemRepository);
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

    private Order createPaidOrder(Long orderId, Long userId) {
        return Order.builder()
                .id(orderId).userId(userId).totalPrice(BigDecimal.valueOf(500))
                .status(Order.OrderStatus.PAID).paymentMethod("online")
                .shippingAddress("123 Main St").paymentStatus(Order.PaymentStatus.PAID)
                .currency("INR").refundedAmount(BigDecimal.ZERO).build();
    }

    // ==================================================================
    // SELLER ORDER VIEW TESTS
    // ==================================================================

    @Nested
    @DisplayName("Seller Order View")
    class SellerOrderView {

        @Test
        @DisplayName("Seller can view orders containing their products")
        void sellerCanViewOwnOrders() {
            Order order = createPaidOrder(1L, 10L);
            Page<Order> orderPage = new PageImpl<>(Collections.singletonList(order));
            when(orderRepository.findOrdersContainingSellerProducts(eq(42L), any(Pageable.class)))
                    .thenReturn(orderPage);
            when(orderItemRepository.findByOrderIdAndSellerId(1L, 42L))
                    .thenReturn(Collections.singletonList(
                            OrderItem.builder().id(1L).orderId(1L).productId(100L)
                                    .productName("Eco Bottle").quantity(2)
                                    .price(BigDecimal.valueOf(250)).unitPrice(BigDecimal.valueOf(250)).build()));

            var result = sellerService.getSellerOrders(42L, Pageable.ofSize(20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Seller cannot view order without their products")
        void sellerCannotViewOtherOrder() {
            when(orderRepository.findByIdAndSellerProduct(1L, 42L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sellerService.getSellerOrder(42L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Seller sees only their items in multi-seller order")
        void sellerSeesOnlyOwnItems() {
            Order order = createPaidOrder(1L, 10L);
            when(orderRepository.findByIdAndSellerProduct(1L, 42L))
                    .thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdAndSellerId(1L, 42L))
                    .thenReturn(Collections.singletonList(
                            OrderItem.builder().id(1L).orderId(1L).productId(100L)
                                    .productName("Seller A Item").quantity(1)
                                    .price(BigDecimal.valueOf(300)).unitPrice(BigDecimal.valueOf(300)).build()));

            var result = sellerService.getSellerOrder(42L, 1L);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getProductName()).isEqualTo("Seller A Item");
            assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(300));
        }
    }

    // ==================================================================
    // SELLER STATUS TRANSITION TESTS
    // ==================================================================

    @Nested
    @DisplayName("Seller Order Status Transitions")
    class SellerStatusTransitions {

        @Test
        @DisplayName("Seller can transition PAID → PROCESSING")
        void sellerCanTransitionPaidToProcessing() {
            Order order = createPaidOrder(1L, 10L);
            when(orderRepository.findByIdAndSellerProduct(1L, 42L))
                    .thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdAndSellerId(1L, 42L))
                    .thenReturn(Collections.emptyList());
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = sellerService.updateOrderStatus(42L, 1L, Order.OrderStatus.PROCESSING);

            assertThat(result.getStatus()).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("Seller can transition PROCESSING → SHIPPED")
        void sellerCanTransitionProcessingToShipped() {
            Order order = Order.builder().id(1L).userId(10L).totalPrice(BigDecimal.valueOf(500))
                    .status(Order.OrderStatus.PROCESSING).paymentMethod("online")
                    .shippingAddress("123 Main St").paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();

            when(orderRepository.findByIdAndSellerProduct(1L, 42L))
                    .thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdAndSellerId(1L, 42L))
                    .thenReturn(Collections.emptyList());
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = sellerService.updateOrderStatus(42L, 1L, Order.OrderStatus.SHIPPED);

            assertThat(result.getStatus()).isEqualTo("SHIPPED");
        }

        @Test
        @DisplayName("Seller CANNOT transition PAID → CANCELLED")
        void sellerCannotCancelPaidOrder() {
            Order order = createPaidOrder(1L, 10L);
            when(orderRepository.findByIdAndSellerProduct(1L, 42L))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> sellerService.updateOrderStatus(42L, 1L, Order.OrderStatus.CANCELLED))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("PAID → PROCESSING → SHIPPED → DELIVERED");
        }

        @Test
        @DisplayName("Seller CANNOT transition PENDING_PAYMENT → PAID")
        void sellerCannotMarkPaymentPaid() {
            Order order = Order.builder().id(1L).userId(10L).totalPrice(BigDecimal.valueOf(500))
                    .status(Order.OrderStatus.PENDING_PAYMENT).paymentMethod("online")
                    .shippingAddress("123 Main St").paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();

            when(orderRepository.findByIdAndSellerProduct(1L, 42L))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> sellerService.updateOrderStatus(42L, 1L, Order.OrderStatus.PAID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Seller CANNOT transition to REFUNDED")
        void sellerCannotRefund() {
            Order order = createPaidOrder(1L, 10L);
            when(orderRepository.findByIdAndSellerProduct(1L, 42L))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> sellerService.updateOrderStatus(42L, 1L, Order.OrderStatus.REFUNDED))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Same status is idempotent (allowed)")
        void sameStatusIsIdempotent() {
            Order order = createPaidOrder(1L, 10L);
            when(orderRepository.findByIdAndSellerProduct(1L, 42L))
                    .thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdAndSellerId(1L, 42L))
                    .thenReturn(Collections.emptyList());
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = sellerService.updateOrderStatus(42L, 1L, Order.OrderStatus.PAID);
            assertThat(result.getStatus()).isEqualTo("PAID");
        }
    }
}
