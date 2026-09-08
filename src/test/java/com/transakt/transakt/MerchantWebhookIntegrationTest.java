package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /api/v1/merchants/me.
 *
 * The route carries no id, so the interesting question is not "can a merchant edit
 * someone else's webhook" but "is there any way to ask". There is not, and the last
 * two tests pin the two ways someone would try.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MerchantWebhookIntegrationTest {

    private static final String PASSWORD = "hunter2";
    private static final String HOOK = "https://merchant.example.com/hooks/transakt";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    /** Returns the new merchant's id. */
    private String createMerchant(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Shop", "email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asText();
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void aMerchantCanSetItsOwnWebhookUrl() throws Exception {
        createMerchant("hook-set@shop.com");
        String token = tokenFor("hook-set@shop.com");

        mockMvc.perform(patch("/api/v1/merchants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("webhookUrl", HOOK))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookUrl").value(HOOK));
    }

    /** Null is how delivery is turned off. There is no separate DELETE for it. */
    @Test
    void aMerchantCanClearItsWebhookUrlWithNull() throws Exception {
        createMerchant("hook-clear@shop.com");
        String token = tokenFor("hook-clear@shop.com");

        mockMvc.perform(patch("/api/v1/merchants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("webhookUrl", HOOK))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/merchants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookUrl").isEmpty());
    }

    /**
     * Format validation only. This rejects the scheme, NOT the destination - a
     * well-formed https:// URL pointing at a private address still gets through,
     * which is the SSRF limitation documented on MerchantService.updateWebhookUrl.
     */
    @Test
    void aWebhookUrlWithAnUnsupportedSchemeIsRejected() throws Exception {
        createMerchant("hook-ftp@shop.com");
        String token = tokenFor("hook-ftp@shop.com");

        mockMvc.perform(patch("/api/v1/merchants/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("webhookUrl", "ftp://elsewhere.example.com/hook"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.webhookUrl").exists());
    }

    /**
     * The only other way to name a merchant is by id, and that path is ADMIN-only.
     * A regular merchant reaching for someone else's row is stopped by the security
     * rule, not by an ownership check inside the service - there is no such check,
     * because there is no such route.
     */
    @Test
    void patchingAnotherMerchantByIdIsForbidden() throws Exception {
        String victimId = createMerchant("hook-victim@shop.com");
        createMerchant("hook-attacker@shop.com");
        String attacker = tokenFor("hook-attacker@shop.com");

        mockMvc.perform(patch("/api/v1/merchants/" + victimId)
                        .header("Authorization", "Bearer " + attacker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("webhookUrl", HOOK))))
                .andExpect(status().isForbidden());
    }

    /** Pins that the new rule says authenticated(), not permitAll(). */
    @Test
    void theEndpointIsClosedWithoutACredential() throws Exception {
        mockMvc.perform(patch("/api/v1/merchants/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("webhookUrl", HOOK))))
                .andExpect(status().isForbidden());
    }
}
