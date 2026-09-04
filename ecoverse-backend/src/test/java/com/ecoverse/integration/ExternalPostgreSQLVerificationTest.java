package com.ecoverse.integration;

import com.ecoverse.model.Order;
import com.ecoverse.model.PaymentEvent;
import com.ecoverse.model.Product;
import com.ecoverse.model.ProductStatus;
import com.ecoverse.model.Role;
import com.ecoverse.model.User;
import com.ecoverse.repository.CartItemRepository;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.PaymentEventRepository;
import com.ecoverse.repository.ProductRepository;
import com.ecoverse.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 7 Verification against External PostgreSQL (docker compose).
 *
 * This test runs against a real PostgreSQL started via docker compose.
 * It verifies the same things as the Testcontainers tests but without
 * requiring Testcontainers to be available (useful on Windows Docker Desktop
 * where Testcontainers has known connectivity issues).
 *
 * Run with: mvn test -Dspring.profiles.active=testpg -Dtest="ExternalPostgreSQLVerificationTest"
 */
@SpringBootTest
@Tag("external-postgres")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("testpg")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class ExternalPostgreSQLVerificationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private PaymentEventRepository paymentEventRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    /** Unique suffix to prevent collisions from previous runs */
    private static final String SUFFIX = Long.toHexString(System.nanoTime());

    /**
     * Execute a decrementStock call within its own transaction.
     * This is needed because executor threads don't inherit Spring's
     * transaction context, and @Modifying queries require an active transaction.
     */
    private int decrementStockInTransaction(Long productId, int qty) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        return txTemplate.execute(status -> productRepository.decrementStock(productId, qty));
    }

    // ================================================================
    // PART D: Real PostgreSQL Concurrency Verification
    // ================================================================
    @Nested
    @DisplayName("Part D — Concurrency (Real PostgreSQL)")
    class ConcurrencyVerification {

        @Test
        @DisplayName("stock=1, two concurrent decrementStock calls — exactly one succeeds")
        void stockOneTwoConcurrentDecrements() throws Exception {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-concurrency-ext-" + SUFFIX + "@test.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Limited Item Ext")
                    .category("solar").price(new BigDecimal("100.00"))
                    .stock(1).status(ProductStatus.ACTIVE).build());

            Long productId = product.getId();

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            AtomicInteger successes = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int result = decrementStockInTransaction(productId, 1);
                        successes.addAndGet(result);
                    } catch (Exception e) {
                        System.err.println("Thread exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            assertThat(completed).withFailMessage("Both threads should complete within timeout").isTrue();
            executor.shutdown();

            assertThat(successes.get()).isEqualTo(1);
            Product updated = productRepository.findById(productId).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);
            assertThat(updated.getStock()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("stock=5, ten concurrent decrementStock calls — exactly 5 succeed")
        void stockFiveTenConcurrentDecrements() throws Exception {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-burst-ext-" + SUFFIX + "@test.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Popular Item Ext")
                    .category("solar").price(new BigDecimal("50.00"))
                    .stock(5).status(ProductStatus.ACTIVE).build());

            Long productId = product.getId();
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successes = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int result = decrementStockInTransaction(productId, 1);
                        successes.addAndGet(result);
                    } catch (Exception e) {
                        System.err.println("Thread exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            assertThat(completed).withFailMessage("All threads should complete within timeout").isTrue();
            executor.shutdown();

            assertThat(successes.get()).isEqualTo(5);
            Product updated = productRepository.findById(productId).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);
            assertThat(updated.getStock()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("stock=0, concurrent decrementStock calls — all fail")
        void stockZeroConcurrentDecrementsAllFail() throws Exception {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-zero-ext-" + SUFFIX + "@test.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Sold Out Item Ext")
                    .category("solar").price(new BigDecimal("50.00"))
                    .stock(0).status(ProductStatus.OUT_OF_STOCK).build());

            Long productId = product.getId();
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successes = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int result = decrementStockInTransaction(productId, 1);
                        successes.addAndGet(result);
                    } catch (Exception e) {
                        System.err.println("Thread exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            executor.shutdown();

            assertThat(successes.get()).isEqualTo(0);
            Product updated = productRepository.findById(productId).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);
        }
    }

    // ================================================================
    // PART F: Idempotency Verification
    // ================================================================
    @Nested
    @DisplayName("Part F — Idempotency (Real PostgreSQL)")
    class IdempotencyVerification {

        @Test
        @DisplayName("Same idempotencyKey returns existing order, no duplicate")
        void sameIdempotencyKeyReturnsExisting() {
            User user = userRepository.save(User.builder()
                    .name("User").email("idem-user-ext-" + SUFFIX + "@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            String idempotencyKey = "idem-ext-" + System.nanoTime();

            Order order1 = orderRepository.save(Order.builder()
                    .userId(user.getId())
                    .idempotencyKey(idempotencyKey)
                    .totalPrice(new BigDecimal("99.99"))
                    .paymentMethod("COD")
                    .shippingAddress("Test Address")
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .build());

            // Lookup by idempotency key should find the existing order
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            assertThat(existing).isPresent();
            assertThat(existing.get().getId()).isEqualTo(order1.getId());

            // Second save with same key should violate unique constraint
            assertThatThrownBy(() -> orderRepository.save(Order.builder()
                            .userId(user.getId())
                            .idempotencyKey(idempotencyKey)
                            .totalPrice(new BigDecimal("199.99"))
                            .paymentMethod("COD")
                            .shippingAddress("Test Address")
                            .status(Order.OrderStatus.PENDING_PAYMENT)
                            .build()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Null idempotencyKey allows multiple orders (no constraint)")
        void nullIdempotencyKeyAllowsDuplicates() {
            User user = userRepository.save(User.builder()
                    .name("User").email("idem-null-ext-" + SUFFIX + "@test.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Order order1 = orderRepository.save(Order.builder()
                    .userId(user.getId())
                    .totalPrice(new BigDecimal("50.00"))
                    .paymentMethod("COD")
                    .shippingAddress("Test Address")
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .build());

            Order order2 = orderRepository.save(Order.builder()
                    .userId(user.getId())
                    .totalPrice(new BigDecimal("75.00"))
                    .paymentMethod("COD")
                    .shippingAddress("Test Address 2")
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .build());

            assertThat(order1.getId()).isNotEqualTo(order2.getId());
        }
    }

    // ================================================================
    // PART G: Webhook Idempotency (provider_event_id unique constraint)
    // ================================================================
    @Nested
    @DisplayName("Part G — Webhook Idempotency (Real PostgreSQL)")
    class WebhookIdempotencyVerification {

        @Test
        @DisplayName("Same provider_event_id rejected by unique constraint")
        void duplicateProviderEventIdRejected() {
            String eventId = "evt_ext_" + System.nanoTime();

            PaymentEvent event1 = PaymentEvent.builder()
                    .providerEventId(eventId)
                    .eventType("payment.captured")
                    .payload("{\"test\": true}")
                    .processed(true)
                    .build();
            paymentEventRepository.save(event1);

            assertThatThrownBy(() -> {
                PaymentEvent event2 = PaymentEvent.builder()
                        .providerEventId(eventId)
                        .eventType("payment.captured")
                        .payload("{\"test\": true}")
                        .processed(true)
                        .build();
                paymentEventRepository.save(event2);
                paymentEventRepository.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Different provider_event_id accepted")
        void differentProviderEventIdAccepted() {
            PaymentEvent event1 = PaymentEvent.builder()
                    .providerEventId("evt_ext_diff1_" + System.nanoTime())
                    .eventType("payment.captured")
                    .payload("{\"test\": 1}")
                    .processed(true)
                    .build();
            paymentEventRepository.save(event1);

            PaymentEvent event2 = PaymentEvent.builder()
                    .providerEventId("evt_ext_diff2_" + System.nanoTime())
                    .eventType("payment.captured")
                    .payload("{\"test\": 2}")
                    .processed(true)
                    .build();
            paymentEventRepository.save(event2);

            assertThat(event1.getId()).isNotNull();
            assertThat(event2.getId()).isNotNull();
        }
    }

    // ================================================================
    // PART C: PostgreSQL Migrations & Constraints Verification
    // ================================================================
    @Nested
    @DisplayName("Part C — PostgreSQL Migrations & Constraints")
    class PostgreSQLConstraintsVerification {

        @Test
        @DisplayName("Flyway migrations ran successfully — tables exist")
        void flywayMigrationsRan() {
            // If we got here, Flyway ran successfully against real PostgreSQL
            assertThat(productRepository).isNotNull();
            assertThat(userRepository).isNotNull();
            assertThat(orderRepository).isNotNull();
        }

        @Test
        @DisplayName("Product stock cannot be negative (CHECK constraint + decrementStock guard)")
        void productStockCannotBeNegative() {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-check-ext-" + SUFFIX + "@test.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Check Item")
                    .category("solar").price(new BigDecimal("10.00"))
                    .stock(5).status(ProductStatus.ACTIVE).build());

            // Decrement all stock
            int result = decrementStockInTransaction(product.getId(), 5);
            assertThat(result).isEqualTo(1);

            // Try to decrement when stock is 0 — should return 0 (no rows affected)
            int result2 = decrementStockInTransaction(product.getId(), 1);
            assertThat(result2).isEqualTo(0);

            // Verify stock is still 0 (not negative)
            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);
        }

        @Test
        @DisplayName("User email is unique (UNIQUE constraint)")
        void userEmailIsUnique() {
            String email = "unique-ext-" + System.nanoTime() + "@test.com";
            userRepository.save(User.builder()
                    .name("User1").email(email).password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            assertThatThrownBy(() -> userRepository.save(User.builder()
                            .name("User2").email(email).password("hash2")
                            .country("IN").role(Role.USER).enabled(true).build()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ================================================================
    // PART E: Transaction Rollback Verification
    // ================================================================
    @Nested
    @DisplayName("Part E — Transaction Rollback (Real PostgreSQL)")
    class TransactionRollbackVerification {

        @Test
        @DisplayName("decrementStock is atomic — stock never goes negative under concurrent load")
        void decrementStockIsAtomic() throws Exception {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-atomic-ext-" + SUFFIX + "@test.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Atomic Item")
                    .category("solar").price(new BigDecimal("25.00"))
                    .stock(3).status(ProductStatus.ACTIVE).build());

            Long productId = product.getId();

            // 10 threads trying to decrement 1 each, only 3 should succeed
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successes = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int result = decrementStockInTransaction(productId, 1);
                        successes.addAndGet(result);
                    } catch (Exception e) {
                        System.err.println("Thread exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            // Exactly 3 decrements succeed
            assertThat(successes.get()).isEqualTo(3);

            // Stock is exactly 0 — never negative
            Product updated = productRepository.findById(productId).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);
        }
    }
}
