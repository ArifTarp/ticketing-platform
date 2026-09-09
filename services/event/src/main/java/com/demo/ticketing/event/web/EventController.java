package com.demo.ticketing.event.web;

import com.demo.ticketing.event.application.EventService;
import com.demo.ticketing.event.web.dto.CreateEventRequest;
import com.demo.ticketing.event.web.dto.CreateSeatCategoryRequest;
import com.demo.ticketing.event.web.dto.EventResponse;
import com.demo.ticketing.event.web.dto.EventSummaryResponse;
import com.demo.ticketing.event.web.dto.SeatCategoryDto;
import com.demo.ticketing.event.web.dto.SeatMapResponse;
import com.demo.ticketing.event.web.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Catalog endpoints: the original public read-only browse/detail/seat-map GETs, plus the admin
 * write endpoints added in roadmap Phase 14 ({@code POST /events}, {@code POST
 * /events/{eventId}/seat-categories}). The gateway is responsible for rejecting non-ADMIN callers
 * on the write routes before the request reaches here — see services/event/CLAUDE.md's
 * "Not here (deliberately)" note; this controller does not itself check any role/JWT.
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

    /** Admin create (roadmap Phase 14). 404 problem+json if {@code venueId} does not exist. */
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Admin create (roadmap Phase 14): adds one or more price tiers to an existing event. 404
     * problem+json if {@code eventId} does not exist; 409 problem+json if a name/section already
     * used by this event is submitted again.
     */
    @PostMapping("/{eventId}/seat-categories")
    public ResponseEntity<List<SeatCategoryDto>> addSeatCategories(
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody List<@Valid CreateSeatCategoryRequest> requests) {
        List<SeatCategoryDto> response = eventService.addSeatCategories(eventId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Admin update. 404 problem+json if {@code eventId} does not exist. */
    @PutMapping("/{eventId}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody UpdateEventRequest request) {
        EventResponse response = eventService.updateEvent(eventId, request);
        return ResponseEntity.ok(response);
    }

    /** Admin delete. 404 problem+json if {@code eventId} does not exist; 204 on success. */
    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable("eventId") Long eventId) {
        eventService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }
}
