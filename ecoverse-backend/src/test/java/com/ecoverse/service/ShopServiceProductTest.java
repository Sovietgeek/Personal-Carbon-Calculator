package com.ecoverse.service;

import com.ecoverse.dto.shop.ProductResponse;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.Product;
import com.ecoverse.model.ProductStatus;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ShopService product CRUD operations.
 *
 * Verifies:
 * - Product creation with stock/status
 * - Seller ownership enforcement (update/delete)
 * - Product status transitions
 * - Stock validation
 * - Seller products listing
 */
@ExtendWith(MockitoExtension.class)
class ShopServiceProductTest {

    @Mock private ProductRepository productRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;

    private ShopService shopService;

    private static final Long SELLER_ID = 42L;
    private static final Long OTHER_SELLER_ID = 99L;

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

    private Product createProduct(Long id, Long sellerId, String name, int stock, ProductStatus status) {
        return Product.builder()
                .id(id).sellerId(sellerId).name(name).price(BigDecimal.valueOf(299.99))
                .stock(stock).status(status).category("eco").ecoRating(4)
                .build();
    }

    // ==================================================================
    // PRODUCT CREATION
    // ==================================================================

    @Nested
    @DisplayName("Product Creation")
    class ProductCreation {

        @Test
        @DisplayName("Create product with stock and ACTIVE status")
        void createProductWithStock() {
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setName("Eco Bottle");
            req.setCategory("eco");
            req.setPrice(BigDecimal.valueOf(299.99));
            req.setStock(50);
            req.setEcoRating(4);

            ProductResponse response = shopService.createProduct(SELLER_ID, req);

            assertThat(response).isNotNull();
            assertThat(response.getStock()).isEqualTo(50);
            assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(response.getSellerId()).isEqualTo(SELLER_ID);
        }

        @Test
        @DisplayName("Product creation defaults to ACTIVE status regardless of request")
        void productCreationDefaultsToActive() {
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setName("Eco Bottle");
            req.setCategory("eco");
            req.setPrice(BigDecimal.valueOf(299.99));
            req.setStatus(ProductStatus.DRAFT); // Try to set DRAFT

            ProductResponse response = shopService.createProduct(SELLER_ID, req);

            // Server always sets ACTIVE on creation, ignoring client-provided status
            assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("Product creation defaults stock to 0 if not provided")
        void productCreationDefaultsStockToZero() {
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setName("Eco Bottle");
            req.setCategory("eco");
            req.setPrice(BigDecimal.valueOf(299.99));

            ProductResponse response = shopService.createProduct(SELLER_ID, req);

            assertThat(response.getStock()).isEqualTo(0);
        }

        @Test
        @DisplayName("Seller ID comes from auth context only")
        void sellerIdFromAuthOnly() {
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setName("Eco Bottle");
            req.setCategory("eco");
            req.setPrice(BigDecimal.valueOf(299.99));

            shopService.createProduct(SELLER_ID, req);

            verify(productRepository).save(argThat(p ->
                    p.getSellerId().equals(SELLER_ID)
            ));
        }
    }

    // ==================================================================
    // PRODUCT UPDATE
    // ==================================================================

    @Nested
    @DisplayName("Product Update — Ownership Enforcement")
    class ProductUpdate {

        @Test
        @DisplayName("Seller can update their own product")
        void sellerCanUpdateOwnProduct() {
            Product product = createProduct(10L, SELLER_ID, "Eco Bottle", 50, ProductStatus.ACTIVE);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setName("Updated Bottle");
            req.setPrice(BigDecimal.valueOf(399.99));

            ProductResponse response = shopService.updateProduct(SELLER_ID, 10L, req);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Seller CANNOT update another seller's product (IDOR)")
        void sellerCannotUpdateAnotherSellersProduct() {
            Product product = createProduct(10L, OTHER_SELLER_ID, "Eco Bottle", 50, ProductStatus.ACTIVE);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setName("Hacked Bottle");

            assertThatThrownBy(() -> shopService.updateProduct(SELLER_ID, 10L, req))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("own products");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cannot update archived product")
        void cannotUpdateArchivedProduct() {
            Product product = createProduct(10L, SELLER_ID, "Eco Bottle", 50, ProductStatus.ARCHIVED);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setName("Updated Bottle");

            assertThatThrownBy(() -> shopService.updateProduct(SELLER_ID, 10L, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        @DisplayName("Updating stock to 0 sets OUT_OF_STOCK status")
        void updatingStockToZeroSetsOutOfStock() {
            Product product = createProduct(10L, SELLER_ID, "Eco Bottle", 50, ProductStatus.ACTIVE);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setStock(0);

            shopService.updateProduct(SELLER_ID, 10L, req);

            verify(productRepository).save(argThat(p ->
                    p.getStock() == 0 && p.getStatus() == ProductStatus.OUT_OF_STOCK
            ));
        }

        @Test
        @DisplayName("Updating stock from 0 reactivates OUT_OF_STOCK product")
        void updatingStockFromZeroReactivates() {
            Product product = createProduct(10L, SELLER_ID, "Eco Bottle", 0, ProductStatus.OUT_OF_STOCK);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setStock(20);

            shopService.updateProduct(SELLER_ID, 10L, req);

            verify(productRepository).save(argThat(p ->
                    p.getStock() == 20 && p.getStatus() == ProductStatus.ACTIVE
            ));
        }
    }

    // ==================================================================
    // PRODUCT DELETE (ARCHIVE)
    // ==================================================================

    @Nested
    @DisplayName("Product Delete — Archive")
    class ProductDelete {

        @Test
        @DisplayName("Seller can archive their own product")
        void sellerCanArchiveOwnProduct() {
            Product product = createProduct(10L, SELLER_ID, "Eco Bottle", 50, ProductStatus.ACTIVE);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            shopService.deleteProduct(SELLER_ID, 10L);

            verify(productRepository).save(argThat(p ->
                    p.getStatus() == ProductStatus.ARCHIVED
            ));
        }

        @Test
        @DisplayName("Seller CANNOT archive another seller's product (IDOR)")
        void sellerCannotArchiveAnotherSellersProduct() {
            Product product = createProduct(10L, OTHER_SELLER_ID, "Eco Bottle", 50, ProductStatus.ACTIVE);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> shopService.deleteProduct(SELLER_ID, 10L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("own products");

            verify(productRepository, never()).save(any());
        }
    }

    // ==================================================================
    // SELLER PRODUCTS
    // ==================================================================

    @Nested
    @DisplayName("Seller Products Listing")
    class SellerProducts {

        @Test
        @DisplayName("getSellerProducts returns seller's products")
        void getSellerProductsReturnsOwnProducts() {
            Product p1 = createProduct(1L, SELLER_ID, "Bottle", 50, ProductStatus.ACTIVE);
            Product p2 = createProduct(2L, SELLER_ID, "Bag", 30, ProductStatus.INACTIVE);
            Page<Product> page = new PageImpl<>(List.of(p1, p2));

            when(productRepository.findAllBySellerId(eq(SELLER_ID), any(Pageable.class))).thenReturn(page);

            Page<ProductResponse> result = shopService.getSellerProducts(SELLER_ID, mock(Pageable.class));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getSellerId()).isEqualTo(SELLER_ID);
        }
    }

    // ==================================================================
    // PRODUCT READ
    // ==================================================================

    @Nested
    @DisplayName("Product Read")
    class ProductRead {

        @Test
        @DisplayName("getProduct returns product by ID")
        void getProductReturnsProduct() {
            Product product = createProduct(10L, SELLER_ID, "Eco Bottle", 50, ProductStatus.ACTIVE);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            ProductResponse response = shopService.getProduct(10L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getStock()).isEqualTo(50);
            assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("getProduct throws for non-existent product")
        void getProductThrowsForNonExistent() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.getProduct(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================================================================
    // PRODUCT STATUS TRANSITIONS
    // ==================================================================

    @Nested
    @DisplayName("Product Status Transitions")
    class ProductStatusTransitions {

        @Test
        @DisplayName("ACTIVE → INACTIVE is legal")
        void activeToInactive() {
            Product product = createProduct(10L, SELLER_ID, "Bottle", 50, ProductStatus.ACTIVE);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setStatus(ProductStatus.INACTIVE);

            shopService.updateProduct(SELLER_ID, 10L, req);

            verify(productRepository).save(argThat(p -> p.getStatus() == ProductStatus.INACTIVE));
        }

        @Test
        @DisplayName("ARCHIVED → ACTIVE is illegal (terminal state)")
        void archivedToActiveIllegal() {
            Product product = createProduct(10L, SELLER_ID, "Bottle", 50, ProductStatus.ARCHIVED);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setStatus(ProductStatus.ACTIVE);

            // ARCHIVED product updates are blocked before status check
            assertThatThrownBy(() -> shopService.updateProduct(SELLER_ID, 10L, req))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
