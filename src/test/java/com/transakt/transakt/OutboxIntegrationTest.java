package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transakt.transakt.auth.JwtService;
import com.transakt.transakt.outbox.OutboxEvent;
import com.transakt.transakt.outbox.OutboxEventRepository;
import com.transakt.transakt.payment.Payment;
import com.transakt.transakt.payment.PaymentRepository;
import com.transakt.transakt.payment.PaymentService;
import com.transakt.transakt.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Settling a payment must write an outbox event in the SAME transaction.
 * Both outcomes are news the merchant needs, so both publish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OutboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JwtService jwtService;

    private String json(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    private String merchantIdFor(String email) throws Exception {
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

        return jwtService.extractMerchantId(objectMapper.readTree(body).get("token").asText());
    }

    private Payment pendingPayment(String merchantId) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setMerchantId(merchantId);
        payment.setAmountPaise(50000L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(Instant.now());
        return paymentRepository.save(payment);
    }

    @Test
    void settlingAnApprovedPaymentWritesAnOutboxEvent() throws Exception {
        Payment pending = pendingPayment(merchantIdFor("outbox-approved@shop.com"));

        paymentService.settle(pending.getId(), true);

        List<OutboxEvent> events = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getAggregateId()).isEqualTo(pending.getId());
        assertThat(event.getEventType()).isEqualTo("payment.settled");
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getPayload()).contains("CAPTURED", pending.getId(), "50000", "INR");
    }

    @Test
    void settlingADeclinedPaymentAlsoWritesAnOutboxEvent() throws Exception {
        Payment pending = pendingPayment(merchantIdFor("outbox-declined@shop.com"));

        paymentService.settle(pending.getId(), false);

        List<OutboxEvent> events = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).contains("FAILED");
    }
}