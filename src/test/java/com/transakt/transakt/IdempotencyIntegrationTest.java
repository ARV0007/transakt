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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdempotencyIntegrationTest {

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

    private String createPayment(String token, String idempotencyKey, long amountPaise) throws Exception {
        var request = post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "amountPaise", amountPaise,
                        "currency", "INR")));

        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }

        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void theSameKeyTwiceReturnsTheSamePayment() throws Exception {
        String token = tokenFor("idem-same@shop.com");

        String first = createPayment(token, "order-001", 50000);
        String second = createPayment(token, "order-001", 50000);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentKeysCreateDifferentPayments() throws Exception {
        String token = tokenFor("idem-diff@shop.com");

        String first = createPayment(token, "order-001", 50000);
        String second = createPayment(token, "order-002", 50000);

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void noKeyStillWorks() throws Exception {
        String token = tokenFor("idem-none@shop.com");

        String first = createPayment(token, null, 50000);
        String second = createPayment(token, null, 50000);

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void aReusedKeyIgnoresADifferentBody() throws Exception {
        String token = tokenFor("idem-body@shop.com");

        String first = createPayment(token, "order-001", 50000);
        String second = createPayment(token, "order-001", 999999);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void twoMerchantsCanUseTheSameKeyIndependently() throws Exception {
        String alice = tokenFor("idem-alice@shop.com");
        String bob = tokenFor("idem-bob@shop.com");

        String alicePayment = createPayment(alice, "order-001", 50000);
        String bobPayment = createPayment(bob, "order-001", 50000);

        assertThat(bobPayment).isNotEqualTo(alicePayment);
    }
}