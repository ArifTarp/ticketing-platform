package com.demo.ticketing.event.web.dto;

import com.demo.ticketing.event.domain.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * {@code POST /api/v1/events} body. {@code status} is optional and defaults to {@code DRAFT}
 * (business-rules.md lifecycle: {@code DRAFT -> ON_SALE -> SOLD_OUT | CLOSED}) — an admin creates
 * an event unannounced and moves it to {@code ON_SALE} in a later update.
 */
public record CreateEventRequest(@NotNull Long venueId,
                                 @NotBlank String title,
                                 String description,
                                 @NotNull Instant startsAt,
                                 EventStatus status) {
}
