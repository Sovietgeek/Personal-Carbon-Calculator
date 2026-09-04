package com.ecoverse.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCallbackRequest {

    @NotBlank(message = "Razorpay order ID is required")
    @Size(min = 1, max = 100, message = "Razorpay order ID must be between 1 and 100 characters")
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay payment ID is required")
    @Size(min = 1, max = 100, message = "Razorpay payment ID must be between 1 and 100 characters")
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay signature is required")
    @Size(min = 1, max = 200, message = "Razorpay signature must be between 1 and 200 characters")
    private String razorpaySignature;
}
