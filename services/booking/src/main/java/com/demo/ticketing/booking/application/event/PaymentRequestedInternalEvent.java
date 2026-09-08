package com.demo.ticketing.booking.application.event;

import com.demo.ticketing.messaging.command.PaymentRequestedCommand;

/**
 * Plain in-process Spring application event (NOT a Kafka message) raised from within
 * {@code CheckoutService.checkout}'s {@code @Transactional} method. This is the "outbox" half of
 * the publish-after-commit pattern (root CLAUDE.md: "publish events only after the local DB
 * commit -- outbox pattern preferred"): the actual Kafka send only happens once
 * {@code KafkaOutboxPublisher}'s {@code @TransactionalEventListener(phase = AFTER_COMMIT)} handler
 * observes this event, guaranteeing the {@code SagaState} row is already durably committed before
 * anything hits the wire.
 */
public record PaymentRequestedInternalEvent(PaymentRequestedCommand command) {
}
