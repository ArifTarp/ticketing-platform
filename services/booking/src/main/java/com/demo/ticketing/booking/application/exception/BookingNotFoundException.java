package com.demo.ticketing.booking.application.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long bookingId) {
        super("Booking " + bookingId + " was not found");
    }
}
