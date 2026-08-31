package com.transakt.transakt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transakt.transakt.auth.JwtService;
import com.transakt.transakt.bank.BankResult;
import com.transakt.transakt.bank.FakeBankClient;
import com.transakt.transakt.ledger.LedgerEntryRepository;
import com.transakt.transakt.payment.Payment;
import com.transakt.transakt.payment.PaymentReconciler;
import com.transakt.transakt.payment.PaymentRepository;
import com.transakt.transakt.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reconciler sweeps payments left PENDING when a bank call failed.
 *
 * Unlike the other integration tests, this one reaches past HTTP to the repository.
 * A payment created through POST /api/v1/payments is settled by PaymentProcessor
 * before the request returns, so it is never PENDING and the sweeper would find
 * nothing. The row has to be planted in the state a crash would have left it in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReconcilerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PaymentReconciler paymentReconciler;

    @Autowired
    private FakeBankClient fakeBankClient;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void clearBankMemory() {
        fakeBankClient.reset();
    }

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

        String token = objectMapper.readTree(body).get("token").asText();
        return jwtService.extractMerchantId(token);
    }

    /** Plants a payment already stranded at PENDING, older than the sweeper's cutoff. */
    private Payment strandedPayment(String merchantId) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setMerchantId(merchantId);
        payment.setAmountPaise(50000L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.PENDING);
        // If createdAt is @CreationTimestamp or set in @PrePersist, Hibernate
        // overwrites this and the sweeper finds nothing.
        payment.setCreatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        return paymentRepository.save(payment);
    }

    @Test
    void aStrandedPaymentTheBankApprovedIsSettledAndTheLedgerBalances() throws Exception {
        String merchantId = merchantIdFor("stranded-approved@shop.com");
        Payment stranded = strandedPayment(merchantId);

        fakeBankClient.recordDecision(stranded.getId(), BankResult.APPROVED);

        int settled = paymentReconciler.reconcileStrandedPayments();

        assertThat(settled).isEqualTo(1);

        Payment after = paymentRepository.findById(stranded.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(ledgerEntryRepository.findByPaymentId(stranded.getId())).hasSize(2);
    }

    @Test
    void aStrandedPaymentTheBankHasNoRecordOfIsLeftAlone() throws Exception {
        String merchantId = merchantIdFor("stranded-unknown@shop.com");
        Payment stranded = strandedPayment(merchantId);

        // Bank memory deliberately empty: lookup returns null, meaning
        // "no record" — the request never reached them.

        int settled = paymentReconciler.reconcileStrandedPayments();

        assertThat(settled).isZero();

        Payment after = paymentRepository.findById(stranded.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(ledgerEntryRepository.findByPaymentId(stranded.getId())).isEmpty();
    }
}