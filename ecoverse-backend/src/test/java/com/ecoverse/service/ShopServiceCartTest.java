package com.ecoverse.service;

import com.ecoverse.dto.shop.CartItemResponse;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.CartItem;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ShopService cart operations.
 *
 * Verifies:
 * - Product must be ACTIVE to add to cart
 * - Quantity validation (1-100)
 * - Cart item ownership enforcement
 * - Batch product loading (no N+1)
 * - Cart price from server (not client)
 */
@ExtendWith(MockitoExtension.class)
class ShopServiceCartTest {

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

    private Product createActiveProduct(Long id) {
        return Product.builder()
                .id(id).name("Product " + id).price(BigDecimal.valueOf(299.99))
                .status(ProductStatus.ACTIVE).stock(50).sellerId(1L).category("eco")
                .build();
    }

    // ==================================================================
    // ADD TO CART
    // ==================================================================

    @Nested
    @DisplayName("Add to Cart")
    class AddToCart {

        @Test
        @DisplayName("Can add ACTIVE product to cart")
        void canAddActiveProduct() {
            Product product = createActiveProduct(10L);
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L)).thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
                CartItem ci = inv.getArgument(0);
                ci.setId(1L);
                return ci;
            });

            CartItemResponse response = shopService.addToCart(USER_ID, 10L, 1);

            assertThat(response).isNotNull();
            assertThat(response.getProductId()).isEqualTo(10L);
            assertThat(response.getQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("Cannot add DRAFT product to cart")
        void cannotAddDraftProduct() {
            Product product = Product.builder().id(10L).name("Draft").status(ProductStatus.DRAFT)
                    .price(BigDecimal.valueOf(99)).stock(0).build();
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> shopService.addToCart(USER_ID, 10L, 1))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("Cannot add INACTIVE product to cart")
        void cannotAddInactiveProduct() {
            Product product = Product.builder().id(10L).name("Inactive").status(ProductStatus.INACTIVE)
                    .price(BigDecimal.valueOf(99)).stock(10).build();
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> shopService.addToCart(USER_ID, 10L, 1))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("Cannot add OUT_OF_STOCK product to cart")
        void cannotAddOutOfStockProduct() {
            Product product = Product.builder().id(10L).name("OOS").status(ProductStatus.OUT_OF_STOCK)
                    .price(BigDecimal.valueOf(99)).stock(0).build();
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> shopService.addToCart(USER_ID, 10L, 1))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("Quantity must be at least 1")
        void quantityMustBeAtLeast1() {
            assertThatThrownBy(() -> shopService.addToCart(USER_ID, 10L, 0))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("at least 1");
        }

        @Test
        @DisplayName("Quantity cannot exceed 100")
        void quantityCannotExceed100() {
            assertThatThrownBy(() -> shopService.addToCart(USER_ID, 10L, 101))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("Total cart quantity cannot exceed 100")
        void totalCartQuantityCannotExceed100() {
            Product product = createActiveProduct(10L);
            CartItem existing = CartItem.builder().id(1L).userId(USER_ID).productId(10L).quantity(99).build();

            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> shopService.addToCart(USER_ID, 10L, 2))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("Non-existent product throws ResourceNotFoundException")
        void nonExistentProductThrows() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.addToCart(USER_ID, 999L, 1))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Adding existing product increments quantity")
        void addingExistingProductIncrementsQuantity() {
            Product product = createActiveProduct(10L);
            CartItem existing = CartItem.builder().id(1L).userId(USER_ID).productId(10L).quantity(2).build();

            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L)).thenReturn(Optional.of(existing));
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

            CartItemResponse response = shopService.addToCart(USER_ID, 10L, 3);

            assertThat(response.getQuantity()).isEqualTo(5); // 2 + 3
        }
    }

    // ==================================================================
    // UPDATE CART ITEM QUANTITY
    // ==================================================================

    @Nested
    @DisplayName("Update Cart Item Quantity")
    class UpdateCartItemQuantity {

        @Test
        @DisplayName("Can update quantity to valid value")
        void canUpdateQuantity() {
            CartItem item = CartItem.builder().id(1L).userId(USER_ID).productId(10L).quantity(1).build();
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.findById(10L)).thenReturn(Optional.of(createActiveProduct(10L)));

            CartItemResponse response = shopService.updateCartItemQuantity(USER_ID, 1L, 5);

            assertThat(response.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("Quantity must be at least 1")
        void quantityMustBeAtLeast1() {
            assertThatThrownBy(() -> shopService.updateCartItemQuantity(USER_ID, 1L, 0))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("at least 1");
        }

        @Test
        @DisplayName("Quantity cannot exceed 100")
        void quantityCannotExceed100() {
            assertThatThrownBy(() -> shopService.updateCartItemQuantity(USER_ID, 1L, 101))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("Cannot update another user's cart item")
        void cannotUpdateAnotherUsersCartItem() {
            CartItem item = CartItem.builder().id(1L).userId(OTHER_USER_ID).productId(10L).quantity(1).build();
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> shopService.updateCartItemQuantity(USER_ID, 1L, 5))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("access");
        }
    }

    // ==================================================================
    // REMOVE FROM CART
    // ==================================================================

    @Nested
    @DisplayName("Remove from Cart — Ownership")
    class RemoveFromCart {

        @Test
        @DisplayName("Cannot remove another user's cart item")
        void cannotRemoveAnotherUsersCartItem() {
            CartItem item = CartItem.builder().id(1L).userId(OTHER_USER_ID).productId(10L).quantity(1).build();
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> shopService.removeFromCart(1L, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("access");
        }
    }

    // ==================================================================
    // GET CART — BATCH LOADING
    // ==================================================================

    @Nested
    @DisplayName("Get Cart — Batch Product Loading")
    class GetCartBatchLoading {

        @Test
        @DisplayName("Cart uses batch product loading (no N+1)")
        void cartUsesBatchProductLoading() {
            CartItem c1 = CartItem.builder().id(1L).userId(USER_ID).productId(10L).quantity(2).build();
            CartItem c2 = CartItem.builder().id(2L).userId(USER_ID).productId(11L).quantity(1).build();
            Product p1 = createActiveProduct(10L);
            Product p2 = createActiveProduct(11L);

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(c1, c2));
            when(productRepository.findAllByIdIn(List.of(10L, 11L))).thenReturn(List.of(p1, p2));

            List<CartItemResponse> cart = shopService.getCart(USER_ID);

            assertThat(cart).hasSize(2);
            // Verify batch loading was used (single query for all products)
            verify(productRepository).findAllByIdIn(List.of(10L, 11L));
            // Verify no individual product lookups
            verify(productRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Empty cart returns empty list")
        void emptyCartReturnsEmptyList() {
            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

            List<CartItemResponse> cart = shopService.getCart(USER_ID);

            assertThat(cart).isEmpty();
            verify(productRepository, never()).findAllByIdIn(any());
        }

        @Test
        @DisplayName("Cart price comes from server (not client)")
        void cartPriceComesFromServer() {
            CartItem c1 = CartItem.builder().id(1L).userId(USER_ID).productId(10L).quantity(2).build();
            Product p1 = Product.builder().id(10L).name("Server Product").price(BigDecimal.valueOf(299.99))
                    .status(ProductStatus.ACTIVE).stock(50).build();

            when(cartItemRepository.findByUserId(USER_ID)).thenReturn(List.of(c1));
            when(productRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(p1));

            List<CartItemResponse> cart = shopService.getCart(USER_ID);

            assertThat(cart.get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(299.99));
        }
    }
}
