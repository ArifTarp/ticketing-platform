package com.demo.ticketing.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published by booking to {@code booking.events} (message key = bookingId) after committing
 * {@code Booking.status = CONFIRMED} and flipping the held seats to {@code SOLD}, per
 * business-rules.md's checkout workflow step 4a.
 *
 * @param messageId  synthetic dedup identifier for idempotent consumer processing (notification
 *                   must dedupe per its own CLAUDE.md and root CLAUDE.md's non-negotiables) —
 *                   named {@code messageId} rather than {@code eventId} to avoid colliding with
 *                   the domain concept "event" (a concert/show) used elsewhere in this codebase.
 * @param bookingId  aggregate id / Kafka message key. {@code Long} to match booking's actual
 *                   JPA entity id type ({@code Booking.id} is BIGSERIAL).
 * @param userId     needed by notification to address the recipient (business-rules.md's
 *                   {@code Notification.recipient} field) without notification ever reading
 *                   booking's or auth's database directly. {@code Long} to match the
 *                   originating Postgres BIGSERIAL column.
 * @param seatIds    seats now SOLD under this booking, so notification can mention them in the
 *                   confirmation without a cross-service call. {@code Long} to match booking's
 *                   seat id type.
 * @param confirmedAt when booking committed the CONFIRMED status.
 */
public record BookingConfirmedEvent(
        UUID messageId,
        Long bookingId,
        Long userId,
        List<Long> seatIds,
        Instant confirmedAt) {
}
