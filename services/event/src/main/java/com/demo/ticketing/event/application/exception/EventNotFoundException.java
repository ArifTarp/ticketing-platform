package com.demo.ticketing.event.application.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long eventId) {
        super("Event " + eventId + " was not found");
    }
}
