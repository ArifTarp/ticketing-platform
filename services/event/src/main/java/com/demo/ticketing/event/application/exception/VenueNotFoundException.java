package com.demo.ticketing.event.application.exception;

public class VenueNotFoundException extends RuntimeException {

    public VenueNotFoundException(Long venueId) {
        super("Venue " + venueId + " was not found");
    }
}
