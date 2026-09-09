package com.demo.ticketing.event.application.exception;

/** {@code POST /api/v1/events/{eventId}/seat-categories} with an empty request body. */
public class InvalidSeatCategoryRequestException extends RuntimeException {

    public InvalidSeatCategoryRequestException(String message) {
        super(message);
    }
}
