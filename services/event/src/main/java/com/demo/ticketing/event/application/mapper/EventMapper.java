package com.demo.ticketing.event.application.mapper;

import com.demo.ticketing.event.domain.Event;
import com.demo.ticketing.event.domain.Seat;
import com.demo.ticketing.event.domain.SeatCategory;
import com.demo.ticketing.event.domain.Venue;
import com.demo.ticketing.event.web.dto.EventResponse;
import com.demo.ticketing.event.web.dto.EventSummaryResponse;
import com.demo.ticketing.event.web.dto.SeatCategoryDto;
import com.demo.ticketing.event.web.dto.SeatDto;
import com.demo.ticketing.event.web.dto.SeatMapResponse;
import com.demo.ticketing.event.web.dto.VenueDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps catalog entities to the wire DTOs. Hand-written rather than MapStruct: the only non-trivial
 * mapping here is the section-based seat/price-tier join below, which is real logic worth reading
 * and unit-testing directly (and it keeps the module free of an annotation processor).
 */
@Component
public class EventMapper {

    public List<EventSummaryResponse> toSummaries(List<Event> events) {
        return events.stream().map(this::toSummary).toList();
    }

    public EventSummaryResponse toSummary(Event event) {
        Venue venue = event.getVenue();
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                venue.getName(),
                venue.getCity(),
                event.getStartsAt(),
                event.getStatus(),
                lowestPriceOf(event));
    }

    public EventResponse toDetail(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartsAt(),
                event.getStatus(),
                event.isBookable(Instant.now()),
                toVenueDto(event.getVenue()),
                event.getSeatCategories().stream().map(this::toSeatCategoryDto).toList());
    }

    /**
     * Builds the static seat map: every seat of the event's venue that falls in a section this event
     * prices, tagged with that section's price tier. Seats in sections the event does not sell are
     * omitted — an event may use only part of a venue.
     *
     * <p>The seat order given by the caller (repository ordering: section, row, number) is
     * preserved. No availability is attached here or anywhere else — see ADR-0001.
     */
    public SeatMapResponse toSeatMap(Event event, List<Seat> venueSeats) {
        Map<String, SeatCategory> categoriesBySection = event.getSeatCategories().stream()
                .collect(Collectors.toMap(
                        SeatCategory::getSection,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));

        List<SeatDto> seats = venueSeats.stream()
                .map(seat -> {
                    SeatCategory category = categoriesBySection.get(seat.getSection());
                    return category == null ? null : toSeatDto(seat, category);
                })
                .filter(Objects::nonNull)
                .toList();

        return new SeatMapResponse(event.getId(), event.getVenue().getId(), seats);
    }

    private SeatDto toSeatDto(Seat seat, SeatCategory category) {
        return new SeatDto(
                seat.getId(),
                seat.getSection(),
                seat.getRowLabel(),
                seat.getNumber(),
                toSeatCategoryDto(category));
    }

    /** Used by the admin seat-category create endpoint to shape its response. */
    public List<SeatCategoryDto> toSeatCategoryDtos(List<SeatCategory> categories) {
        return categories.stream().map(this::toSeatCategoryDto).toList();
    }

    private SeatCategoryDto toSeatCategoryDto(SeatCategory category) {
        return new SeatCategoryDto(category.getId(), category.getName(), category.getPrice());
    }

    private VenueDto toVenueDto(Venue venue) {
        return new VenueDto(venue.getId(), venue.getName(), venue.getAddress(), venue.getCity());
    }

    /** "from $X" on an event card — null when the event has no price tiers yet. */
    private BigDecimal lowestPriceOf(Event event) {
        return event.getSeatCategories().stream()
                .map(SeatCategory::getPrice)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
