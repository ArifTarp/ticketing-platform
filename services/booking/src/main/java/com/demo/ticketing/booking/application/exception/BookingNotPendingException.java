package com.demo.ticketing.booking.application.exception;

/**
 * Thrown by {@code CheckoutService.checkout} when the target booking is not eligible for
 * checkout: its {@code status} is no longer {@code PENDING} (already progressed by a previous
 * checkout attempt, or already terminated by the payment consumer/sweep), or it is still
 * {@code PENDING} but its hold has already {@code expiresAt} in the past (checkout raced the
 * sweep and lost -- the booking is effectively dead even though the sweep hasn't run yet). Maps
 * to {@code 409 Conflict}.
 */
public class BookingNotPendingException extends RuntimeException {

    public BookingNotPendingException(Long bookingId, String reason) {
        super("Booking " + bookingId + " is not eligible for checkout: " + reason);
    }
}
