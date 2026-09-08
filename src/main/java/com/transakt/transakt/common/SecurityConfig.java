package com.transakt.transakt.common;

import com.transakt.transakt.auth.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final ApiKeyFilter apiKeyFilter;
    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(ApiKeyFilter apiKeyFilter,
                          JwtAuthFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter) {
        this.apiKeyFilter = apiKeyFilter;
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchants").permitAll()
                        // ABOVE the ADMIN rule, and it has to be. authorizeHttpRequests
                        // is first-match-wins, so putting this line after the next one
                        // would route every merchant editing its own webhook into the
                        // ADMIN check and return a silent 403.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/merchants/me").authenticated()
                        .requestMatchers("/api/v1/merchants/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, ApiKeyFilter.class)
                .addFilterAfter(rateLimitFilter, ApiKeyFilter.class);

        return http.build();
    }
}