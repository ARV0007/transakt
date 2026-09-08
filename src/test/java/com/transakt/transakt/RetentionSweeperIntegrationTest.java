package com.transakt.transakt;

import com.transakt.transakt.common.IdempotencyKey;
import com.transakt.transakt.common.IdempotencyKeyRepository;
import com.transakt.transakt.common.RetentionSweeper;
import com.transakt.transakt.outbox.OutboxEvent;
import com.transakt.transakt.outbox.OutboxEventRepository;
import com.transakt.transakt.payment.Payment;
import com.transakt.transakt.payment.PaymentRepository;
import com.transakt.transakt.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sweeper deletes rows that have outlived their purpose - and, more importantly,
 * refuses to delete the ones that have not.
 *
 * An integration test, deliberately. The rules live in JPQL, and a mocked repository
 * would prove the sweeper calls a method, not that the statement it sends selects
 * the right rows. Only a real database can fail this test for the right reason.
 *
 * Every fixture uses saveAndFlush. A @Modifying query does NOT flush the persistence
 * context before it runs, so a plain save() would leave the inserts pending in memory
 * and the DELETE would run against a table that does not yet contain them.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RetentionSweeperIntegrationTest {

    @Autowired
    private RetentionSweeper retentionSweeper;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private OutboxEvent event(Instant createdAt, Instant publishedAt) {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID().toString(), "payment.settled");
        event.setPayload("{}");
        event.setCreatedAt(createdAt);
        event.setPublishedAt(publishedAt);
        return outboxEventRepository.saveAndFlush(event);
    }

    /** idempotency_keys has a foreign key to payments, so a key needs a real one. */
    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setMerchantId(UUID.randomUUID().toString());
        payment.setAmountPaise(50000L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCreatedAt(Instant.now());
        return paymentRepository.saveAndFlush(payment);
    }

    private IdempotencyKey key(Instant createdAt) {
        IdempotencyKey key = new IdempotencyKey();
        key.setId(UUID.randomUUID().toString());
        key.setMerchantId(UUID.randomUUID().toString());
        key.setIdempotencyKey(UUID.randomUUID().toString());
        key.setPaymentId(payment().getId());
        key.setCreatedAt(createdAt);
        return idempotencyKeyRepository.saveAndFlush(key);
    }

    @Test
    void publishedOutboxRowsPastTheWindowGoAndRecentOnesStay() {
        Instant longAgo = Instant.now().minus(Duration.ofDays(30));
        OutboxEvent stale = event(longAgo, longAgo);
        OutboxEvent fresh = event(Instant.now(), Instant.now());

        retentionSweeper.sweep();

        assertThat(outboxEventRepository.existsById(stale.getId())).isFalse();
        assertThat(outboxEventRepository.existsById(fresh.getId())).isTrue();
    }

    /**
     * The assertion that matters most in this file.
     *
     * An old UNPUBLISHED row is precisely a row that has been failing to publish: a
     * merchant who was never told their payment settled. It is also the oldest thing
     * in the table, so a careless "WHERE created_at < cutoff" would eat it first.
     * The outbox pattern exists to make that loss impossible, and one sloppy DELETE
     * would undo it.
     */
    @Test
    void unpublishedOutboxRowsAreNeverDeletedHoweverOld() {
        OutboxEvent ancient = event(Instant.now().minus(Duration.ofDays(365)), null);

        retentionSweeper.sweep();

        assertThat(outboxEventRepository.existsById(ancient.getId())).isTrue();
    }

    @Test
    void idempotencyKeysPastTheirWindowGoAndRecentOnesStay() {
        IdempotencyKey stale = key(Instant.now().minus(Duration.ofDays(2)));
        IdempotencyKey fresh = key(Instant.now());

        retentionSweeper.sweep();

        assertThat(idempotencyKeyRepository.existsById(stale.getId())).isFalse();
        assertThat(idempotencyKeyRepository.existsById(fresh.getId())).isTrue();
    }
}
