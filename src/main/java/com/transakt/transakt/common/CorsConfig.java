package com.transakt.transakt.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Lets the demo page on the portfolio site call this API from a browser.
 *
 * A browser blocks a page on one origin from reading responses from another unless
 * the server says that origin is allowed. curl, Postman and other servers never ask,
 * so this changes nothing for them: CORS is a rule browsers apply, not a lock on
 * the API. Credentials here travel in headers (X-API-Key, Authorization), never in
 * cookies, so allowing an origin does not let it act as anyone who has not handed
 * it a key.
 *
 * The list is exact origins, not "*", so a page has to be on this list to call the
 * API from a browser at all. Set CORS_ALLOWED_ORIGINS to a comma-separated list to
 * change it; an empty value allows none.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:}") List<String> allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-API-Key", "Idempotency-Key"));
        // Without this the page can see a 429 but not how long to wait.
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(false);
        // Browsers cache a successful preflight for this long instead of asking before every call.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
