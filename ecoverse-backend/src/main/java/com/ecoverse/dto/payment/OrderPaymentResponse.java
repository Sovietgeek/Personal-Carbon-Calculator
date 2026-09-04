package com.ecoverse.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPaymentResponse {
    private String razorpayOrderId;     // Razorpay order ID
    private String razorpayPaymentId;   // Payment ID (after successful payment)
    private String currency;            // INR
    private Integer amount;             // Amount in paise (₹100 = 10000)
    private String key;                 // Razorpay key ID (for frontend)
    private String status;              // created, paid, failed
    private String ecoverseOrderId;     // Our internal order ID
    private Double carbonSaved;         // Total CO2 saved by this order
    private String message;             // Status message
}
