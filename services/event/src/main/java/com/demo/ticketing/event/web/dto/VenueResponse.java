package com.demo.ticketing.event.web.dto;

/** {@code POST /api/v1/venues} response. */
public record VenueResponse(Long id, String name, String address, String city) {
}
