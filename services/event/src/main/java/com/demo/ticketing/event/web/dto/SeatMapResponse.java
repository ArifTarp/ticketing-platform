package com.demo.ticketing.event.web.dto;

import java.util.List;

/**
 * Response of {@code GET /api/v1/events/{eventId}/seats} — the static layout for the event's venue,
 * restricted to the sections this event actually prices. Carries no availability (ADR-0001).
 */
public record SeatMapResponse(Long eventId, Long venueId, List<SeatDto> seats) {
}
