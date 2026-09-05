package com.transakt.transakt.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sweeps unpublished outbox rows and sends them to Kafka.
 *
 * Deliberately NOT @Transactional: the send is an external call, and holding a
 * pooled database connection across one is the mistake Day 18 fixed. Each stamp
 * commits on its own.
 */
@Slf4j
@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${outbox.topic}") String topic) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "PT5S")
    public int publishPendingEvents() {
        List<OutboxEvent> unpublished =
                outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();

        if (unpublished.isEmpty()) {
            return 0;
        }

        log.info("Publishing {} outbox event(s)", unpublished.size());
        int published = 0;

        for (OutboxEvent event : unpublished) {
            try {
                // Keyed by aggregateId so every event for one payment lands on the
                // same partition. Kafka orders within a partition, not across a topic.
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);

                // Stamped only after the broker confirms. Stamping first would let a
                // failed send look published, which is the loss the outbox prevents.
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                published++;

            } catch (Exception e) {
                // Broad on purpose. The row stays unpublished whatever went wrong, so
                // nothing is lost and the next sweep retries. Stop rather than skip:
                // continuing past a failure would publish later events before earlier
                // ones and break the ordering the key exists to protect.
                log.warn("Failed to publish outbox event {} — stopping sweep, will retry",
                        event.getId(), e);
                Thread.currentThread().interrupt();
                break;
            }
        }

        return published;
    }
}