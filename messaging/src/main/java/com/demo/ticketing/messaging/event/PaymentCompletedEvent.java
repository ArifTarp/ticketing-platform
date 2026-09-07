package com.demo.ticketing.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by payment to {@code payment.events} (message key = bookingId) after committing
 * {@code Payment.status = COMPLETED}, per business-rules.md's checkout workflow step 3/4a.
 *
 * @param eventId      stable id for consumer dedupe (booking's payment-result consumer must be
 *                     idempotent per root CLAUDE.md).
 * @param bookingId    aggregate id / Kafka message key; booking uses this to look up the
 *                     PENDING booking to confirm.
 * @param providerRef  the mock provider's reference, carried through for audit/traceability.
 * @param amount       amount that was charged, echoed back for booking-side verification.
 * @param completedAt  when payment committed the COMPLETED status.
 */
public record PaymentCompletedEvent(
        UUID eventId,
        UUID bookingId,
        String providerRef,
        BigDecimal amount,
        Instant completedAt) {
}
