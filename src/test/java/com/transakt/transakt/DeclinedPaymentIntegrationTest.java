package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bank's answer decides the payment's final state, and the final state decides
 * whether the ledger records any movement at all.
 *
 * `decline-rate: 1` forces every authorization to be refused for this class only,
 * which is why it needs its own application context. A random decline rate would
 * make these assertions flaky, and a flaky test teaches people to ignore red builds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "bank.decline-rate=1")
@Transactional
class DeclinedPaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void aDeclinedPaymentIsFailedAndMovesNoMoney() throws Exception {
        String token = tokenFor("declined@shop.com");

        String body = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "amountPaise", 50000,
                                "currency", "INR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var payment = objectMapper.readTree(body);
        assertThat(payment.get("status").asText()).isEqualTo("FAILED");

        String ledger = mockMvc.perform(get("/api/v1/payments/" + payment.get("id").asText() + "/ledger")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The bank refused, so nothing moved. Two entries here would be the books
        // claiming a transfer that never happened.
        assertThat(objectMapper.readTree(ledger)).isEmpty();
    }
}
