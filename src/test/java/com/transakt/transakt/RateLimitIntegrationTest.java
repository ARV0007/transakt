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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ratelimit.requests-per-minute=5")
@Transactional
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private String json(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    private String tokenFor(String email) throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Shop",
                                "email", email,
                                "password", "hunter2"))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "hunter2"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void requestsAreRefusedPastTheLimit() throws Exception {
        String token = tokenFor("rate-limited@shop.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/payments")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * A 429 with no Retry-After tells a client to stop but not when it may resume,
     * so a well-behaved client either gives up or guesses. The value is a seconds
     * count and must never be zero — a client that trusts a zero retries straight
     * back into the same window.
     */
    @Test
    void aRefusedRequestSaysWhenToRetry() throws Exception {
        String token = tokenFor("rate-retry-after@shop.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/payments")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        String retryAfter = mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse().getHeader("Retry-After");

        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
    }

    @Test
    void oneMerchantHittingTheLimitDoesNotAffectAnother() throws Exception {
        String noisy = tokenFor("rate-noisy@shop.com");
        String quiet = tokenFor("rate-quiet@shop.com");

        for (int i = 0; i < 6; i++) {
            mockMvc.perform(get("/api/v1/payments")
                    .header("Authorization", "Bearer " + noisy));
        }

        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + quiet))
                .andExpect(status().isOk());
    }
}