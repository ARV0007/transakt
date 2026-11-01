package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which browser pages may call the API. Browsers send a preflight OPTIONS before any
 * request that carries a custom header such as X-API-Key, and only go ahead if the
 * answer names their origin.
 *
 * The login limit is set to three here so the last test can show that preflights are
 * not counted as login attempts. It also gives this class its own application context,
 * the same way LoginRateLimitIntegrationTest does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "cors.allowed-origins=https://arv0007.github.io",
        "ratelimit.login-attempts-per-minute=3"
})
class CorsIntegrationTest {

    private static final String PORTFOLIO = "https://arv0007.github.io";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    /** @Transactional rolls back Postgres, never Redis. */
    @BeforeEach
    void flushRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void thePortfolioMayCreateAPaymentWithAnApiKeyFromABrowser() throws Exception {
        mockMvc.perform(options("/api/v1/payments")
                        .header("Origin", PORTFOLIO)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-api-key,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PORTFOLIO))
                .andExpect(header().string("Access-Control-Allow-Methods", containsStringIgnoringCase("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("x-api-key")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("idempotency-key")));
    }

    @Test
    void anyOtherSiteIsRefusedAtThePreflight() throws Exception {
        mockMvc.perform(options("/api/v1/payments")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-api-key"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void aSimpleRequestFromThePortfolioCanReadTheResponse() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("Origin", PORTFOLIO))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PORTFOLIO));
    }

    /**
     * CORS is a browser rule, not authentication. An allowed origin still needs a key:
     * the browser is allowed to read the 403, nothing more.
     */
    @Test
    void anAllowedOriginWithoutAKeyIsStillRefused() throws Exception {
        mockMvc.perform(get("/api/v1/payments").header("Origin", PORTFOLIO))
                .andExpect(status().isForbidden());
    }

    /**
     * Every login from a browser on another origin is preceded by a preflight. If the
     * login limiter counted those, a visitor would be locked out after half the attempts.
     */
    @Test
    void preflightsDoNotUseUpLoginAttempts() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header("Origin", PORTFOLIO)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Origin", PORTFOLIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "nobody@shop.com", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }
}
