package com.transakt.transakt.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IdempotencyService {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String buildKey(String merchantId, String idempotencyKey) {
        return "idem:" + merchantId + ":" + idempotencyKey;
    }

    public boolean tryReserve(String merchantId, String idempotencyKey) {
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(buildKey(merchantId, idempotencyKey), IN_PROGRESS, TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public String getStoredValue(String merchantId, String idempotencyKey) {
        return redis.opsForValue().get(buildKey(merchantId, idempotencyKey));
    }

    public void storeResult(String merchantId, String idempotencyKey, String paymentId) {
        redis.opsForValue().set(buildKey(merchantId, idempotencyKey), paymentId, TTL);
    }

    public void release(String merchantId, String idempotencyKey) {
        redis.delete(buildKey(merchantId, idempotencyKey));
    }
}