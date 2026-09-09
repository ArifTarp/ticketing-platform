package com.demo.ticketing.event.web;

import com.demo.ticketing.event.application.EventService;
import com.demo.ticketing.event.web.dto.EventSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Admin-only catalog listing (roadmap Phase 14): every {@code Event} status, not just {@code
 * ON_SALE}. Deliberately kept on a distinct {@code /api/v1/admin/**} path prefix rather than a
 * {@code ?status=} query parameter on the public {@code GET /api/v1/events} — Spring Cloud
 * Gateway/Security route matching is path+method based, so putting this on its own prefix is what
 * lets the gateway's {@code /api/v1/admin/**} → {@code hasRole("ADMIN")} rule enforce it with a
 * simple path rule. This controller performs no local JWT/role check itself; per
 * services/event/CLAUDE.md's "Not here (deliberately)" note, that is the gateway's job, matching
 * the same deferral already used for {@code POST /events}/{@code POST /venues}.
 *
 * <p>Every {@code @RequestParam} names its parameter explicitly — see {@link EventController}'s
 * Javadoc for why (this Maven module does not build with {@code -parameters} on by default).
 */
@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Admin listing: same optional filters as {@code GET /api/v1/events}
     * ({@code ?city=&q=&from=&to=}), but every event status is returned (DRAFT, ON_SALE, SOLD_OUT,
     * CLOSED) — this is what backs the admin events table.
     */
    @GetMapping
    public ResponseEntity<List<EventSummaryResponse>> listEventsForAdmin(
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(eventService.listEventsForAdmin(city, q, from, to));
    }
}
