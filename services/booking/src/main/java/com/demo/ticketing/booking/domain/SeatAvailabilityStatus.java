package com.demo.ticketing.booking.domain;

/**
 * Per {@code (eventId, seatId)} sale state, owned entirely by booking (ADR-0001). Mirrors the
 * Redis hold but is the durable/queryable record.
 */
public enum SeatAvailabilityStatus {
    AVAILABLE,
    HELD,
    SOLD
}
