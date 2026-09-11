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

    private static final String LOGIN_PATH = "/api/v1/auth/login";

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

        if (auth != null) {
            if (!rateLimitService.isAllowed(auth.getName())) {
                reject(response);
                return;
            }
        } else if (LOGIN_PATH.equals(request.getRequestURI())) {
            // The one endpoint that has to be limited WITHOUT a credential, because
            // the point of attacking it is not having one yet. Counted per client
            // address so that one attacker cannot lock every merchant out.
            if (!rateLimitService.isLoginAllowed(clientIp(request))) {
                reject(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Which address to count.
     *
     * getRemoteAddr() is the PROXY's address behind a load balancer, so using it
     * unguarded in production would treat the entire internet as one client and lock
     * everybody out after five logins a minute. Render sits behind Cloudflare, and
     * the header to trust there is CF-Connecting-IP, which Cloudflare OVERWRITES —
     * unlike X-Forwarded-For, which is appended to, so its leftmost entry is whatever
     * the caller claimed and is trivially forged.
     *
     * The trust boundary is explicit and worth stating: this is only as trustworthy
     * as the proxy in front of it. Exposed without one, the header must be ignored,
     * because anyone could then set it to a fresh value on every request and evade
     * the limit entirely.
     */
    private String clientIp(HttpServletRequest request) {
        String cloudflare = request.getHeader("CF-Connecting-IP");
        if (cloudflare != null && !cloudflare.isBlank()) {
            return cloudflare;
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response) throws IOException {
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
    }
}