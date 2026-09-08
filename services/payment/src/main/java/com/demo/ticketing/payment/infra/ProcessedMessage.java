package com.demo.ticketing.payment.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One consumed-and-processed Kafka message id, used to dedupe redeliveries of
 * {@code PaymentRequestedCommand} (at-least-once delivery, root CLAUDE.md's "consumers must be
 * idempotent"). The primary key itself is the dedup mechanism — see
 * {@code PaymentProcessingService} for how a PK violation on insert is interpreted as "duplicate".
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    @Column(name = "message_id")
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
