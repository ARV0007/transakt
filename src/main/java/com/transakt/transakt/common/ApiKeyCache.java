package com.transakt.transakt.common;

import com.transakt.transakt.merchant.Merchant;
import com.transakt.transakt.merchant.MerchantRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Caches what ApiKeyFilter needs to authenticate a merchant, keyed by the API key's
 * public prefix, so a request with an API key no longer costs a database query.
 *
 * WHAT IS CACHED: the merchant id, the role and the SHA-256 hash of the key - the
 * three fields authentication reads. Never the key itself. The filter still compares
 * the presented key against the cached hash in constant time, so the cache replaces
 * the LOOKUP, not the check: a wrong key with a valid prefix is still refused.
 *
 * WHEN IT GOES STALE: the hash is written once at signup and nothing changes it, and
 * no endpoint changes a role. Deleting a merchant does change who may authenticate,
 * so MerchantService.delete evicts the entry. A role changed by hand in psql (which is
 * how an admin is made) takes effect within the TTL, not instantly - the TTL is short
 * for that reason.
 *
 * FAILS OPEN, like RateLimitService and for the same reason: Redis is an optimisation
 * here, not a dependency. If it is unreachable, every lookup is a miss and the filter
 * goes to the database exactly as it did before this class existed. A cache outage
 * costs latency, never availability.
 */
@Slf4j
@Service
public class ApiKeyCache {

    private static final String KEY_PREFIX = "apikey:";
    private static final String SEPARATOR = "|";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public ApiKeyCache(StringRedisTemplate redis,
                       @Value("${apikey-cache.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /** What the filter needs to authenticate a request, without the database. */
    public record CachedPrincipal(String merchantId, MerchantRole role, String apiKeyHash) {
    }

    public Optional<CachedPrincipal> get(String apiKeyPrefix) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + apiKeyPrefix);
            return value == null ? Optional.empty() : parse(apiKeyPrefix, value);
        } catch (RedisConnectionFailureException e) {
            log.warn("API key cache unavailable - Redis unreachable. Falling back to the database.");
            return Optional.empty();
        }
    }

    public void put(Merchant merchant) {
        String value = merchant.getId() + SEPARATOR + merchant.getRole().name() + SEPARATOR + merchant.getApiKeyHash();
        try {
            redis.opsForValue().set(KEY_PREFIX + merchant.getApiKeyPrefix(), value, ttl);
        } catch (RedisConnectionFailureException e) {
            log.warn("API key cache unavailable - Redis unreachable. Not caching merchant {}", merchant.getId());
        }
    }

    public void evict(String apiKeyPrefix) {
        if (apiKeyPrefix == null) {
            return;
        }
        try {
            redis.delete(KEY_PREFIX + apiKeyPrefix);
        } catch (RedisConnectionFailureException e) {
            // Nothing could have been served from an unreachable Redis either, but an
            // entry written before the outage survives until its TTL. Logged loudly
            // because that is the one case where the cache can briefly be wrong.
            log.error("Could not evict API key cache entry {} - Redis unreachable. It expires within {}s.",
                    apiKeyPrefix, ttl.toSeconds());
        }
    }

    /**
     * A value that does not parse is treated as a miss and removed, so a format change
     * in a later version can never lock a merchant out - the next request repopulates it.
     */
    private Optional<CachedPrincipal> parse(String apiKeyPrefix, String value) {
        String[] parts = value.split("\\|", 3);
        try {
            if (parts.length == 3) {
                return Optional.of(new CachedPrincipal(parts[0], MerchantRole.valueOf(parts[1]), parts[2]));
            }
        } catch (IllegalArgumentException ignored) {
            // unknown role name - fall through and evict
        }
        evict(apiKeyPrefix);
        return Optional.empty();
    }
}
