package com.transakt.transakt.common;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    public Optional<String> findPaymentId(String merchantId, String idempotencyKey) {
        return repository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .map(IdempotencyKey::getPaymentId);
    }
}