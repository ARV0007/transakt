package com.transakt.transakt.common;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);
}