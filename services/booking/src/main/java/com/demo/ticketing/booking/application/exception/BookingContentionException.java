package com.demo.ticketing.booking.application.exception;

/**
 * The {@code uq_bookings_user_event_pending} race (see {@code BookingHoldService.persistHoldWithRetry})
 * didn't resolve within {@code MAX_PENDING_BOOKING_INSERT_ATTEMPTS} retries — genuine, if rare, DB-level
 * contention on the same {@code (userId, eventId)} pending booking, not a bug. Maps to
 * {@code 409 Conflict} rather than leaking the underlying {@code DataIntegrityViolationException}.
 */
public class BookingContentionException extends RuntimeException {

    public BookingContentionException(String message) {
        super(message);
    }
}
