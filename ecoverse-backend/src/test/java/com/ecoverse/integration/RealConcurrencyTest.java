package com.ecoverse.integration;

import com.ecoverse.model.*;
import com.ecoverse.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL Concurrency Test (Phase 7 — Part D).
 *
 * Uses real threads with Testcontainers PostgreSQL to verify:
 * - stock=1, two simultaneous purchase attempts
 * - Exactly one succeeds, stock=0, stock never negative
 * - Atomic decrementStock prevents race conditions
 */
@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Tag("testcontainers")
class RealConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecoverse_concurrency_test")
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

    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    @Nested
    @DisplayName("Simultaneous Purchase Attempts (Real Threads)")
    class SimultaneousPurchaseAttempts {

        @Test
        @DisplayName("stock=1, two concurrent decrementStock calls — exactly one succeeds")
        void stockOneTwoConcurrentDecrementsExactlyOneSucceeds() throws Exception {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller@concurrent.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Limited Item")
                    .category("solar").price(new BigDecimal("100.00"))
                    .stock(1).status(ProductStatus.ACTIVE).build());

            Long productId = product.getId();

            // Use CountDownLatch to ensure both threads start at the same time
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            AtomicInteger successes = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for signal
                        int result = productRepository.decrementStock(productId, 1);
                        successes.addAndGet(result);
                    } catch (Exception e) {
                        // Log but don't fail the test thread
                        System.err.println("Thread exception: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Release both threads simultaneously
            startLatch.countDown();

            // Wait for both threads to complete
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            assertThat(completed).withFailMessage("Both threads should complete within timeout").isTrue();

            executor.shutdown();

            // Exactly one decrement should have succeeded
            assertThat(successes.get()).isEqualTo(1);

            // Stock should be 0
            Product updated = productRepository.findById(productId).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);

            // Stock must NEVER be negative
            assertThat(updated.getStock()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("stock=5, ten concurrent decrementStock calls — exactly 5 succeed")
        void stockFiveTenConcurrentDecrementsExactlyFiveSucceed() throws Exception {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-burst@concurrent.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Popular Item")
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
                        int result = productRepository.decrementStock(productId, 1);
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

            // Exactly 5 decrements should have succeeded
            assertThat(successes.get()).isEqualTo(5);

            // Stock should be 0
            Product updated = productRepository.findById(productId).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);

            // Stock must NEVER be negative
            assertThat(updated.getStock()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("stock=0, concurrent decrementStock calls — all fail, stock stays 0")
        void stockZeroConcurrentDecrementsAllFail() throws Exception {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-zero@concurrent.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Sold Out Item")
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
                        int result = productRepository.decrementStock(productId, 1);
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

            // No decrements should succeed
            assertThat(successes.get()).isEqualTo(0);

            // Stock should remain 0
            Product updated = productRepository.findById(productId).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(0);
        }
    }
}
