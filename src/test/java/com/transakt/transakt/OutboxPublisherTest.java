package com.transakt.transakt;

import com.transakt.transakt.outbox.OutboxEvent;
import com.transakt.transakt.outbox.OutboxEventRepository;
import com.transakt.transakt.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A unit test, deliberately. The rule worth pinning is "stamp only after the
 * broker confirms", and proving it needs the send to FAIL on demand — which a
 * mock does cleanly and a real broker does not. Same reasoning as
 * RateLimitServiceTest.
 */
class OutboxPublisherTest {

    private OutboxEventRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new OutboxPublisher(repository, kafkaTemplate, "payment.settled");
    }

    private OutboxEvent anEvent() {
        OutboxEvent event = new OutboxEvent("payment-123", "payment.settled");
        event.setPayload("{\"eventId\":\"" + event.getId() + "\",\"status\":\"CAPTURED\"}");
        return event;
    }

    @Test
    void aSuccessfulSendStampsThePublishedTime() {
        OutboxEvent event = anEvent();
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        int published = publisher.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void aFailedSendLeavesTheEventUnpublished() {
        OutboxEvent event = anEvent();
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("broker unreachable")));

        int published = publisher.publishPendingEvents();

        assertThat(published).isZero();
        assertThat(event.getPublishedAt()).isNull();
        verify(repository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void theEventIsKeyedByAggregateIdSoOrderingIsPreservedPerPayment() {
        OutboxEvent event = anEvent();
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPendingEvents();

        verify(kafkaTemplate).send("payment.settled", "payment-123", event.getPayload());
    }
}