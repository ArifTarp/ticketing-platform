package com.demo.ticketing.messaging.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by booking to {@code payment.commands} (message key = bookingId) after the local
 * {@code SagaState(step=PAYMENT_REQUESTED, status=IN_PROGRESS)} commit, per
 * business-rules.md's checkout workflow step 2.
 *
 * @param eventId   stable id for this message, used by the payment consumer to dedupe on
 *                  redelivery (root CLAUDE.md non-negotiable: consumers must be idempotent).
 * @param bookingId aggregate id; also the Kafka message key for ordering.
 * @param userId    owner of the booking; payment does not need it for its own logic today but
 *                  carrying it avoids a future cross-service lookup if payment ever needs to
 *                  address/report on the paying user.
 * @param amount    total to charge, snapshot from {@code Booking.total} (BookingItem price sum
 *                  at hold time, not recalculated) — minor units are not implied here since
 *                  business-rules.md only says "store as integer minor units or BigDecimal,
 *                  never float/double"; BigDecimal is used directly to avoid a lossy conversion.
 * @param requestedAt when booking published this command.
 */
public record PaymentRequestedCommand(
        UUID eventId,
        UUID bookingId,
        UUID userId,
        BigDecimal amount,
        Instant requestedAt) {
}
