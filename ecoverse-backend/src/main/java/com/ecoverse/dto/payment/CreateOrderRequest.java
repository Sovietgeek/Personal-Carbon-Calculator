package com.ecoverse.dto.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @Valid
    private List<CartItem> items;

    @Valid
    @NotNull(message = "Shipping address is required")
    private ShippingAddress shippingAddress;

    @NotBlank(message = "Payment method is required")
    @Size(min = 1, max = 20, message = "Payment method must be between 1 and 20 characters")
    private String paymentMethod;  // "card", "upi", "cod"

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CartItem {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShippingAddress {
        @NotBlank(message = "Full name is required")
        @Size(min = 1, max = 100, message = "Full name must be between 1 and 100 characters")
        private String fullName;

        @Size(max = 20, message = "Phone must be at most 20 characters")
        private String phone;

        @NotBlank(message = "Address line 1 is required")
        @Size(min = 1, max = 500, message = "Address line 1 must be between 1 and 500 characters")
        private String addressLine1;

        @Size(max = 500, message = "Address line 2 must be at most 500 characters")
        private String addressLine2;

        @NotBlank(message = "City is required")
        @Size(min = 1, max = 100, message = "City must be between 1 and 100 characters")
        private String city;

        @NotBlank(message = "State is required")
        @Size(min = 1, max = 100, message = "State must be between 1 and 100 characters")
        private String state;

        @NotBlank(message = "Pincode is required")
        @Size(min = 1, max = 20, message = "Pincode must be between 1 and 20 characters")
        private String pincode;

        @Size(max = 100, message = "Country must be at most 100 characters")
        private String country;
    }
}
