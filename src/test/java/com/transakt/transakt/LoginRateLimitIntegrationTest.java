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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Brute-force protection on POST /api/v1/auth/login.
 *
 * The per-merchant limiter cannot cover this endpoint: it counts by merchant id,
 * and the whole point of attacking login is not having a credential yet. So login
 * is counted by CLIENT ADDRESS instead, before authentication.
 *
 * @TestPropertySource forces a limit of three, which also forces Spring to build a
 * SEPARATE application context for this class — the same trick RateLimitIntegrationTest
 * uses. Without it, the other twelve test classes that call login would share this
 * ceiling and the suite would collapse into 429s.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "ratelimit.login-attempts-per-minute=3")
class LoginRateLimitIntegrationTest {

    private static final String PASSWORD = "hunter2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    /** @Transactional rolls back Postgres, never Redis. Isolation is per store. */
    @BeforeEach
    void flushRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private String json(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    private void attemptLogin(String email, String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().is(expectedStatus));
    }

    /**
     * Note that every attempt here FAILS authentication, and every one still counts.
     * That is the point: the filter runs before the controller, so it limits
     * attempts rather than successes. A limiter that only counted successful logins
     * would be no obstacle to guessing passwords at all.
     */
    @Test
    void repeatedLoginAttemptsFromOneAddressAreRefused() throws Exception {
        for (int i = 0; i < 3; i++) {
            attemptLogin("nobody@shop.com", "wrong-password", 401);
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@shop.com", "password", "wrong-password"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    /**
     * The two limiters use different Redis keys, so exhausting one must not touch
     * the other. The token is taken FIRST, because obtaining it costs a login.
     */
    @Test
    void exhaustingTheLoginLimitDoesNotBlockAuthenticatedTraffic() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Shop", "email", "login-limit@shop.com",
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "login-limit@shop.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asText();

        for (int i = 0; i < 3; i++) {
            attemptLogin("nobody@shop.com", "wrong-password", 401);
        }
        attemptLogin("nobody@shop.com", "wrong-password", 429);

        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
