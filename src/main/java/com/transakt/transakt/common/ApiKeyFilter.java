package com.transakt.transakt.common;

import com.transakt.transakt.common.ApiKeyCache.CachedPrincipal;
import com.transakt.transakt.merchant.Merchant;
import com.transakt.transakt.merchant.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final MerchantRepository merchantRepository;
    private final ApiKeyCache apiKeyCache;

    public ApiKeyFilter(MerchantRepository merchantRepository, ApiKeyCache apiKeyCache) {
        this.merchantRepository = merchantRepository;
        this.apiKeyCache = apiKeyCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-Key");

        if (apiKey != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String prefix = ApiKeyHasher.prefixOf(apiKey);

            if (prefix != null) {
                Optional<CachedPrincipal> principal = lookUp(prefix);

                // The comparison runs on every request, cached or not: the cache only
                // replaces the database lookup, so a wrong key with a real prefix still fails.
                if (principal.isPresent()
                        && ApiKeyHasher.matches(apiKey, principal.get().apiKeyHash())) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal.get().merchantId(),
                            null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_" + principal.get().role().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Cache first, database on a miss. A miss is cached only when the merchant exists:
     * caching "no such prefix" would let a key minted a moment later be refused until
     * the negative entry expired.
     */
    private Optional<CachedPrincipal> lookUp(String prefix) {
        Optional<CachedPrincipal> cached = apiKeyCache.get(prefix);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Merchant> merchant = merchantRepository.findByApiKeyPrefix(prefix);
        merchant.ifPresent(apiKeyCache::put);
        return merchant.map(m -> new CachedPrincipal(m.getId(), m.getRole(), m.getApiKeyHash()));
    }
}
