package com.transakt.transakt.common;

import com.transakt.transakt.outbox.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes rows that have outlived their purpose.
 *
 * The two tables are different problems wearing the same clothes. The outbox is a
 * queue, not an archive: once published_at is stamped the row has done its job, and
 * what is left is a debugging window. Idempotency keys DID expire when they lived in
 * Redis, which dropped them after 24 hours for free; moving them into Postgres kept
 * the correctness and silently lost the expiry, because Postgres has no TTL. This
 * restores a guarantee rather than inventing new policy.
 *
 * The windows are constants, not configuration. Rate limits and topic names vary by
 * environment. How long an idempotency key is honoured is a promise to merchants,
 * and a promise that changes per environment is not a promise.
 */
@Slf4j
@Component
public class RetentionSweeper {

    /** The TTL the Redis implementation used, and the window Stripe publishes. */
    private static final Duration IDEMPOTENCY_WINDOW = Duration.ofHours(24);

    /** Not a queue requirement - a week of "did we actually send that, and when". */
    private static final Duration OUTBOX_WINDOW = Duration.ofDays(7);

    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public RetentionSweeper(OutboxEventRepository outboxEventRepository,
                            IdempotencyKeyRepository idempotencyKeyRepository) {
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    /**
     * Hourly. Deletion is not urgent, and a sweep that runs every five seconds is
     * just load.
     *
     * @Transactional because a @Modifying query requires an active transaction.
     * Unlike the outbox publisher next door, two instances running this at once is
     * harmless: a DELETE that matches nothing is a no-op, whereas a double publish
     * is a duplicate webhook.
     */
    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public int sweep() {
        Instant now = Instant.now();

        int events = outboxEventRepository.deletePublishedBefore(now.minus(OUTBOX_WINDOW));
        int keys = idempotencyKeyRepository.deleteCreatedBefore(now.minus(IDEMPOTENCY_WINDOW));

        if (events > 0 || keys > 0) {
            log.info("Retention sweep removed {} published outbox event(s) and {} idempotency key(s)",
                    events, keys);
        }

        return events + keys;
    }
}
