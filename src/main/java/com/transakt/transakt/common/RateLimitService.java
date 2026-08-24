package com.transakt.transakt.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;


@Slf4j
@Service

public class RateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final int maxRequestsPerMinute;

    public RateLimitService(StringRedisTemplate redis,
                            @Value("${ratelimit.requests-per-minute:20}") int maxRequestsPerMinute) {
        this.redis = redis;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public boolean isAllowed(String merchantId) {
        long currentMinute = System.currentTimeMillis() / 60000;
        String key = "rate:" + merchantId + ":" + currentMinute;

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            return count != null && count <= maxRequestsPerMinute;
        } catch (RedisConnectionFailureException e) {
            log.warn("Rate limiting unavailable — Redis unreachable. Failing open for merchant {}", merchantId);
            return true;
        }
    }
}
