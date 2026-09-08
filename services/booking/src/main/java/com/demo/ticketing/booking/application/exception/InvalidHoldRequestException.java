package com.demo.ticketing.booking.application.exception;

/**
 * A hold request is structurally invalid in a way bean validation on {@code HoldBookingRequest}
 * cannot express (e.g. a duplicate {@code seatId} within the same request). Maps to
 * {@code 400 Bad Request}.
 */
public class InvalidHoldRequestException extends RuntimeException {

    public InvalidHoldRequestException(String message) {
        super(message);
    }
}
