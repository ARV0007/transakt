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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnershipIntegrationTest {

    private static final String ALICE = "alice@shop.com";
    private static final String BOB = "bob@shop.com";
    private static final String PASSWORD = "hunter2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    private void createMerchant(String email) throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Shop",
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    private String tokenFor(String email) throws Exception {
        createMerchant(email);

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    private String createPayment(String token, long amountPaise) throws Exception {
        String body = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "amountPaise", amountPaise,
                                "currency", "INR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void aMerchantCanReadItsOwnPayment() throws Exception {
        String alice = tokenFor(ALICE);
        String paymentId = createPayment(alice, 50000);

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaise").value(50000));
    }

    @Test
    void aMerchantCannotReadAnotherMerchantsPayment() throws Exception {
        String alice = tokenFor(ALICE);
        String paymentId = createPayment(alice, 50000);

        String bob = tokenFor(BOB);

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMerchantCannotReadAnotherMerchantsLedger() throws Exception {
        String alice = tokenFor(ALICE);
        String paymentId = createPayment(alice, 50000);

        String bob = tokenFor(BOB);

        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/ledger")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void theListEndpointIsScopedToTheCaller() throws Exception {
        String alice = tokenFor(ALICE);
        String alicePaymentId = createPayment(alice, 50000);

        String bob = tokenFor(BOB);
        createPayment(bob, 99000);

        String bobsList = mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(bobsList).contains("99000");
        assertThat(bobsList).doesNotContain(alicePaymentId);
    }

    @Test
    void merchantIdInTheBodyIsIgnored() throws Exception {
        String alice = tokenFor(ALICE);

        String body = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "merchantId", "some-other-merchants-id",
                                "amountPaise", 50000,
                                "currency", "INR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("some-other-merchants-id");
    }
}