package com.transakt.transakt.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A row written in the SAME transaction as the thing it describes.
 * Either both commit or neither does, so an event can never be lost.
 *
 * publishedAt null means unpublished. There is no status enum: null or a
 * timestamp says everything, and it records WHEN for free.
 *
 * The primary key doubles as the public event id. It is assigned once, in the same
 * transaction as the payment, and survives every republish — which is what makes it
 * usable for deduplication. An id minted at publish time would differ on each retry.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * The payload is set separately, on purpose. It must carry this row's id as the
     * event id, and the only way to make the two incapable of disagreeing is to build
     * the payload from getId() after the row exists. A constructor taking a ready-made
     * payload would let a caller pass one with a different id, or none at all, and
     * nothing would complain.
     */
    public OutboxEvent(String aggregateId, String eventType) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
    }
}