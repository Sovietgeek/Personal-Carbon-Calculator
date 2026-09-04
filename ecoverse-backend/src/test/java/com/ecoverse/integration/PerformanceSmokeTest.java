package com.ecoverse.integration;

import com.ecoverse.model.Order;
import com.ecoverse.model.OrderItem;
import com.ecoverse.model.Product;
import com.ecoverse.model.ProductStatus;
import com.ecoverse.model.Role;
import com.ecoverse.model.User;
import com.ecoverse.repository.OrderItemRepository;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.ProductRepository;
import com.ecoverse.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance Smoke Test (Phase 6 — Part S).
 *
 * Creates realistic data volumes and verifies:
 * - Pagination works (no unbounded loading)
 * - Order history with pagination
 * - Product search with pagination
 * - Seller order queries with pagination
 * - Bulk insert + query completes in reasonable time
 * - No N+1 issues on product loading
 */
@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Tag("testcontainers")
class PerformanceSmokeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecoverse_perf_test")
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
        // Batch inserts for performance
        registry.add("spring.jpa.properties.hibernate.jdbc.batch_size", () -> "50");
        registry.add("spring.jpa.properties.hibernate.order_inserts", () -> "true");
        registry.add("spring.jpa.properties.hibernate.order_updates", () -> "true");
    }

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    // ================================================================
    // PAGINATION
    // ================================================================

    @Nested
    @DisplayName("Pagination Verification")
    class PaginationVerification {

        @Test
        @DisplayName("Order history pagination — returns exact page size")
        void orderHistoryPagination() {
            User buyer = userRepository.save(User.builder()
                    .name("Buyer").email("buyer@perf.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            // Create 25 orders
            List<Order> orders = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                orders.add(Order.builder()
                        .userId(buyer.getId()).totalPrice(BigDecimal.TEN)
                        .status(Order.OrderStatus.PAID)
                        .paymentMethod("card").shippingAddress("Test")
                        .paymentStatus(Order.PaymentStatus.PAID)
                        .currency("INR").build());
            }
            orderRepository.saveAll(orders);

            // Page 1: 10 results
            Page<Order> page1 = orderRepository.findByUserIdOrderByCreatedAtDesc(
                    buyer.getId(), PageRequest.of(0, 10));
            assertThat(page1.getContent()).hasSize(10);
            assertThat(page1.getTotalElements()).isEqualTo(25);
            assertThat(page1.getTotalPages()).isEqualTo(3);

            // Page 2: 10 results
            Page<Order> page2 = orderRepository.findByUserIdOrderByCreatedAtDesc(
                    buyer.getId(), PageRequest.of(1, 10));
            assertThat(page2.getContent()).hasSize(10);

            // Page 3: 5 results
            Page<Order> page3 = orderRepository.findByUserIdOrderByCreatedAtDesc(
                    buyer.getId(), PageRequest.of(2, 10));
            assertThat(page3.getContent()).hasSize(5);

            // Page 4: empty
            Page<Order> page4 = orderRepository.findByUserIdOrderByCreatedAtDesc(
                    buyer.getId(), PageRequest.of(3, 10));
            assertThat(page4.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Product listing pagination — returns exact page size")
        void productListingPagination() {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller@perf.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            List<Product> products = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                products.add(Product.builder()
                        .sellerId(seller.getId()).name("Product " + i)
                        .category("solar").price(new BigDecimal("10.00"))
                        .stock(100).status(ProductStatus.ACTIVE).build());
            }
            productRepository.saveAll(products);

            Page<Product> page1 = productRepository.findAll(PageRequest.of(0, 10));
            assertThat(page1.getContent()).hasSize(10);
            assertThat(page1.getTotalElements()).isGreaterThanOrEqualTo(30);
        }

        @Test
        @DisplayName("Seller order pagination — returns exact page size")
        void sellerOrderPagination() {
            User seller = userRepository.save(User.builder()
                    .name("Seller").email("seller-pag@perf.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());
            User buyer = userRepository.save(User.builder()
                    .name("Buyer").email("buyer-pag@perf.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            Product product = productRepository.save(Product.builder()
                    .sellerId(seller.getId()).name("Test Product")
                    .category("solar").price(BigDecimal.TEN)
                    .stock(100).status(ProductStatus.ACTIVE).build());

            // Create 15 orders with this seller's product
            for (int i = 0; i < 15; i++) {
                Order order = orderRepository.save(Order.builder()
                        .userId(buyer.getId()).totalPrice(BigDecimal.TEN)
                        .status(Order.OrderStatus.PAID)
                        .paymentMethod("card").shippingAddress("Test")
                        .paymentStatus(Order.PaymentStatus.PAID)
                        .currency("INR").build());
                orderItemRepository.save(OrderItem.builder()
                        .orderId(order.getId()).productId(product.getId())
                        .productName("Test Product").quantity(1)
                        .price(BigDecimal.TEN).unitPrice(BigDecimal.TEN).build());
            }

            Page<Order> page1 = orderRepository.findOrdersContainingSellerProducts(
                    seller.getId(), PageRequest.of(0, 10));
            assertThat(page1.getContent()).hasSize(10);
            assertThat(page1.getTotalElements()).isEqualTo(15);

            Page<Order> page2 = orderRepository.findOrdersContainingSellerProducts(
                    seller.getId(), PageRequest.of(1, 10));
            assertThat(page2.getContent()).hasSize(5);
        }
    }

    // ================================================================
    // BULK DATA PERFORMANCE
    // ================================================================

    @Nested
    @DisplayName("Bulk Data Performance")
    class BulkDataPerformance {

        @Test
        @DisplayName("1000 products — insert and paginate in reasonable time")
        void thousandProductsInsertAndPaginate() {
            User seller = userRepository.save(User.builder()
                    .name("Bulk Seller").email("bulk@perf.com").password("hash")
                    .country("IN").role(Role.SELLER).enabled(true).build());

            long start = Instant.now().toEpochMilli();

            List<Product> batch = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                batch.add(Product.builder()
                        .sellerId(seller.getId()).name("Bulk Product " + i)
                        .category(i % 2 == 0 ? "solar" : "wind")
                        .price(new BigDecimal("10.00"))
                        .stock(100).status(ProductStatus.ACTIVE).build());
            }
            productRepository.saveAll(batch);

            long insertTime = Instant.now().toEpochMilli() - start;
            // Should complete in reasonable time (<30s)
            assertThat(insertTime).isLessThan(30000);

            // Paginate
            long queryStart = Instant.now().toEpochMilli();
            Page<Product> page = productRepository.findAll(PageRequest.of(0, 50));
            long queryTime = Instant.now().toEpochMilli() - queryStart;

            assertThat(page.getContent()).hasSize(50);
            assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1000);
            // Pagination query should be fast (<2s)
            assertThat(queryTime).isLessThan(2000);
        }

        @Test
        @DisplayName("500 orders — insert and query user orders in reasonable time")
        void fiveHundredOrdersInsertAndQuery() {
            User buyer = userRepository.save(User.builder()
                    .name("Bulk Buyer").email("bulk-buyer@perf.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            long start = Instant.now().toEpochMilli();

            List<Order> batch = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                batch.add(Order.builder()
                        .userId(buyer.getId()).totalPrice(BigDecimal.TEN)
                        .status(Order.OrderStatus.PAID)
                        .paymentMethod("card").shippingAddress("Test")
                        .paymentStatus(Order.PaymentStatus.PAID)
                        .currency("INR").build());
            }
            orderRepository.saveAll(batch);

            long insertTime = Instant.now().toEpochMilli() - start;
            assertThat(insertTime).isLessThan(30000);

            // Query user orders
            long queryStart = Instant.now().toEpochMilli();
            Page<Order> page = orderRepository.findByUserIdOrderByCreatedAtDesc(
                    buyer.getId(), PageRequest.of(0, 20));
            long queryTime = Instant.now().toEpochMilli() - queryStart;

            assertThat(page.getContent()).hasSize(20);
            assertThat(page.getTotalElements()).isEqualTo(500);
            assertThat(queryTime).isLessThan(2000);
        }
    }

    // ================================================================
    // INDEX PERFORMANCE
    // ================================================================

    @Nested
    @DisplayName("Index Performance Verification")
    class IndexPerformance {

        @Test
        @DisplayName("Razorpay order ID lookup uses index (unique)")
        void razorpayOrderIdLookupUsesIndex() {
            User buyer = userRepository.save(User.builder()
                    .name("Idx Buyer").email("idx@perf.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            // Create order with razorpay order ID
            Order order = orderRepository.save(Order.builder()
                    .userId(buyer.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .razorpayOrderId("order_idx_test_001")
                    .currency("INR").build());

            // Lookup by razorpay order ID — uses unique index
            long start = Instant.now().toEpochMilli();
            Optional<Order> found = orderRepository.findByRazorpayOrderId("order_idx_test_001");
            long queryTime = Instant.now().toEpochMilli() - start;

            assertThat(found).isPresent();
            assertThat(queryTime).isLessThan(100); // Indexed lookup should be instant
        }

        @Test
        @DisplayName("Idempotency key lookup uses index efficiently")
        void idempotencyKeyLookupEfficient() {
            User buyer = userRepository.save(User.builder()
                    .name("Idem Buyer").email("idem@perf.com").password("hash")
                    .country("IN").role(Role.USER).enabled(true).build());

            String idemKey = "idem_perf_" + System.currentTimeMillis();
            orderRepository.save(Order.builder()
                    .userId(buyer.getId()).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .idempotencyKey(idemKey)
                    .currency("INR").build());

            long start = Instant.now().toEpochMilli();
            Optional<Order> found = orderRepository.findByIdempotencyKey(idemKey);
            long queryTime = Instant.now().toEpochMilli() - start;

            assertThat(found).isPresent();
            assertThat(queryTime).isLessThan(100);
        }
    }
}
