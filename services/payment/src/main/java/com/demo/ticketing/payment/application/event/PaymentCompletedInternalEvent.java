package com.demo.ticketing.payment.application.event;

import com.demo.ticketing.messaging.event.PaymentCompletedEvent;

/**
 * Plain in-process event raised by {@link com.demo.ticketing.payment.application.PaymentProcessingService}
 * right before its {@code @Transactional} method returns a fresh {@code COMPLETED} outcome. Never
 * published to Kafka directly from there — {@code KafkaOutboxPublisher}'s
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} is what actually sends {@link #event()}
 * to {@code payment.events}, so a rollback of the surrounding transaction can never leave a
 * published message for a {@code Payment} row that doesn't exist (root CLAUDE.md: "publish only
 * after the local DB commit").
 *
 * @param event the fully-formed outbound event, built with a fresh random {@code messageId} that
 *              identifies this Kafka message for the downstream consumer's own dedup — distinct
 *              from the inbound {@code PaymentRequestedCommand.messageId} used for this service's
 *              own idempotency check.
 */
public record PaymentCompletedInternalEvent(PaymentCompletedEvent event) {
}
