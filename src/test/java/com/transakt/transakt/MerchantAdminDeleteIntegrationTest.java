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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELETE /api/v1/merchants/{id}.
 *
 * Before this change the route answered 200 with the body `false` for an id that
 * does not exist, because the service returned a boolean and the controller handed
 * it straight back. A client that checks the status code — which is what you want
 * clients to do — read that as success.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MerchantAdminDeleteIntegrationTest {

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

    /**
     * 204, and the row is actually gone. Asserting the status alone would have
     * passed against the old code too, which is the whole reason the second
     * assertion is here — existsById issues a query, which forces the pending
     * delete to flush.
     */
    @Test
    void anAdminCanDeleteAMerchant() throws Exception {
        String admin = adminToken("admin-delete@shop.com");
        String targetId = createMerchant("target-delete@shop.com");

        mockMvc.perform(delete("/api/v1/merchants/" + targetId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(merchantRepository.existsById(targetId)).isFalse();
    }

    /** Answered 200 with the body `false` before the fix. */
    @Test
    void deletingAMerchantThatDoesNotExistIs404() throws Exception {
        String admin = adminToken("admin-delete-404@shop.com");

        mockMvc.perform(delete("/api/v1/merchants/does-not-exist")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPlainMerchantCannotDeleteAnyone() throws Exception {
        createMerchant("plain-delete@shop.com");
        String token = tokenFor("plain-delete@shop.com");
        String targetId = createMerchant("target-plain-delete@shop.com");

        mockMvc.perform(delete("/api/v1/merchants/" + targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertThat(merchantRepository.existsById(targetId)).isTrue();
    }
}
