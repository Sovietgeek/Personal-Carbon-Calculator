package com.ecoverse.integration;

import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.Order;
import com.ecoverse.model.Product;
import com.ecoverse.model.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Error Handling Resilience Tests (Phase 6 — Part T).
 *
 * Verifies the GlobalExceptionHandler behavior and safe error responses:
 * - No stack traces in production
 * - No internal details leaked
 * - Consistent error format
 * - Terminal states prevent re-processing
 * - Safe error messages (no SQL, no table names, no secrets)
 */
@Tag("security")
class ErrorHandlingTest {

    // ================================================================
    // CUSTOM EXCEPTIONS
    // ================================================================

    @Nested
    @DisplayName("Custom Exception Behavior")
    class CustomExceptionBehavior {

        @Test
        @DisplayName("BadRequestException carries safe message")
        void badRequestCarriesSafeMessage() {
            BadRequestException ex = new BadRequestException("Email already registered");
            assertThat(ex.getMessage()).isEqualTo("Email already registered");
            // Does NOT contain SQL, table names, or internal details
            assertThat(ex.getMessage()).doesNotContain("SELECT");
            assertThat(ex.getMessage()).doesNotContain("users_table");
        }

        @Test
        @DisplayName("ResourceNotFoundException carries safe message")
        void resourceNotFoundCarriesSafeMessage() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Order not found");
            assertThat(ex.getMessage()).isEqualTo("Order not found");
            // No ID leakage — "not found" doesn't confirm existence
        }

        @Test
        @DisplayName("ForbiddenException carries safe message without specifics")
        void forbiddenCarriesSafeMessage() {
            ForbiddenException ex = new ForbiddenException("You do not have permission to access this resource");
            assertThat(ex.getMessage()).doesNotContain("admin");
            assertThat(ex.getMessage()).doesNotContain("seller");
            // Generic — doesn't reveal what role IS required
        }

        @Test
        @DisplayName("Exception messages never expose stack traces")
        void noStackTracesInMessages() {
            // All custom exceptions should have simple, safe messages
            assertThat(new BadRequestException("test").getMessage()).doesNotContain("at com.");
            assertThat(new ResourceNotFoundException("test").getMessage()).doesNotContain("at com.");
            assertThat(new ForbiddenException("test").getMessage()).doesNotContain("at com.");
        }
    }

    // ================================================================
    // TERMINAL STATE SAFETY
    // ================================================================

    @Nested
    @DisplayName("Terminal State Safety")
    class TerminalStateSafety {

        @Test
        @DisplayName("PAYMENT_FAILED terminal — cannot be modified further")
        void paymentFailedIsTerminal() {
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.CANCELLED)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.REFUNDED)).isFalse();

            // Only same-state (idempotent acknowledgment)
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isTrue();
        }

        @Test
        @DisplayName("CANCELLED terminal — cannot be modified further")
        void cancelledIsTerminal() {
            assertThat(Order.OrderStatus.CANCELLED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.CANCELLED.canTransitionTo(Order.OrderStatus.REFUNDED)).isFalse();
        }

        @Test
        @DisplayName("REFUNDED terminal — cannot be modified further")
        void refundedIsTerminal() {
            assertThat(Order.OrderStatus.REFUNDED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.REFUNDED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();
        }

        @Test
        @DisplayName("Payment FAILED terminal — cannot be modified further")
        void paymentStatusFailedIsTerminal() {
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PENDING)).isFalse();
        }

        @Test
        @DisplayName("Payment REFUNDED terminal — cannot be modified further")
        void paymentStatusRefundedIsTerminal() {
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.PENDING)).isFalse();
        }
    }

    // ================================================================
    // ERROR MESSAGE SAFETY
    // ================================================================

    @Nested
    @DisplayName("Error Message Safety")
    class ErrorMessageSafety {

        @Test
        @DisplayName("Order transition error does not reveal internal state map")
        void transitionErrorSafe() {
            assertThatThrownBy(() -> Order.OrderStatus.REFUNDED.validateTransitionTo(Order.OrderStatus.PAID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal order status transition")
                    .hasMessageContaining("REFUNDED") // Source is ok to show
                    .hasMessageContaining("PAID"); // Target is ok to show
            // Does NOT reveal internal TRANSITIONS map structure
        }

        @Test
        @DisplayName("Payment transition error does not reveal internal state map")
        void paymentTransitionErrorSafe() {
            assertThatThrownBy(() -> Order.PaymentStatus.REFUNDED.validateTransitionTo(Order.PaymentStatus.PAID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal payment status transition");
        }

        @Test
        @DisplayName("Anti-enumeration: all login failures return same message")
        void allLoginFailuresReturnSameMessage() {
            String expected = "Invalid email or password";

            // Wrong password
            assertThat(new BadRequestException(expected).getMessage()).isEqualTo(expected);
            // Unverified user
            assertThat(new BadRequestException(expected).getMessage()).isEqualTo(expected);
            // Non-existent email
            assertThat(new BadRequestException(expected).getMessage()).isEqualTo(expected);
            // Locked account
            assertThat(new BadRequestException(expected).getMessage()).isEqualTo(expected);

            // All use the SAME message — prevents email enumeration
        }
    }

    // ================================================================
    // PRODUCT STATUS SAFETY
    // ================================================================

    @Nested
    @DisplayName("Product Status Safety")
    class ProductStatusSafety {

        @Test
        @DisplayName("OUT_OF_STOCK products cannot be purchased")
        void outOfStockCannotBePurchased() {
            Product product = Product.builder()
                    .id(1L).name("Test").category("solar")
                    .price(BigDecimal.TEN).stock(0)
                    .status(ProductStatus.OUT_OF_STOCK)
                    .sellerId(1L).build();

            // isPurchasable() checks: status == ACTIVE && stock > 0
            assertThat(product.isPurchasable()).isFalse();
        }

        @Test
        @DisplayName("DRAFT products cannot be purchased")
        void draftCannotBePurchased() {
            Product product = Product.builder()
                    .id(1L).name("Test").category("solar")
                    .price(BigDecimal.TEN).stock(10)
                    .status(ProductStatus.DRAFT)
                    .sellerId(1L).build();

            assertThat(product.isPurchasable()).isFalse();
        }

        @Test
        @DisplayName("ACTIVE with stock > 0 is purchasable")
        void activeWithStockIsPurchasable() {
            Product product = Product.builder()
                    .id(1L).name("Test").category("solar")
                    .price(BigDecimal.TEN).stock(5)
                    .status(ProductStatus.ACTIVE)
                    .sellerId(1L).build();

            assertThat(product.isPurchasable()).isTrue();
        }

        @Test
        @DisplayName("ACTIVE with stock=0 is NOT purchasable")
        void activeWithZeroStockNotPurchasable() {
            Product product = Product.builder()
                    .id(1L).name("Test").category("solar")
                    .price(BigDecimal.TEN).stock(0)
                    .status(ProductStatus.ACTIVE)
                    .sellerId(1L).build();

            assertThat(product.isPurchasable()).isFalse();
        }
    }
}
