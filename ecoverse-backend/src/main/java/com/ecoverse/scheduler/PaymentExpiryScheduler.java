package com.ecoverse.scheduler;

import com.ecoverse.config.RazorpayConfig;
import com.ecoverse.model.Order;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that expires abandoned PENDING_PAYMENT orders.
 *
 * DESIGN:
 * - Runs every 5 minutes
 * - Finds PENDING_PAYMENT orders with online payment methods older than the configured TTL
 * - COD orders are NOT expired (they stay PENDING_PAYMENT until delivery or manual cancel)
 * - Sets order to PAYMENT_FAILED + restores stock
 * - Idempotent: skips orders already in terminal state
 *
 * STOCK SAFETY:
 * - Stock was decremented at order creation time
 * - If payment is never completed, stock remains locked forever without this scheduler
 * - Expired orders release stock back to the inventory
 */
@Component
public class PaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RazorpayConfig razorpayConfig;

    /**
     * Run every 5 minutes to check for expired PENDING_PAYMENT orders.
     * Uses fixedRate to ensure consistent scheduling regardless of execution duration.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void expireAbandonedPayments() {
        int expiryMinutes = razorpayConfig.getPaymentExpiryMinutes();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expiryMinutes);

        // Find PENDING_PAYMENT orders with online payment methods that are older than the TTL
        // Exclude COD orders — they should remain PENDING_PAYMENT until delivery or manual cancel
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(
                Order.OrderStatus.PENDING_PAYMENT,
                List.of("cod"),
                cutoff
        );

        if (expiredOrders.isEmpty()) {
            return; // Nothing to expire
        }

        log.info("Found {} expired PENDING_PAYMENT orders (older than {} minutes)", expiredOrders.size(), expiryMinutes);

        int expired = 0;
        int failed = 0;
        for (Order order : expiredOrders) {
            try {
                paymentService.expirePayment(order);
                expired++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to expire order {}: {}", order.getId(), e.getMessage());
            }
        }

        log.info("Payment expiry sweep complete: {} expired, {} failed", expired, failed);
    }
}
