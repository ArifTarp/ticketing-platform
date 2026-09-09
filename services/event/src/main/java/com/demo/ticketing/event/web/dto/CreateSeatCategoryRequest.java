package com.demo.ticketing.event.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * One item of the {@code POST /api/v1/events/{eventId}/seat-categories} body — the request body is
 * a JSON array of these. {@code section} is how the tier attaches to physical seats (see
 * {@code SeatCategory} javadoc); {@code name}/{@code section} must each be unique per event
 * ({@code uq_seat_categories_event_name}/{@code uq_seat_categories_event_section}).
 */
public record CreateSeatCategoryRequest(@NotBlank String name,
                                        @NotNull @PositiveOrZero BigDecimal price,
                                        @NotBlank String section) {
}
