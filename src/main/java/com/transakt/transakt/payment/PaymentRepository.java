package com.transakt.transakt.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Page<Payment> findByMerchantId(String merchantId, Pageable pageable);
}