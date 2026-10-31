package com.transakt.transakt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transakt.transakt.merchant.Merchant;
import com.transakt.transakt.merchant.MerchantRepository;
import com.transakt.transakt.merchant.MerchantRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API key cache, through the real filter chain against real Postgres and Redis.
 *
 * Proving a request was served FROM THE CACHE is the hard part, because a cache hit and a
 * database hit look identical from outside. So these tests break the database copy after
 * the first request: if the second request still authenticates, the database was not
 * asked. If it is refused, the cache was skipped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiKeyCacheIntegrationTest {

    private static final String PASSWORD = "hunter2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private StringRedisTemplate redis;

    /** @Transactional rolls back Postgres, not Redis - isolation is per store. */
    @BeforeEach
    void clearRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private JsonNode signUp(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Shop", "email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String adminToken(String email) throws Exception {
        String id = signUp(email).get("id").asText();
        Merchant admin = merchantRepository.findById(id).orElseThrow();
        admin.setRole(MerchantRole.ADMIN);
        merchantRepository.saveAndFlush(admin);

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void aSecondRequestWithTheSameKeyIsServedFromTheCache() throws Exception {
        JsonNode merchant = signUp("cache-hit@shop.com");
        String id = merchant.get("id").asText();
        String apiKey = merchant.get("apiKey").asText();

        mockMvc.perform(get("/api/v1/merchants/me").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        assertThat(redis.hasKey("apikey:" + apiKey.substring(0, 8))).isTrue();

        // Break the database copy. A lookup that reached the database now fails.
        Merchant row = merchantRepository.findById(id).orElseThrow();
        row.setApiKeyHash("0".repeat(64));
        merchantRepository.saveAndFlush(row);

        mockMvc.perform(get("/api/v1/merchants/me").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    /** The cache replaces the lookup, not the check. */
    @Test
    void aWrongKeyWithARealPrefixIsStillRefusedWhenCached() throws Exception {
        String apiKey = signUp("cache-wrong-key@shop.com").get("apiKey").asText();

        mockMvc.perform(get("/api/v1/merchants/me").header("X-API-Key", apiKey))
                .andExpect(status().isOk());

        String forged = apiKey.substring(0, 8) + "f".repeat(apiKey.length() - 8);
        mockMvc.perform(get("/api/v1/merchants/me").header("X-API-Key", forged))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingAMerchantStopsItsKeyImmediately() throws Exception {
        JsonNode merchant = signUp("cache-deleted@shop.com");
        String id = merchant.get("id").asText();
        String apiKey = merchant.get("apiKey").asText();
        String admin = adminToken("cache-admin@shop.com");

        mockMvc.perform(get("/api/v1/merchants/me").header("X-API-Key", apiKey))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/merchants/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(redis.hasKey("apikey:" + apiKey.substring(0, 8))).isFalse();
        mockMvc.perform(get("/api/v1/merchants/me").header("X-API-Key", apiKey))
                .andExpect(status().isForbidden());
    }

    /** Only merchants that exist are cached, so a key minted later is never refused by a stale miss. */
    @Test
    void anUnknownPrefixIsNotCached() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me").header("X-API-Key", "tk_zzzzz-no-such-merchant"))
                .andExpect(status().isForbidden());

        assertThat(redis.hasKey("apikey:tk_zzzzz")).isFalse();
    }
}
