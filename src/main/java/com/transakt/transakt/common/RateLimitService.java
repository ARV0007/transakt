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
    private final int maxLoginAttemptsPerMinute;

    public RateLimitService(StringRedisTemplate redis,
                            @Value("${ratelimit.requests-per-minute:20}") int maxRequestsPerMinute,
                            @Value("${ratelimit.login-attempts-per-minute:5}") int maxLoginAttemptsPerMinute) {
        this.redis = redis;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxLoginAttemptsPerMinute = maxLoginAttemptsPerMinute;
    }

    /**
     * Brute-force protection for POST /api/v1/auth/login, counted by CLIENT ADDRESS
     * rather than by merchant.
     *
     * The per-merchant limiter cannot cover login, because it keys on a merchant id
     * that only exists once you are authenticated — and the whole point of attacking
     * login is not being authenticated yet. Until this existed, passwords could be
     * guessed as fast as the network allowed.
     *
     * A separate key prefix and a separate ceiling on purpose: five guesses a minute
     * is generous for a human typing a password and useless for a script, whereas
     * the twenty-a-minute API ceiling would be neither.
     *
     * Fails OPEN, like its sibling, and the reasoning is the same question applied
     * to a different case: failing closed would mean a Redis blip locks every human
     * out of the dashboard entirely. Losing brute-force protection for a few minutes
     * is the cheaper failure, especially against BCrypt at cost 10.
     */
    public boolean isLoginAllowed(String clientIp) {
        long currentMinute = System.currentTimeMillis() / 60000;
        String key = "rate:login:" + clientIp + ":" + currentMinute;

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            return count != null && count <= maxLoginAttemptsPerMinute;
        } catch (RedisConnectionFailureException e) {
            log.warn("Login rate limiting unavailable — Redis unreachable. Failing open for {}", clientIp);
            return true;
        }
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

    /**
     * Seconds until the current window rolls over, for the Retry-After header.
     *
     * Rounded UP deliberately. Telling a client to wait 0 seconds when 400ms remain
     * sends it straight back into the same window and straight back into a 429 — a
     * Retry-After that is too short is worse than none at all, because a client that
     * trusts it retries in a tight loop and makes the overload worse.
     *
     * This lives here rather than in the filter because the window boundary is this
     * class's concept. If the algorithm ever becomes a sliding window or a token
     * bucket, the filter should not have to change with it.
     */
    public long secondsUntilReset() {
        long windowMillis = WINDOW.toMillis();
        long millisIntoWindow = System.currentTimeMillis() % windowMillis;
        return (windowMillis - millisIntoWindow + 999) / 1000;
    }
}
