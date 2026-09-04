package com.ecoverse.repository;

import com.ecoverse.model.PaymentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByProviderEventId(String providerEventId);

    Page<PaymentEvent> findByOrderIdOrderByCreatedAtDesc(Long orderId, Pageable pageable);

    Page<PaymentEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByProviderEventId(String providerEventId);
}
