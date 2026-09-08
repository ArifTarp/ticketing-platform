package com.demo.ticketing.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by booking to {@code booking.events} (message key = bookingId), covering both edge
 * paths that terminate a booking without confirming it, per business-rules.md:
 * <ul>
 *   <li>edge path (a): payment failed → {@code Booking.status = CANCELLED}</li>
 *   <li>edge path (b): hold-expiry sweep timeout → {@code Booking.status = EXPIRED}</li>
 * </ul>
 * business-rules.md's "Flagged gaps" section notes there is no distinct {@code BookingExpired}
 * Kafka event — both paths reuse this event type, distinguished only by {@link #reason}, which
 * carries the actual {@code Booking.status} value the reader may care about ("CANCELLED" or
 * "EXPIRED") so notification/consumers aren't forced to treat them identically if that
 * distinction later matters. This does not resolve the flagged gap (whether EXPIRED should be a
 * first-class event) — that decision is left to whoever picks it up, per the gap note.
 *
 * @param messageId   synthetic dedup identifier for idempotent consumer processing — named
 *                    {@code messageId} rather than {@code eventId} to avoid colliding with the
 *                    domain concept "event" (a concert/show) used elsewhere in this codebase.
 * @param bookingId   aggregate id / Kafka message key. {@code Long} to match booking's actual
 *                    JPA entity id type ({@code Booking.id} is BIGSERIAL).
 * @param userId      needed by notification to address the recipient. {@code Long} to match the
 *                    originating Postgres BIGSERIAL column.
 * @param reason      why the booking was cancelled: {@code "PAYMENT_FAILED"} or
 *                    {@code "HOLD_EXPIRED"} — informational only, not a new topic/event type.
 * @param cancelledAt when booking committed the terminal status.
 */
public record BookingCancelledEvent(
        UUID messageId,
        Long bookingId,
        Long userId,
        String reason,
        Instant cancelledAt) {
}
