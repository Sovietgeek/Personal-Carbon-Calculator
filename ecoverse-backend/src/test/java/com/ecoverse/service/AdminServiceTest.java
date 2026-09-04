package com.ecoverse.service;

import com.ecoverse.model.*;
import com.ecoverse.repository.*;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AdminService tests — user management, product moderation,
 * order management, analytics, and audit logging.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentEventRepository paymentEventRepository;
    @Mock private AuditLogService auditLogService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService();
        injectField(adminService, "userRepository", userRepository);
        injectField(adminService, "productRepository", productRepository);
        injectField(adminService, "orderRepository", orderRepository);
        injectField(adminService, "orderItemRepository", orderItemRepository);
        injectField(adminService, "paymentEventRepository", paymentEventRepository);
        injectField(adminService, "auditLogService", auditLogService);
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

    // ==================================================================
    // USER MANAGEMENT TESTS
    // ==================================================================

    @Nested
    @DisplayName("Admin User Management")
    class UserManagement {

        @Test
        @DisplayName("Get user by ID returns user")
        void getUserReturnsUser() {
            User user = User.builder().id(1L).name("Test").email("test@test.com").role(Role.USER).enabled(true).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            User result = adminService.getUser(1L);
            assertThat(result.getName()).isEqualTo("Test");
        }

        @Test
        @DisplayName("Get non-existent user throws ResourceNotFoundException")
        void getNonExistentUserThrows() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getUser(999L))
                    .isInstanceOf(com.ecoverse.exception.ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Disable user sets enabled=false and audits")
        void disableUserSetsEnabledFalse() {
            User user = User.builder().id(1L).name("Test").email("test@test.com").role(Role.USER).enabled(true).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = adminService.updateUserStatus(99L, 1L, false);

            assertThat(result.getEnabled()).isFalse();
            verify(auditLogService).log(eq(99L), eq("ACCOUNT_DISABLE"), any(), any());
        }

        @Test
        @DisplayName("Enable user sets enabled=true and audits")
        void enableUserSetsEnabledTrue() {
            User user = User.builder().id(1L).name("Test").email("test@test.com").role(Role.USER).enabled(false).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = adminService.updateUserStatus(99L, 1L, true);

            assertThat(result.getEnabled()).isTrue();
            verify(auditLogService).log(eq(99L), eq("ACCOUNT_ENABLE"), any(), any());
        }

        @Test
        @DisplayName("Change user role from USER to SELLER and audits")
        void changeRoleUserToSeller() {
            User user = User.builder().id(1L).name("Test").email("test@test.com").role(Role.USER).enabled(true).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = adminService.updateUserRole(99L, 1L, Role.SELLER);

            assertThat(result.getRole()).isEqualTo(Role.SELLER);
            verify(auditLogService).log(eq(99L), eq("ROLE_CHANGE"), any(), any());
        }

        @Test
        @DisplayName("No change if role is same as requested")
        void noChangeIfSameRole() {
            User user = User.builder().id(1L).name("Test").email("test@test.com").role(Role.USER).enabled(true).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            User result = adminService.updateUserRole(99L, 1L, Role.USER);

            assertThat(result.getRole()).isEqualTo(Role.USER);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("List users returns paginated results")
        void listUsersReturnsPaginated() {
            Page<User> userPage = new PageImpl<>(Collections.singletonList(
                    User.builder().id(1L).name("Test").email("test@test.com").build()));
            when(userRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                    .thenReturn(userPage);

            Page<User> result = adminService.getUsers(Pageable.ofSize(20));
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Search users by name or email")
        void searchUsers() {
            Page<User> userPage = new PageImpl<>(Collections.singletonList(
                    User.builder().id(1L).name("Test").email("test@test.com").build()));
            when(userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    eq("test"), eq("test"), any(Pageable.class))).thenReturn(userPage);

            Page<User> result = adminService.searchUsers("test", Pageable.ofSize(20));
            assertThat(result.getContent()).hasSize(1);
        }
    }

    // ==================================================================
    // PRODUCT MODERATION TESTS
    // ==================================================================

    @Nested
    @DisplayName("Admin Product Moderation")
    class ProductModeration {

        @Test
        @DisplayName("Archive product sets status and audits")
        void archiveProductSetsStatus() {
            Product product = Product.builder().id(1L).name("Test").price(BigDecimal.valueOf(100))
                    .status(ProductStatus.ACTIVE).stock(10).build();
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Product result = adminService.updateProductStatus(99L, 1L, ProductStatus.ARCHIVED);

            assertThat(result.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
            verify(auditLogService).log(eq(99L), eq("PRODUCT_STATUS_CHANGE"), any(), any());
        }

        @Test
        @DisplayName("Get products returns paginated results")
        void getProductsReturnsPaginated() {
            Page<Product> productPage = new PageImpl<>(Collections.singletonList(
                    Product.builder().id(1L).name("Test").build()));
            when(productRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                    .thenReturn(productPage);

            Page<Product> result = adminService.getProducts(Pageable.ofSize(20));
            assertThat(result.getContent()).hasSize(1);
        }
    }

    // ==================================================================
    // ORDER MANAGEMENT TESTS
    // ==================================================================

    @Nested
    @DisplayName("Admin Order Management")
    class OrderManagement {

        @Test
        @DisplayName("Update order status and audits")
        void updateOrderStatusAndAudits() {
            Order order = Order.builder().id(1L).userId(10L).totalPrice(BigDecimal.valueOf(500))
                    .status(Order.OrderStatus.PAID).paymentMethod("online")
                    .shippingAddress("123 Main St").paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = adminService.updateOrderStatus(99L, 1L, Order.OrderStatus.PROCESSING);

            assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PROCESSING);
            verify(auditLogService).log(eq(99L), eq("ORDER_STATE_OVERRIDE"), any(), any());
        }

        @Test
        @DisplayName("Cancel order restores stock")
        void cancelOrderRestoresStock() {
            Order order = Order.builder().id(1L).userId(10L).totalPrice(BigDecimal.valueOf(500))
                    .status(Order.OrderStatus.PAID).paymentMethod("online")
                    .shippingAddress("123 Main St").paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderId(1L)).thenReturn(Collections.singletonList(
                    OrderItem.builder().id(1L).orderId(1L).productId(100L).quantity(2)
                            .price(BigDecimal.valueOf(250)).unitPrice(BigDecimal.valueOf(250)).build()));
            when(productRepository.restoreStock(100L, 2)).thenReturn(1);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = adminService.updateOrderStatus(99L, 1L, Order.OrderStatus.CANCELLED);

            assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
            verify(productRepository).restoreStock(100L, 2);
        }

        @Test
        @DisplayName("Illegal status transition throws")
        void illegalTransitionThrows() {
            Order order = Order.builder().id(1L).userId(10L).totalPrice(BigDecimal.valueOf(500))
                    .status(Order.OrderStatus.DELIVERED).paymentMethod("online")
                    .shippingAddress("123 Main St").paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> adminService.updateOrderStatus(99L, 1L, Order.OrderStatus.PROCESSING))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ==================================================================
    // ANALYTICS TESTS
    // ==================================================================

    @Nested
    @DisplayName("Admin Analytics")
    class Analytics {

        @Test
        @DisplayName("Analytics returns platform counts")
        void analyticsReturnsCounts() {
            when(userRepository.count()).thenReturn(100L);
            when(userRepository.countByRole(Role.SELLER)).thenReturn(10L);
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);
            when(productRepository.count()).thenReturn(50L);
            when(orderRepository.count()).thenReturn(200L);
            when(orderRepository.findAll()).thenReturn(Collections.emptyList());

            var result = adminService.getAnalytics();

            assertThat(result.get("totalUsers")).isEqualTo(100L);
            assertThat(result.get("sellerCount")).isEqualTo(10L);
            assertThat(result.get("adminCount")).isEqualTo(2L);
            assertThat(result.get("totalProducts")).isEqualTo(50L);
            assertThat(result.get("totalOrders")).isEqualTo(200L);
            assertThat(result.get("customerCount")).isEqualTo(88L); // 100 - 10 - 2
        }
    }
}
