package com.demo.ticketing.booking.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per successfully-deduped Kafka message (root CLAUDE.md: "every consumer must be
 * idempotent -- dedupe on event id"). {@link com.demo.ticketing.booking.application.SagaCompletionService}
 * inserts a row before doing any saga work; a primary-key violation on retry/redelivery means the
 * message was already processed and the listener should no-op.
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
        // JPA
    }

    public ProcessedMessage(UUID messageId) {
        this.messageId = messageId;
        this.processedAt = Instant.now();
    }

    public UUID getMessageId() {
        return messageId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
