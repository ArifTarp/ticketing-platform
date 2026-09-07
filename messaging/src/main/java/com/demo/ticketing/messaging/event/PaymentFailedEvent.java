package com.demo.ticketing.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by payment to {@code payment.events} (message key = bookingId) after committing
 * {@code Payment.status = FAILED}, per business-rules.md's checkout workflow edge path (a).
 *
 * @param eventId    stable id for consumer dedupe.
 * @param bookingId  aggregate id / Kafka message key.
 * @param reason     short machine/human-readable reason (e.g. "amount exceeds threshold",
 *                   "force-fail flag set") — business-rules.md's mock rule implies a reason is
 *                   knowable at failure time even though it doesn't mandate surfacing it; kept
 *                   here so booking/notification can log something more useful than a bare
 *                   failure flag without inventing new business behavior.
 * @param failedAt   when payment committed the FAILED status.
 */
public record PaymentFailedEvent(
        UUID eventId,
        UUID bookingId,
        String reason,
        Instant failedAt) {
}
