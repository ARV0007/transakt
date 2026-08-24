package com.transakt.transakt.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    @Test
    void requestsAreAllowedWhenRedisIsUnreachable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        RateLimitService service = new RateLimitService(redis, 20);

        assertThat(service.isAllowed("merchant-123")).isTrue();
    }
}