package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/merchants/me - a merchant reading its own record.
 *
 * The route has no id, so the interesting questions are whether "me" resolves to the
 * CALLER rather than to a merchant whose id is literally "me", and whether anything
 * secret rides along in the response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MerchantMeIntegrationTest {

    private static final String PASSWORD = "hunter2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

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

    /**
     * Also pins the routing rule. Both @GetMapping("/me") and @GetMapping("/{id}")
     * match this URL, and Spring MVC resolves by pattern specificity - a literal
     * segment beats a template variable - so /me wins. Asserting the returned id
     * equals the CALLER's id is what proves that, rather than a 404 from looking up
     * a merchant whose id is the string "me".
     */
    @Test
    void aMerchantCanReadItsOwnRecord() throws Exception {
        String id = createMerchant("me-read@shop.com");
        String token = tokenFor("me-read@shop.com");

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("me-read@shop.com"))
                .andExpect(jsonPath("$.role").value("MERCHANT"));
    }

    /**
     * The response is the entity itself, so this test is the thing standing between
     * a future field and an accidental disclosure. The password is WRITE_ONLY, the
     * hash is ignored, and apiKey is @Transient so it is null after a read - the key
     * is shown exactly once, at signup, and there is deliberately no way back to it.
     */
    @Test
    void theResponseCarriesNoSecrets() throws Exception {
        createMerchant("me-secrets@shop.com");
        String token = tokenFor("me-secrets@shop.com");

        // The whole test runs in ONE transaction, so signup's Merchant is still
        // managed here with its @Transient apiKey populated - MerchantService.create
        // sets it on the instance after save(). findById would then hand back that
        // same object out of the first-level cache rather than a fresh row, and the
        // key would appear in the response for a reason that cannot happen in
        // production, where every request has its own persistence context.
        //
        // flush() pushes the pending insert to the database; clear() detaches
        // everything, restoring the boundary the shared transaction erases.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.apiKeyHash").doesNotExist())
                .andExpect(jsonPath("$.apiKey").isEmpty())
                .andExpect(jsonPath("$.apiKeyPrefix").exists());
    }

    /** The only other way to name a merchant is by id, and that path is ADMIN-only. */
    @Test
    void readingAnotherMerchantByIdIsForbidden() throws Exception {
        String victimId = createMerchant("me-victim@shop.com");
        createMerchant("me-attacker@shop.com");
        String attacker = tokenFor("me-attacker@shop.com");

        mockMvc.perform(get("/api/v1/merchants/" + victimId)
                        .header("Authorization", "Bearer " + attacker))
                .andExpect(status().isForbidden());
    }

    @Test
    void theEndpointIsClosedWithoutACredential() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me"))
                .andExpect(status().isForbidden());
    }
}
