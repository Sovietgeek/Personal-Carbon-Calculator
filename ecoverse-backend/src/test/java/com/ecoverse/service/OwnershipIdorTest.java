package com.ecoverse.service;

import com.ecoverse.dto.health.HealthLogRequest;
import com.ecoverse.dto.shop.OrderResponse;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.*;
import com.ecoverse.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for resource ownership / IDOR protection (B4).
 *
 * Every endpoint that accepts a resource ID MUST verify the authenticated user
 * owns that resource before acting on it. This test suite verifies that for
 * all identified IDOR surfaces:
 *
 * - CartItem deletion (ShopService.removeFromCart)
 * - CarbonEntry deletion (CarbonService.deleteEntry)
 * - Note deletion (NoteService.deleteNote)
 * - Health logs user-scoping (HealthService.getHealthLogs)
 * - Payment verification (PaymentService.verifyPayment) — covered in PaymentServiceTest
 */
@ExtendWith(MockitoExtension.class)
class OwnershipIdorTest {

    @Nested
    @DisplayName("Cart Item Ownership")
    class CartItemOwnership {

        @Mock private ProductRepository productRepository;
        @Mock private CartItemRepository cartItemRepository;
        @Mock private OrderRepository orderRepository;
        @Mock private OrderItemRepository orderItemRepository;

        @InjectMocks private ShopService shopService;

        @Test
        @DisplayName("Owner can remove their own cart item")
        void ownerCanRemoveOwnCartItem() {
            CartItem item = CartItem.builder().id(1L).userId(42L).productId(100L).quantity(2).build();
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

            shopService.removeFromCart(1L, 42L);

            verify(cartItemRepository).deleteByIdAndUserId(1L, 42L);
        }

        @Test
        @DisplayName("User CANNOT remove another user's cart item (IDOR)")
        void userCannotRemoveOtherUsersCartItem() {
            CartItem item = CartItem.builder().id(1L).userId(99L).productId(100L).quantity(2).build();
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> shopService.removeFromCart(1L, 42L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("don't have access");

            // Must NOT delete
            verify(cartItemRepository, never()).deleteByIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("Removing non-existent cart item throws ResourceNotFoundException")
        void removingNonExistentCartItemThrows() {
            when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.removeFromCart(999L, 42L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Carbon Entry Ownership")
    class CarbonEntryOwnership {

        @Mock private CarbonEntryRepository carbonEntryRepository;
        @Mock private EmissionFactorRepository emissionFactorRepository;
        @Mock private UserRepository userRepository;

        @InjectMocks private CarbonService carbonService;

        @Test
        @DisplayName("Owner can delete their own carbon entry")
        void ownerCanDeleteOwnEntry() {
            CarbonEntry entry = CarbonEntry.builder().id(1L).userId(42L).category("transport").type("bus").co2(new java.math.BigDecimal("0.5")).build();
            when(carbonEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

            carbonService.deleteEntry(1L, 42L);

            verify(carbonEntryRepository).deleteByIdAndUserId(1L, 42L);
        }

        @Test
        @DisplayName("User CANNOT delete another user's carbon entry (IDOR)")
        void userCannotDeleteOtherUsersEntry() {
            CarbonEntry entry = CarbonEntry.builder().id(1L).userId(99L).category("transport").type("bus").co2(new java.math.BigDecimal("0.5")).build();
            when(carbonEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

            assertThatThrownBy(() -> carbonService.deleteEntry(1L, 42L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("don't have access");

            // Must NOT delete
            verify(carbonEntryRepository, never()).deleteByIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("Deleting non-existent carbon entry throws ResourceNotFoundException")
        void deletingNonExistentEntryThrows() {
            when(carbonEntryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> carbonService.deleteEntry(999L, 42L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Note Ownership")
    class NoteOwnership {

        @Mock private NoteRepository noteRepository;

        @InjectMocks private NoteService noteService;

        @Test
        @DisplayName("Owner can delete their own note")
        void ownerCanDeleteOwnNote() {
            Note note = Note.builder().id(1L).userId(42L).title("Test").body("Body").tag("tips").build();
            when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

            noteService.deleteNote(1L, 42L);

            verify(noteRepository).deleteByIdAndUserId(1L, 42L);
        }

        @Test
        @DisplayName("User CANNOT delete another user's note (IDOR)")
        void userCannotDeleteOtherUsersNote() {
            Note note = Note.builder().id(1L).userId(99L).title("Secret").body("Private").tag("private").build();
            when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

            assertThatThrownBy(() -> noteService.deleteNote(1L, 42L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("don't have access");

            // Must NOT delete
            verify(noteRepository, never()).deleteByIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("Deleting non-existent note throws ResourceNotFoundException")
        void deletingNonExistentNoteThrows() {
            when(noteRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> noteService.deleteNote(999L, 42L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Shop Resource User-Scoping")
    class ShopResourceUserScoping {

        @Mock private ProductRepository productRepository;
        @Mock private CartItemRepository cartItemRepository;
        @Mock private OrderRepository orderRepository;
        @Mock private OrderItemRepository orderItemRepository;

        @InjectMocks private ShopService shopService;

        @Test
        @DisplayName("getCart only returns items for the authenticated user")
        void getCartReturnsOnlyUserItems() {
            when(cartItemRepository.findByUserId(42L)).thenReturn(java.util.List.of());

            shopService.getCart(42L);

            verify(cartItemRepository).findByUserId(42L);
            // Does NOT call findAll or findByUserId with any other user ID
            verify(cartItemRepository, never()).findByUserId(99L);
        }

        @Test
        @DisplayName("getOrders only returns orders for the authenticated user")
        void getOrdersReturnsOnlyUserOrders() {
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(java.util.List.of());

            shopService.getOrders(42L);

            verify(orderRepository).findByUserIdOrderByCreatedAtDesc(42L);
            verify(orderRepository, never()).findByUserIdOrderByCreatedAtDesc(99L);
        }

        @Test
        @DisplayName("clearCart only clears items for the authenticated user")
        void clearCartOnlyClearsUserItems() {
            shopService.clearCart(42L);

            verify(cartItemRepository).deleteByUserId(42L);
            verify(cartItemRepository, never()).deleteByUserId(99L);
        }

        @Test
        @DisplayName("placeOrder creates order under the authenticated user")
        void placeOrderCreatesOrderForAuthenticatedUser() {
            CartItem cartItem = CartItem.builder().id(1L).userId(42L).productId(10L).quantity(2).build();
            Product product = Product.builder().id(10L).name("Eco Bottle").price(BigDecimal.valueOf(299.99))
                    .status(com.ecoverse.model.ProductStatus.ACTIVE).stock(10).build();

            when(cartItemRepository.findByUserId(42L)).thenReturn(java.util.List.of(cartItem));
            when(productRepository.findAllByIdIn(java.util.List.of(10L))).thenReturn(java.util.List.of(product));
            when(productRepository.decrementStock(10L, 2)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });
            when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OrderResponse response = shopService.placeOrder(42L, "cod", "123 Main St", null);

            // Verify order was saved with the authenticated user's ID
            verify(orderRepository).save(argThat(order ->
                    order.getUserId().equals(42L)
            ));
        }
    }

    @Nested
    @DisplayName("Health Log User-Scoping")
    class HealthLogUserScoping {

        @Mock private HealthLogRepository healthLogRepository;
        @Mock private UserRepository userRepository;
        @Mock private TimezoneService timezoneService;
        @Mock private StreakService streakService;
        @Mock private HealthEntryValidator healthEntryValidator;

        @InjectMocks private HealthService healthService;

        @Test
        @DisplayName("getHealthLogs only returns logs for the authenticated user")
        void getHealthLogsReturnsOnlyUserLogs() {
            User user = new User();
            user.setId(42L);
            user.setTimezone("Asia/Kolkata");
            when(userRepository.findById(42L)).thenReturn(Optional.of(user));

            ZoneId zoneId = ZoneId.of("Asia/Kolkata");
            when(timezoneService.getUserZoneId("Asia/Kolkata")).thenReturn(zoneId);
            when(timezoneService.getPeriodRange(eq("today"), eq(zoneId)))
                    .thenReturn(new java.time.Instant[]{java.time.Instant.now(), java.time.Instant.now()});

            when(healthLogRepository.findByUserIdAndEntryDateBetween(
                    eq(42L), any(java.time.Instant.class), any(java.time.Instant.class), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

            healthService.getHealthLogs(42L, null, "today", 0, 20);

            verify(healthLogRepository).findByUserIdAndEntryDateBetween(
                    eq(42L), any(java.time.Instant.class), any(java.time.Instant.class), any(org.springframework.data.domain.Pageable.class));
            // Does NOT query with any other user ID
            verify(healthLogRepository, never()).findByUserIdAndEntryDateBetween(
                    eq(99L), any(java.time.Instant.class), any(java.time.Instant.class), any(org.springframework.data.domain.Pageable.class));
        }

        @Test
        @DisplayName("getHealthScore only fetches logs for the authenticated user")
        void getHealthScoreOnlyFetchesUserLogs() {
            User user = new User();
            user.setId(42L);
            user.setTimezone("Asia/Kolkata");
            when(userRepository.findById(42L)).thenReturn(Optional.of(user));

            ZoneId zoneId = ZoneId.of("Asia/Kolkata");
            when(timezoneService.getUserZoneId("Asia/Kolkata")).thenReturn(zoneId);
            when(timezoneService.getTodayRange(zoneId))
                    .thenReturn(new java.time.Instant[]{java.time.Instant.now(), java.time.Instant.now()});

            when(healthLogRepository.findByUserIdAndEntryDateBetween(
                    eq(42L), any(java.time.Instant.class), any(java.time.Instant.class)))
                    .thenReturn(java.util.List.of());

            healthService.getHealthScore(42L);

            verify(healthLogRepository).findByUserIdAndEntryDateBetween(
                    eq(42L), any(java.time.Instant.class), any(java.time.Instant.class));
            verify(healthLogRepository, never()).findByUserIdAndEntryDateBetween(
                    eq(99L), any(java.time.Instant.class), any(java.time.Instant.class));
        }

        @Test
        @DisplayName("calculateStreak is scoped to the authenticated user")
        void calculateStreakScopedToUser() {
            User user = new User();
            user.setId(42L);
            user.setTimezone("Asia/Kolkata");
            when(userRepository.findById(42L)).thenReturn(Optional.of(user));

            when(healthLogRepository.calculateCurrentStreak(42L, "Asia/Kolkata")).thenReturn(5);

            Integer streak = healthService.calculateStreak(42L);

            assertThat(streak).isEqualTo(5);
            verify(healthLogRepository).calculateCurrentStreak(42L, "Asia/Kolkata");
            verify(healthLogRepository, never()).calculateCurrentStreak(eq(99L), anyString());
        }

        @Test
        @DisplayName("logHealth creates entry under the authenticated user")
        void logHealthCreatesEntryForAuthenticatedUser() {
            User user = new User();
            user.setId(42L);
            user.setTimezone("Asia/Kolkata");
            when(userRepository.findById(42L)).thenReturn(Optional.of(user));
            when(timezoneService.now()).thenReturn(java.time.Instant.now());

            doNothing().when(healthEntryValidator).validate(any());

            when(healthLogRepository.save(any(HealthLog.class))).thenAnswer(invocation -> {
                HealthLog log = invocation.getArgument(0);
                log.setId(1L);
                return log;
            });

            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(5000).build();
            healthService.logHealth(42L, req);

            verify(healthLogRepository).save(argThat(log ->
                    log.getUserId().equals(42L)
            ));
        }
    }

    // ==================================================================
    // PHASE 4: SHOP IDOR TESTS
    // ==================================================================

    @Nested
    @DisplayName("Product Ownership — Seller IDOR")
    class ProductOwnership {

        @Mock private ProductRepository productRepository;
        @Mock private CartItemRepository cartItemRepository;
        @Mock private OrderRepository orderRepository;
        @Mock private OrderItemRepository orderItemRepository;

        @InjectMocks private ShopService shopService;

        @Test
        @DisplayName("Seller CANNOT update another seller's product (IDOR)")
        void sellerCannotUpdateAnotherSellersProduct() {
            Product product = Product.builder().id(10L).sellerId(99L).name("Other's Product")
                    .price(BigDecimal.valueOf(299)).status(ProductStatus.ACTIVE).stock(10).build();
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            var req = new com.ecoverse.dto.shop.ProductRequest();
            req.setName("Hacked");

            assertThatThrownBy(() -> shopService.updateProduct(42L, 10L, req))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("own products");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Seller CANNOT delete another seller's product (IDOR)")
        void sellerCannotDeleteAnotherSellersProduct() {
            Product product = Product.builder().id(10L).sellerId(99L).name("Other's Product")
                    .price(BigDecimal.valueOf(299)).status(ProductStatus.ACTIVE).stock(10).build();
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> shopService.deleteProduct(42L, 10L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("own products");

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Order Ownership — IDOR Protection")
    class OrderOwnershipIdor {

        @Mock private ProductRepository productRepository;
        @Mock private CartItemRepository cartItemRepository;
        @Mock private OrderRepository orderRepository;
        @Mock private OrderItemRepository orderItemRepository;

        @InjectMocks private ShopService shopService;

        @Test
        @DisplayName("User A CANNOT read User B's order (IDOR)")
        void userCannotReadAnotherUsersOrder() {
            when(orderRepository.findByIdAndUserId(1L, 42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.getOrder(42L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("User A CANNOT cancel User B's order (IDOR)")
        void userCannotCancelAnotherUsersOrder() {
            when(orderRepository.findByIdAndUserId(1L, 42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shopService.updateOrderStatus(42L, 1L, Order.OrderStatus.CANCELLED))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(orderRepository, never()).save(any());
        }
    }
}
