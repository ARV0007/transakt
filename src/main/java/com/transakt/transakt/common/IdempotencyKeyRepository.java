package com.transakt.transakt.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    /**
     * Restores the expiry that Redis used to provide for free. Deleting a key means
     * the same key is honoured again as a NEW payment, which is the intended
     * semantics: an idempotency key protects against a retry storm, not forever.
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}