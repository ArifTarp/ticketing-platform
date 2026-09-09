package com.demo.ticketing.event.web.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/v1/venues} body — every field is required and non-blank. */
public record CreateVenueRequest(@NotBlank String name,
                                 @NotBlank String address,
                                 @NotBlank String city) {
}
