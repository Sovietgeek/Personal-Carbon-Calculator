package com.ecoverse.repository;

import com.ecoverse.model.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<PaymentAttempt> findByProviderOrderId(String providerOrderId);

    Optional<PaymentAttempt> findTopByOrderIdAndStatusOrderByCreatedAtDesc(Long orderId, String status);

    List<PaymentAttempt> findByOrderId(Long orderId);

    /**
     * Find the latest payment attempt for an order.
     */
    Optional<PaymentAttempt> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
}
