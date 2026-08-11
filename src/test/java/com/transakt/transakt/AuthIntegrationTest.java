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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    private void createMerchant(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Test Shop",
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk());
    }

    @Test
    void signupIsOpenAndNeverReturnsThePassword() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Open Shop",
                                "email", "open@shop.com",
                                "password", "hunter2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginWithCorrectPasswordReturnsAToken() throws Exception {
        createMerchant("login-ok@shop.com", "hunter2");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "login-ok@shop.com",
                                "password", "hunter2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.merchantId").exists());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        createMerchant("wrong-pw@shop.com", "hunter2");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "wrong-pw@shop.com",
                                "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void loginWithUnknownEmailGivesTheIdenticalMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "nobody@nowhere.com",
                                "password", "hunter2"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void paymentsAreClosedWithoutACredential() throws Exception {
        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsOpen() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }
}