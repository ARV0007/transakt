package com.transakt.transakt.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && !rateLimitService.isAllowed(auth.getName())) {
            long retryAfter = rateLimitService.secondsUntilReset();

            // Retry-After is what turns a 429 from "you got this wrong" into "wait
            // this long". 429 and 403 are both refusals but they mean opposite
            // things: a client that treats 429 like 403 abandons a request that
            // would have succeeded, and one given no guidance at all retries
            // immediately and deepens the overload. RFC 9110 allows either a
            // seconds count or an HTTP date; seconds is simpler and immune to clock
            // skew between this server and the caller.
            //
            // Written by hand because filters run before the DispatcherServlet, so
            // @RestControllerAdvice cannot reach anything thrown from here.
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded. Try again in " + retryAfter + " seconds.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}