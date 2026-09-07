package com.demo.ticketing.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published by booking to {@code booking.events} (message key = bookingId) after committing
 * {@code Booking.status = CONFIRMED} and flipping the held seats to {@code SOLD}, per
 * business-rules.md's checkout workflow step 4a.
 *
 * @param eventId    stable id for consumer dedupe (notification must dedupe per its own
 *                   CLAUDE.md and root CLAUDE.md's non-negotiables).
 * @param bookingId  aggregate id / Kafka message key.
 * @param userId     needed by notification to address the recipient (business-rules.md's
 *                   {@code Notification.recipient} field) without notification ever reading
 *                   booking's or auth's database directly.
 * @param seatIds    seats now SOLD under this booking, so notification can mention them in the
 *                   confirmation without a cross-service call.
 * @param confirmedAt when booking committed the CONFIRMED status.
 */
public record BookingConfirmedEvent(
        UUID eventId,
        UUID bookingId,
        UUID userId,
        List<UUID> seatIds,
        Instant confirmedAt) {
}
