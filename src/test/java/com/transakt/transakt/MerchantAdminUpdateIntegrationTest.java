package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transakt.transakt.merchant.Merchant;
import com.transakt.transakt.merchant.MerchantRepository;
import com.transakt.transakt.merchant.MerchantRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/v1/merchants/{id} — the admin edit route.
 *
 * Note how the ADMIN fixture is built: sign up (which can only ever produce a
 * MERCHANT), promote the row directly through the repository, and only THEN log in.
 * The order is load-bearing, because the JWT carries the role as a claim and is
 * signed at login — a token minted before the promotion would still say MERCHANT
 * until it expired. That is the documented cost of stateless authorisation, visible
 * here in three lines of test setup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MerchantAdminUpdateIntegrationTest {

    private static final String PASSWORD = "hunter2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    private String json(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

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

    /** Signup can only produce a MERCHANT, so an admin has to be promoted in place. */
    private String adminToken(String email) throws Exception {
        String id = createMerchant(email);
        Merchant merchant = merchantRepository.findById(id).orElseThrow();
        merchant.setRole(MerchantRole.ADMIN);
        merchantRepository.saveAndFlush(merchant);
        return tokenFor(email);
    }

    @Test
    void anAdminCanUpdateAMerchant() throws Exception {
        String admin = adminToken("admin-update@shop.com");
        String targetId = createMerchant("target-update@shop.com");

        mockMvc.perform(put("/api/v1/merchants/" + targetId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Renamed", "email", "renamed@shop.com",
                                "businessName", "Renamed Ltd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.businessName").value("Renamed Ltd"));
    }

    /**
     * This returned 200 with an EMPTY BODY before the DTO change, because the
     * service returned null for a missing id and the controller handed null
     * straight back. A 200 that means "not found" is worse than a 500 — a client
     * has no way to tell it apart from success.
     */
    @Test
    void updatingAMerchantThatDoesNotExistIs404() throws Exception {
        String admin = adminToken("admin-404@shop.com");

        mockMvc.perform(put("/api/v1/merchants/does-not-exist")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Ghost", "email", "ghost@shop.com"))))
                .andExpect(status().isNotFound());
    }

    /**
     * The same assertion as on signup, one route along. A role in the body has
     * nowhere to bind, so it changes nothing — and unlike signup, the route is
     * ADMIN-only, so this was never an escalation. It is pinned because the rule
     * is "no controller binds a body to an entity", and a rule with an untested
     * exception is not a rule.
     */
    @Test
    void aRoleInTheUpdateBodyIsIgnored() throws Exception {
        String admin = adminToken("admin-role@shop.com");
        String targetId = createMerchant("target-role@shop.com");

        mockMvc.perform(put("/api/v1/merchants/" + targetId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Still A Merchant","email":"still@shop.com",
                                 "role":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MERCHANT"));
    }

    @Test
    void aPlainMerchantCannotUpdateAnyone() throws Exception {
        createMerchant("plain-update@shop.com");
        String token = tokenFor("plain-update@shop.com");
        String targetId = createMerchant("target-plain@shop.com");

        mockMvc.perform(put("/api/v1/merchants/" + targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nope", "email", "nope@shop.com"))))
                .andExpect(status().isForbidden());
    }
}
