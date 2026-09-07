package com.demo.ticketing.event.web;

import com.demo.ticketing.event.application.EventService;
import com.demo.ticketing.event.web.dto.EventResponse;
import com.demo.ticketing.event.web.dto.EventSummaryResponse;
import com.demo.ticketing.event.web.dto.SeatMapResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Read-only catalog endpoints.
 *
 * <p>Every {@code @RequestParam}/{@code @PathVariable} names its parameter explicitly on purpose.
 * The Maven parent is a plain aggregator rather than {@code spring-boot-starter-parent}, so
 * {@code -parameters} is not on by default and Spring cannot infer names by reflection; the root
 * pom now sets {@code maven.compiler.parameters}, but naming them here keeps the controller correct
 * regardless of build flags.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Browse the catalog. All filters are optional and mirror the event-list screen's filter bar
     * ({@code ?city=&from=&to=&q=}); {@code from}/{@code to} are ISO-8601 instants bounding
     * {@code startsAt}. Only {@code ON_SALE} events are returned.
     */
    @GetMapping
    public ResponseEntity<List<EventSummaryResponse>> listEvents(
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(eventService.listEvents(city, q, from, to));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.getEvent(eventId));
    }

    /**
     * Static seat map for the event — layout + price tier per seat, never availability (ADR-0001).
     * Clients merge this with {@code GET /api/v1/bookings/availability?eventId=...} by {@code seatId}.
     */
    @GetMapping("/{eventId}/seats")
    public ResponseEntity<SeatMapResponse> getEventSeats(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.getSeatMap(eventId));
    }
}
