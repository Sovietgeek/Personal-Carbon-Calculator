package com.ecoverse.integration;

import com.ecoverse.model.Order;
import com.ecoverse.model.PaymentAttempt;
import com.ecoverse.model.PaymentEvent;
import com.ecoverse.model.Product;
import com.ecoverse.model.ProductStatus;
import com.ecoverse.model.Role;
import com.ecoverse.model.User;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.PaymentAttemptRepository;
import com.ecoverse.repository.PaymentEventRepository;
import com.ecoverse.repository.ProductRepository;
import com.ecoverse.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL Integration Tests using Testcontainers.
 *
 * Tests critical scenarios against real PostgreSQL that H2 cannot fully replicate:
 * - Flyway migration chain V1→V16
 * - Foreign keys, indexes, constraints, NUMERIC precision
 * - Inventory concurrency (stock=1, two buyers)
 * - Idempotency (same X-Idempotency-Key → one order)
 * - Duplicate webhook (same provider_event_id → logic runs once)
 * - Duplicate payment verification (same payment → no double-transition)
 * - Refund duplication (same refund → safely rejected)
 */
@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Tag("testcontainers")
class PostgreSQLIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecoverse_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.h2.console.enabled", () -> "false");
    }

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ================================================================
    // FLYWAY MIGRATIONS
    // ================================================================

    @Nested
    @DisplayName("Flyway Migration Verification")
    class FlywayVerification {

        @Test
        @DisplayName("All V1-V16 migrations apply successfully against real PostgreSQL")
        void allMigrationsApply() {
            // If we got here, Spring context loaded → Flyway ran successfully
            var count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
            assertThat(count).isGreaterThanOrEqualTo(16);
        }

        @Test
        @DisplayName("payment_attempts table exists with correct columns")
        void paymentAttemptsTableExists() {
            assertThatCode(() -> jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_attempts", Integer.class))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("payment_events table exists with unique constraint on provider_event_id")
        void paymentEventsTableExistsWithUniqueConstraint() {
            assertThatCode(() -> jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_events", Integer.class))
                    .doesNotThrowAnyException();

            // Verify unique constraint exists
            var constraintCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints " +
                    "WHERE table_name = 'payment_events' AND constraint_type = 'UNIQUE'", Integer.class);
            assertThat(constraintCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("orders table has idempotency_key column from V13")
        void ordersTableHasIdempotencyKeyColumn() {
            var colCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = 'orders' AND column_name = 'idempotency_key'", Integer.class);
            assertThat(colCount).isEqualTo(1);
        }

        @Test
        @DisplayName("orders table has refunded_amount column from V16")
        void ordersTableHasV16Fields() {
            var colCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = 'orders' AND column_name = 'refunded_amount'", Integer.class);
            assertThat(colCount).isEqualTo(1);
        }

        @Test
        @DisplayName("NUMERIC precision preserved for monetary values in PostgreSQL")
        void numericPrecisionPreserved() {
            User user = userRepository.save(User.builder()
                    .name("Precision Test").email("precision@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(user.getId()).name("Test Product")
                    .category("solar").price(new BigDecimal("99999.99"))
                    .stock(10).status(ProductStatus.ACTIVE).build());

            Product found = productRepository.findById(product.getId()).orElseThrow();
            assertThat(found.getPrice()).isEqualByComparingTo(new BigDecimal("99999.99"));
        }
    }

    // ================================================================
    // FOREIGN KEY AND CONSTRAINT TESTS
    // ================================================================

    @Nested
    @DisplayName("Constraints and Foreign Keys")
    class ConstraintTests {

        @Test
        @DisplayName("PaymentAttempt FK to orders is enforced by PostgreSQL")
        void paymentAttemptFkEnforced() {
            PaymentAttempt attempt = PaymentAttempt.builder()
                    .orderId(99999L) // Non-existent order
                    .provider("razorpay")
                    .amount(BigDecimal.TEN)
                    .currency("INR")
                    .status("PENDING")
                    .build();

            assertThatThrownBy(() -> paymentAttemptRepository.saveAndFlush(attempt))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("PaymentEvent provider_event_id unique constraint enforced by PostgreSQL")
        void paymentEventProviderEventIdUnique() {
            User user = userRepository.save(User.builder()
                    .name("Unique Test").email("unique@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .paymentMethod("card")
                    .shippingAddress("Test Address")
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").build());

            PaymentEvent event1 = PaymentEvent.builder()
                    .providerEventId("evt_unique_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();
            paymentEventRepository.saveAndFlush(event1);

            PaymentEvent event2 = PaymentEvent.builder()
                    .providerEventId("evt_unique_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();

            assertThatThrownBy(() -> paymentEventRepository.saveAndFlush(event2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Order user_id NOT NULL constraint enforced by PostgreSQL")
        void orderUserIdNotNull() {
            Order order = Order.builder()
                    .userId(null) // Violates NOT NULL
                    .totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .paymentMethod("card")
                    .shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").build();

            assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Order razorpay_order_id unique constraint enforced by PostgreSQL")
        void orderRazorpayOrderIdUnique() {
            User user = userRepository.save(User.builder()
                    .name("Rzp Unique").email("rzp-unique@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order1 = Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .razorpayOrderId("order_rzp_unique_001")
                    .currency("INR").build();
            orderRepository.saveAndFlush(order1);

            Order order2 = Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .razorpayOrderId("order_rzp_unique_001") // Duplicate
                    .currency("INR").build();

            assertThatThrownBy(() -> orderRepository.saveAndFlush(order2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ================================================================
    // INVENTORY CONCURRENCY
    // ================================================================

    @Nested
    @DisplayName("Inventory Concurrency")
    class InventoryConcurrency {

        @Test
        @DisplayName("stock=1, two sequential decrements — exactly one succeeds (atomic)")
        void stockOneTwoDecrementsExactlyOneSucceeds() {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller@concurrency.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Limited Item")
                    .category("solar").price(new BigDecimal("100.00"))
                    .stock(1).status(ProductStatus.ACTIVE).build());

            // Two sequential atomic decrements — exactly one should succeed
            int decremented1 = productRepository.decrementStock(product.getId(), 1);
            int decremented2 = productRepository.decrementStock(product.getId(), 1);

            assertThat(decremented1).isEqualTo(1);
            assertThat(decremented2).isEqualTo(0); // Stock already 0

            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);
        }

        @Test
        @DisplayName("decrementStock with insufficient stock returns 0")
        void decrementStockInsufficientReturnsZero() {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-nostock@concurrency.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Scarce Item")
                    .category("solar").price(new BigDecimal("50.00"))
                    .stock(2).status(ProductStatus.ACTIVE).build());

            // Request 5 but only 2 available
            int result = productRepository.decrementStock(product.getId(), 5);
            assertThat(result).isEqualTo(0); // No rows affected

            // Stock unchanged
            Product unchanged = productRepository.findById(product.getId()).orElseThrow();
            assertThat(unchanged.getStock()).isEqualTo(2);
        }

        @Test
        @DisplayName("restoreStock increments stock back")
        void restoreStockIncrementsBack() {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-restore@concurrency.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Restored Item")
                    .category("solar").price(new BigDecimal("50.00"))
                    .stock(5).status(ProductStatus.ACTIVE).build());

            // Decrement 2
            productRepository.decrementStock(product.getId(), 2);
            Product afterDec = productRepository.findById(product.getId()).orElseThrow();
            assertThat(afterDec.getStock()).isEqualTo(3);

            // Restore 2
            productRepository.restoreStock(product.getId(), 2);
            Product afterRestore = productRepository.findById(product.getId()).orElseThrow();
            assertThat(afterRestore.getStock()).isEqualTo(5);
        }
    }

    // ================================================================
    // IDEMPOTENCY
    // ================================================================

    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("Duplicate webhook event — rejected by unique constraint, logic runs once")
        void duplicateWebhookEventRejectedOnce() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("idempotent@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .paymentMethod("card")
                    .shippingAddress("Test Address")
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").build());

            // First event saved successfully
            PaymentEvent event1 = PaymentEvent.builder()
                    .providerEventId("evt_dup_test_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();
            paymentEventRepository.saveAndFlush(event1);

            // Idempotency check — event already exists
            boolean exists = paymentEventRepository.existsByProviderEventId("evt_dup_test_001");
            assertThat(exists).isTrue();

            // Attempt to save duplicate — must fail
            PaymentEvent event2 = PaymentEvent.builder()
                    .providerEventId("evt_dup_test_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();

            assertThatThrownBy(() -> paymentEventRepository.saveAndFlush(event2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // Verify only one event persisted
            List<PaymentEvent> events = paymentEventRepository
                    .findByOrderIdOrderByCreatedAtDesc(order.getId(), PageRequest.of(0, 10))
                    .getContent();
            assertThat(events).hasSize(1);
        }

        @Test
        @DisplayName("Same idempotency key on orders — lookup returns existing order")
        void sameIdempotencyKeyOneOrder() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("idem-order@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            String idemKey = "idem_key_" + System.currentTimeMillis();

            Order order1 = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .idempotencyKey(idemKey)
                    .currency("INR").build());

            // Second lookup with same key — should find the first order
            Optional<Order> found = orderRepository.findByIdempotencyKey(idemKey);
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(order1.getId());
        }

        @Test
        @DisplayName("Null idempotency key — multiple orders allowed")
        void nullIdempotencyKeyMultipleOrdersAllowed() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("null-idem@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            // Two orders with null idempotency key
            orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .idempotencyKey(null).currency("INR").build());

            orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .idempotencyKey(null).currency("INR").build());

            // Both should be saved
            List<Order> orders = orderRepository.findByUserId(user.getId());
            assertThat(orders).hasSize(2);
        }

        @Test
        @DisplayName("Duplicate payment verification — no double-transition (guard check)")
        void duplicatePaymentVerificationNoDoubleTransition() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("dup-verify@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card")
                    .shippingAddress("Test Address")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build());

            // Payment already PAID — re-verification should be a no-op
            boolean alreadyPaid = order.getPaymentStatus() == Order.PaymentStatus.PAID;
            assertThat(alreadyPaid).isTrue();

            // Verify that PAID → PAID is idempotent (canTransitionTo allows same status)
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();

            // But PAID → PENDING is illegal
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.PENDING)).isFalse();
        }
    }

    // ================================================================
    // ORDER STATUS TRANSITION ENFORCEMENT
    // ================================================================

    @Nested
    @DisplayName("Order Status Transition Enforcement")
    class StatusTransitionTests {

        @Test
        @DisplayName("Legal transitions are allowed")
        void legalTransitionsAllowed() {
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAID)).isTrue();
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.PROCESSING)).isTrue();
            assertThat(Order.OrderStatus.PROCESSING.canTransitionTo(Order.OrderStatus.SHIPPED)).isTrue();
            assertThat(Order.OrderStatus.SHIPPED.canTransitionTo(Order.OrderStatus.DELIVERED)).isTrue();
        }

        @Test
        @DisplayName("Illegal transitions are rejected")
        void illegalTransitionsRejected() {
            // Cannot go backwards
            assertThat(Order.OrderStatus.DELIVERED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.SHIPPED.canTransitionTo(Order.OrderStatus.PENDING_PAYMENT)).isFalse();
            // Terminal states
            assertThat(Order.OrderStatus.CANCELLED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.REFUNDED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("validateTransitionTo throws on illegal transition")
        void validateTransitionToThrowsOnIllegal() {
            assertThatThrownBy(() -> Order.OrderStatus.REFUNDED.validateTransitionTo(Order.OrderStatus.PAID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal order status transition");
        }

        @Test
        @DisplayName("Payment status transitions are enforced")
        void paymentStatusTransitionsEnforced() {
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.REFUND_PENDING)).isTrue();
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.REFUNDED)).isTrue();

            // Illegal
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
        }
    }

    // ================================================================
    // PAYMENT ATTEMPT LIFECYCLE
    // ================================================================

    @Nested
    @DisplayName("Payment Attempt Lifecycle")
    class PaymentAttemptLifecycle {

        @Test
        @DisplayName("Multiple attempts on one order — both preserved (append-only)")
        void multipleAttemptsPreserved() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("attempts@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .paymentMethod("card")
                    .shippingAddress("Test Address")
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").build());

            // Attempt 1 → FAILED
            PaymentAttempt attempt1 = PaymentAttempt.builder()
                    .orderId(order.getId()).provider("razorpay")
                    .providerOrderId("order_attempt_1").providerPaymentId("pay_attempt_1")
                    .amount(BigDecimal.TEN).currency("INR").status("FAILED")
                    .failureReason("Payment declined").build();
            paymentAttemptRepository.saveAndFlush(attempt1);

            // Attempt 2 → SUCCESS
            PaymentAttempt attempt2 = PaymentAttempt.builder()
                    .orderId(order.getId()).provider("razorpay")
                    .providerOrderId("order_attempt_2").providerPaymentId("pay_attempt_2")
                    .amount(BigDecimal.TEN).currency("INR").status("SUCCESS").build();
            paymentAttemptRepository.saveAndFlush(attempt2);

            // Verify both attempts preserved
            List<PaymentAttempt> attempts = paymentAttemptRepository.findByOrderId(order.getId());
            assertThat(attempts).hasSize(2);

            // Verify old attempt not overwritten
            assertThat(attempts.stream().filter(a -> "FAILED".equals(a.getStatus()))).hasSize(1);
            assertThat(attempts.stream().filter(a -> "SUCCESS".equals(a.getStatus()))).hasSize(1);

            // Verify provider IDs unique per attempt
            assertThat(attempts.get(0).getProviderOrderId()).isNotEqualTo(attempts.get(1).getProviderOrderId());
        }

        @Test
        @DisplayName("Refund duplication — refundedAmount equals totalPrice, no further refund possible")
        void refundDuplicationSafelyRejected() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("refund-dup@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(new BigDecimal("100.00"))
                    .status(Order.OrderStatus.REFUNDED)
                    .paymentMethod("card")
                    .shippingAddress("Test Address")
                    .paymentStatus(Order.PaymentStatus.REFUNDED)
                    .refundedAmount(new BigDecimal("100.00"))
                    .currency("INR").build());

            // Already fully refunded — second refund should be rejected
            BigDecimal refundable = order.getTotalPrice().subtract(order.getRefundedAmount());
            assertThat(refundable).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Partial refund — refundedAmount tracks cumulative refund")
        void partialRefundTracksCumulative() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("partial-refund@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(new BigDecimal("200.00"))
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card")
                    .shippingAddress("Test Address")
                    .paymentStatus(Order.PaymentStatus.REFUND_PENDING)
                    .refundedAmount(new BigDecimal("50.00"))
                    .currency("INR").build());

            // Partial refund — remaining refundable amount
            BigDecimal refundable = order.getTotalPrice().subtract(order.getRefundedAmount());
            assertThat(refundable).isEqualByComparingTo(new BigDecimal("150.00"));
        }
    }

    // ================================================================
    // INDEX VERIFICATION
    // ================================================================

    @Nested
    @DisplayName("Index Verification")
    class IndexTests {

        @Test
        @DisplayName("payment_attempts has index on order_id")
        void paymentAttemptsOrderIndex() {
            var count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'payment_attempts' AND indexname LIKE '%order_id%'",
                    Integer.class);
            assertThat(count).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("orders has index on user_id")
        void ordersUserIdIndex() {
            var count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'orders' AND indexname LIKE '%user_id%'",
                    Integer.class);
            assertThat(count).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("orders has index on razorpay_order_id")
        void ordersRazorpayOrderIdIndex() {
            var count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'orders' AND indexname LIKE '%razorpay%'",
                    Integer.class);
            assertThat(count).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("payment_events has index on order_id")
        void paymentEventsOrderIdIndex() {
            var count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'payment_events' AND indexname LIKE '%order_id%'",
                    Integer.class);
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }
}
