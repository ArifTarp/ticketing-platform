package com.demo.ticketing.event.web.dto;

import com.demo.ticketing.event.domain.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One card on the event-list screen: title, venue, date, "from $X" (lowest tier price) and a status
 * badge — exactly the fields {@code docs/user-flow.md} screen 2 renders.
 *
 * <p>{@code fromPrice} is null when the event has no price tiers yet.
 */
public record EventSummaryResponse(Long id,
                                   String title,
                                   String venueName,
                                   String city,
                                   Instant startsAt,
                                   EventStatus status,
                                   BigDecimal fromPrice) {
}
