package com.demo.ticketing.event.web.dto;

import com.demo.ticketing.event.domain.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * {@code PUT /api/v1/events/{eventId}} body. Mirrors {@link CreateEventRequest} minus
 * {@code venueId} (an event's venue is not reassignable via this endpoint) plus {@code imageUrl}.
 * {@code status} is required here (unlike create, it does not default to {@code DRAFT}) since an
 * update always states the event's full current state.
 */
public record UpdateEventRequest(@NotBlank String title,
                                 String description,
                                 @NotNull Instant startsAt,
                                 @NotNull EventStatus status,
                                 String imageUrl) {
}
