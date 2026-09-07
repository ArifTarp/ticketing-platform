package com.demo.ticketing.event.application;

import com.demo.ticketing.event.application.exception.EventNotFoundException;
import com.demo.ticketing.event.application.mapper.EventMapper;
import com.demo.ticketing.event.domain.Event;
import com.demo.ticketing.event.domain.EventStatus;
import com.demo.ticketing.event.domain.Seat;
import com.demo.ticketing.event.infra.EventRepository;
import com.demo.ticketing.event.infra.SeatRepository;
import com.demo.ticketing.event.web.dto.EventResponse;
import com.demo.ticketing.event.web.dto.EventSummaryResponse;
import com.demo.ticketing.event.web.dto.SeatMapResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Read-only catalog use cases. This service never knows about seat sale state (ADR-0001).
 */
@Service
public class EventService {

    /**
     * The public catalog only ever lists {@code ON_SALE} events: {@code DRAFT} is unannounced, and
     * {@code SOLD_OUT}/{@code CLOSED} events are reachable by direct link but not browsable
     * (docs/user-flow.md screen 2). An admin-scoped listing comes with roadmap Phase 14.
     */
    private static final EventStatus BROWSABLE_STATUS = EventStatus.ON_SALE;

    /** Open bounds for the optional startsAt range — see {@code EventRepository.findForBrowse}. */
    private static final Instant OPEN_LOWER_BOUND = Instant.EPOCH;
    private static final Instant OPEN_UPPER_BOUND = Instant.parse("9999-12-31T23:59:59Z");

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final EventMapper eventMapper;

    public EventService(EventRepository eventRepository,
                        SeatRepository seatRepository,
                        EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.eventMapper = eventMapper;
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listEvents(String city, String query, Instant from, Instant to) {
        List<Event> events = eventRepository.findForBrowse(
                BROWSABLE_STATUS,
                lowerCased(city),
                titlePattern(query),
                from == null ? OPEN_LOWER_BOUND : from,
                to == null ? OPEN_UPPER_BOUND : to);
        return eventMapper.toSummaries(events);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        return eventMapper.toDetail(loadEvent(eventId));
    }

    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long eventId) {
        Event event = loadEvent(eventId);
        List<Seat> seats = seatRepository
                .findByVenueIdOrderBySectionAscRowLabelAscNumberAsc(event.getVenue().getId());
        return eventMapper.toSeatMap(event, seats);
    }

    private Event loadEvent(Long eventId) {
        return eventRepository.findDetailById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    /** Case-folding happens here, not in the query — see {@code EventRepository.findForBrowse}. */
    private static String lowerCased(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** Free-text title search: a lower-cased {@code %contains%} LIKE pattern, or null if unset. */
    private static String titlePattern(String query) {
        String lowered = lowerCased(query);
        return lowered == null ? null : "%" + lowered + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
