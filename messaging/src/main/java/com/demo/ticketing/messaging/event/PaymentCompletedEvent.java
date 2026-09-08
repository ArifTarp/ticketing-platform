package com.demo.ticketing.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by payment to {@code payment.events} (message key = bookingId) after committing
 * {@code Payment.status = COMPLETED}, per business-rules.md's checkout workflow step 3/4a.
 *
 * @param messageId    synthetic dedup identifier for idempotent consumer processing (booking's
 *                     payment-result consumer must be idempotent per root CLAUDE.md) — named
 *                     {@code messageId} rather than {@code eventId} to avoid colliding with the
 *                     domain concept "event" (a concert/show) used elsewhere in this codebase.
 * @param bookingId    aggregate id / Kafka message key; booking uses this to look up the
 *                     PENDING booking to confirm. {@code Long} to match booking's actual JPA
 *                     entity id type ({@code Booking.id} is BIGSERIAL).
 * @param providerRef  the mock provider's reference, carried through for audit/traceability.
 * @param amount       amount that was charged, echoed back for booking-side verification.
 * @param completedAt  when payment committed the COMPLETED status.
 */
public record PaymentCompletedEvent(
        UUID messageId,
        Long bookingId,
        String providerRef,
        BigDecimal amount,
        Instant completedAt) {
}
