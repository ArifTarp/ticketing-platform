package com.demo.ticketing.booking.application.exception;

import java.util.List;

/**
 * Thrown when a hold request cannot be satisfied because one or more requested seats are already
 * locked (Redis) or already {@code HELD}/{@code SOLD} (the durable {@code SeatAvailability} record).
 * Maps to {@code 409 Conflict} — see business-rules.md's "concurrent seat-race" edge path: the whole
 * hold request fails atomically, no partial holds.
 */
public class SeatUnavailableException extends RuntimeException {

    private final List<Long> seatIds;

    public SeatUnavailableException(List<Long> seatIds) {
        super("Seat(s) already held or sold: " + seatIds);
        this.seatIds = List.copyOf(seatIds);
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }
}
