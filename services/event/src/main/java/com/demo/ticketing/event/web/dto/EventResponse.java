package com.demo.ticketing.event.web.dto;

import com.demo.ticketing.event.domain.EventStatus;

import java.time.Instant;
import java.util.List;

/**
 * Event detail screen: header (title/venue/date/status badge) plus the price-tier list.
 *
 * <p>{@code bookable} mirrors the business rule "only an ON_SALE event starting in the future can be
 * booked", so the UI does not have to re-derive it from status + startsAt.
 */
public record EventResponse(Long id,
                            String title,
                            String description,
                            Instant startsAt,
                            EventStatus status,
                            boolean bookable,
                            String imageUrl,
                            VenueDto venue,
                            List<SeatCategoryDto> seatCategories) {
}
