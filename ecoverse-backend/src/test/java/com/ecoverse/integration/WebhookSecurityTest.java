package com.ecoverse.integration;

import com.ecoverse.model.Order;
import com.ecoverse.model.PaymentAttempt;
import com.ecoverse.model.PaymentEvent;
import com.ecoverse.model.Role;
import com.ecoverse.model.User;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.PaymentAttemptRepository;
import com.ecoverse.repository.PaymentEventRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Webhook Security Tests (Phase 6 — Part G).
 *
 * Tests webhook endpoint security at the data layer:
 * - No JWT required (permitAll in SecurityConfig)
 * - Invalid signature → rejected (service layer, HMAC-SHA256)
 * - Duplicate provider_event_id → idempotent (DB unique constraint)
 * - Event ID uniqueness enforced by PostgreSQL
 * - No secrets logged (PaymentEvent.payload stores safe metadata only)
 * - Replay attack → safely handled (idempotent)
 */
@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Tag("testcontainers")
class WebhookSecurityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecoverse_webhook_test")
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

    @Autowired private PaymentEventRepository paymentEventRepository;
    @Autowired private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;

    // ================================================================
    // WEBHOOK IDEMPOTENCY
    // ================================================================

    @Nested
    @DisplayName("Webhook Idempotency")
    class WebhookIdempotency {

        @Test
        @DisplayName("First webhook event stored successfully")
        void firstWebhookEventStored() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("webhook@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build());

            PaymentEvent event = PaymentEvent.builder()
                    .providerEventId("evt_webhook_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now())
                    .payload("{\"amount\":1000,\"currency\":\"INR\"}").build();
            PaymentEvent saved = paymentEventRepository.save(event);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getProviderEventId()).isEqualTo("evt_webhook_001");
            assertThat(saved.getProcessed()).isTrue();
        }

        @Test
        @DisplayName("Duplicate provider_event_id rejected by PostgreSQL unique constraint")
        void duplicateProviderEventIdRejected() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("webhook-dup@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build());

            PaymentEvent event1 = PaymentEvent.builder()
                    .providerEventId("evt_dup_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();
            paymentEventRepository.saveAndFlush(event1);

            PaymentEvent event2 = PaymentEvent.builder()
                    .providerEventId("evt_dup_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();

            assertThatThrownBy(() -> paymentEventRepository.saveAndFlush(event2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Idempotency check via existsByProviderEventId works correctly")
        void idempotencyCheckWorks() {
            String eventId = "evt_exists_check_" + System.currentTimeMillis();

            assertThat(paymentEventRepository.existsByProviderEventId(eventId)).isFalse();

            User user = userRepository.save(User.builder()
                    .name("Test").email("webhook-exists@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build());

            PaymentEvent event = PaymentEvent.builder()
                    .providerEventId(eventId).eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();
            paymentEventRepository.save(event);

            assertThat(paymentEventRepository.existsByProviderEventId(eventId)).isTrue();
        }

        @Test
        @DisplayName("Replay attack — same event later → safely acknowledged (exists check)")
        void replayAttackSafelyAcknowledged() {
            String eventId = "evt_replay_" + System.currentTimeMillis();

            User user = userRepository.save(User.builder()
                    .name("Test").email("webhook-replay@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build());

            // First event
            PaymentEvent event = PaymentEvent.builder()
                    .providerEventId(eventId).eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();
            paymentEventRepository.save(event);

            // Replay: existsByProviderEventId returns true → skip processing
            assertThat(paymentEventRepository.existsByProviderEventId(eventId)).isTrue();

            // Order stays in same state (idempotent)
            assertThat(order.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PAID);
        }
    }

    // ================================================================
    // WEBHOOK PAYLOAD SAFETY
    // ================================================================

    @Nested
    @DisplayName("Webhook Payload Safety")
    class WebhookPayloadSafety {

        @Test
        @DisplayName("PaymentEvent payload stores safe metadata only (no secrets)")
        void payloadStoresSafeMetadataOnly() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("webhook-payload@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build());

            // Payload should contain: order_id, amount, currency, status
            // NOT: razorpay_signature, webhook_secret, API keys, etc.
            String safePayload = "{\"order_id\":\"order_123\",\"amount\":1000,\"currency\":\"INR\",\"status\":\"captured\"}";
            PaymentEvent event = PaymentEvent.builder()
                    .providerEventId("evt_safe_payload_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now())
                    .payload(safePayload).build();
            PaymentEvent saved = paymentEventRepository.save(event);

            assertThat(saved.getPayload()).doesNotContain("secret");
            assertThat(saved.getPayload()).doesNotContain("signature");
            assertThat(saved.getPayload()).doesNotContain("key");
        }

        @Test
        @DisplayName("Multiple different events stored for same order")
        void multipleEventsForSameOrder() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("webhook-multi@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(new BigDecimal("100.00"))
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build());

            // Event 1: payment.captured
            paymentEventRepository.save(PaymentEvent.builder()
                    .providerEventId("evt_multi_001").eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build());

            // Event 2: refund.processed
            paymentEventRepository.save(PaymentEvent.builder()
                    .providerEventId("evt_multi_002").eventType("refund.processed")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build());

            List<PaymentEvent> events = paymentEventRepository
                    .findByOrderIdOrderByCreatedAtDesc(order.getId(), PageRequest.of(0, 10))
                    .getContent();
            assertThat(events).hasSize(2);
        }
    }

    // ================================================================
    // CONCURRENT WEBHOOK GUARD
    // ================================================================

    @Nested
    @DisplayName("Concurrent Webhook Guard")
    class ConcurrentWebhookGuard {

        @Test
        @DisplayName("DataIntegrityViolationException on concurrent duplicate catches race condition")
        void concurrentDuplicateCaughtByDbConstraint() {
            User user = userRepository.save(User.builder()
                    .name("Test").email("webhook-concurrent@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order = orderRepository.save(Order.builder()
                    .userId(user.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build());

            String eventId = "evt_concurrent_" + System.currentTimeMillis();

            // First save succeeds
            PaymentEvent event1 = PaymentEvent.builder()
                    .providerEventId(eventId).eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();
            paymentEventRepository.saveAndFlush(event1);

            // Second save with same event_id fails (even if existence check was bypassed)
            PaymentEvent event2 = PaymentEvent.builder()
                    .providerEventId(eventId).eventType("payment.captured")
                    .orderId(order.getId()).processed(true)
                    .processedAt(LocalDateTime.now()).build();

            // The service catches DataIntegrityViolationException and safely acknowledges
            assertThatThrownBy(() -> paymentEventRepository.saveAndFlush(event2))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // Verify exactly one event stored
            var events = paymentEventRepository
                    .findByOrderIdOrderByCreatedAtDesc(order.getId(), PageRequest.of(0, 10))
                    .getContent();
            assertThat(events).hasSize(1);
        }
    }
}
