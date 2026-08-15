package com.transakt.transakt.common;

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

    public ApiKeyFilter(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
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
                Optional<Merchant> merchant = merchantRepository.findByApiKeyPrefix(prefix);

                if (merchant.isPresent()
                        && ApiKeyHasher.matches(apiKey, merchant.get().getApiKeyHash())) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            merchant.get().getId(),
                            null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_" + merchant.get().getRole().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}