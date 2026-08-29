package com.transakt.transakt.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant createdAt);
    Page<Payment> findByMerchantId(String merchantId, Pageable pageable);
}