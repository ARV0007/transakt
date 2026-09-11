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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signup is the only permitAll endpoint that writes a row, which makes its request
 * body the largest attack surface in the API.
 *
 * The first two tests pin a real privilege escalation that existed until the
 * CreateMerchantRequest DTO was introduced: POST with {"role":"ADMIN"} bound
 * straight onto the entity and produced an administrator, and /api/v1/merchants/**
 * is hasRole("ADMIN") - read, edit and delete every merchant on the platform.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MerchantSignupSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "hunter2"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void aRoleInTheSignupBodyIsIgnored() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Escalator","email":"escalate@shop.com",
                                 "password":"hunter2","role":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MERCHANT"));
    }

    /**
     * The assertion that actually matters. The test above checks what the response
     * SAYS; this one checks what the credential can DO, which is the thing an
     * attacker cares about and the thing a future refactor could quietly break.
     */
    @Test
    void aMerchantWhoAskedForAdminStillCannotUseAdminRoutes() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Escalator","email":"escalate2@shop.com",
                                 "password":"hunter2","role":"ADMIN"}"""))
                .andExpect(status().isOk());

        String token = tokenFor("escalate2@shop.com");

        mockMvc.perform(get("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * Signup used to accept this, creating a merchant who could never log in - an
     * account reachable only by an API key shown once and never again.
     */
    @Test
    void signupWithoutAPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "No Password", "email", "nopw@shop.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").exists());
    }

    @Test
    void signupWithABlankNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "  ", "email", "blank@shop.com",
                                "password", "hunter2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void signupWithAMalformedEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Bad Email", "email", "not-an-email",
                                "password", "hunter2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }
}
