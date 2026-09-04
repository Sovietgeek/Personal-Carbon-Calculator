package com.ecoverse.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Refund request DTO.
 * Amount is optional — if null, full refund is processed.
 * Amount is validated server-side — never trusts client for maximum refundable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    /**
     * Refund amount. If null, the entire remaining balance is refunded.
     * Server validates this doesn't exceed the refundable amount.
     */
    @Positive(message = "Refund amount must be positive")
    private BigDecimal amount;

    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
