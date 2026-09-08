package com.demo.ticketing.booking.web.dto;

import com.demo.ticketing.booking.domain.SeatAvailabilityStatus;

/**
 * Wire shape for {@code GET /api/v1/bookings/availability?eventId=} -- the frontend merges this
 * with event's static seat-map layout (business-rules.md's "Seat map contract") to render which
 * seats are pickable. A seat with no {@code SeatAvailability} row is implicitly
 * {@code AVAILABLE} and simply does not appear in this list (booking has no static seat catalog
 * to enumerate "everything else" from -- that's event's job).
 */
public record SeatAvailabilityResponse(Long seatId, SeatAvailabilityStatus status) {
}
