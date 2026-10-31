package com.transakt.transakt.common;

import com.transakt.transakt.merchant.Merchant;
import com.transakt.transakt.merchant.MerchantRole;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cache must never be the reason a request fails: an unreachable Redis is a miss,
 * and a value that no longer parses is a miss that cleans up after itself.
 */
class ApiKeyCacheTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ApiKeyCache cache = new ApiKeyCache(redis, 300);

    ApiKeyCacheTest() {
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void anUnreachableRedisIsAMissNotAnError() {
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertThat(cache.get("tk_12345")).isEmpty();
    }

    @Test
    void cachingAndEvictingDoNotThrowWhenRedisIsUnreachable() {
        doThrow(new RedisConnectionFailureException("Redis is down"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertThatCode(() -> cache.put(merchant())).doesNotThrowAnyException();
        assertThatCode(() -> cache.evict("tk_12345")).doesNotThrowAnyException();
    }

    @Test
    void storesIdRoleAndHashUnderThePrefixWithTheTtl() {
        cache.put(merchant());

        verify(ops).set("apikey:tk_12345", "merchant-1|MERCHANT|" + "a".repeat(64), Duration.ofSeconds(300));
    }

    @Test
    void readsBackWhatItStored() {
        when(ops.get("apikey:tk_12345")).thenReturn("merchant-1|ADMIN|" + "b".repeat(64));

        assertThat(cache.get("tk_12345")).hasValueSatisfying(p -> {
            assertThat(p.merchantId()).isEqualTo("merchant-1");
            assertThat(p.role()).isEqualTo(MerchantRole.ADMIN);
            assertThat(p.apiKeyHash()).isEqualTo("b".repeat(64));
        });
    }

    @Test
    void aValueThatDoesNotParseIsAMissAndIsRemoved() {
        when(ops.get("apikey:tk_12345")).thenReturn("merchant-1|NOT_A_ROLE|hash");

        assertThat(cache.get("tk_12345")).isEmpty();
        verify(redis).delete("apikey:tk_12345");
    }

    private static Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId("merchant-1");
        merchant.setRole(MerchantRole.MERCHANT);
        merchant.setApiKeyPrefix("tk_12345");
        merchant.setApiKeyHash("a".repeat(64));
        return merchant;
    }
}
