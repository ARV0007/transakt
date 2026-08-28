package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The approved half of the state machine. The test profile already sets
 * decline-rate to 0, so this class needs no property override and shares the
 * default test context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApprovedPaymentIntegrationTest {

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
    void anApprovedPaymentIsCapturedAndTheLedgerBalances() throws Exception {
        String token = tokenFor("approved@shop.com");

        String body = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "amountPaise", 50000,
                                "currency", "INR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode payment = objectMapper.readTree(body);
        assertThat(payment.get("status").asText()).isEqualTo("CAPTURED");

        String ledger = mockMvc.perform(get("/api/v1/payments/" + payment.get("id").asText() + "/ledger")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode entries = objectMapper.readTree(ledger);
        assertThat(entries).hasSize(2);

        // Sum CREDITs as positive and DEBITs as negative. Double entry means the
        // total is always zero; anything else is corruption and this is how you spot it.
        long net = 0;
        for (JsonNode entry : entries) {
            long amount = entry.get("amountPaise").asLong();
            net += entry.get("direction").asText().equals("CREDIT") ? amount : -amount;
        }
        assertThat(net).isZero();
    }
}
