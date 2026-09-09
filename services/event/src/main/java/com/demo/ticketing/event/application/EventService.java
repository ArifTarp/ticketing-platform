package com.demo.ticketing.event.application;

import com.demo.ticketing.event.application.exception.EventNotFoundException;
import com.demo.ticketing.event.application.exception.InvalidSeatCategoryRequestException;
import com.demo.ticketing.event.application.exception.SeatCategoryConflictException;
import com.demo.ticketing.event.application.exception.VenueNotFoundException;
import com.demo.ticketing.event.application.mapper.EventMapper;
import com.demo.ticketing.event.domain.Event;
import com.demo.ticketing.event.domain.EventStatus;
import com.demo.ticketing.event.domain.Seat;
import com.demo.ticketing.event.domain.SeatCategory;
import com.demo.ticketing.event.domain.Venue;
import com.demo.ticketing.event.infra.EventRepository;
import com.demo.ticketing.event.infra.SeatRepository;
import com.demo.ticketing.event.infra.VenueRepository;
import com.demo.ticketing.event.web.dto.CreateEventRequest;
import com.demo.ticketing.event.web.dto.CreateSeatCategoryRequest;
import com.demo.ticketing.event.web.dto.EventResponse;
import com.demo.ticketing.event.web.dto.EventSummaryResponse;
import com.demo.ticketing.event.web.dto.SeatCategoryDto;
import com.demo.ticketing.event.web.dto.SeatMapResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Catalog use cases: the original read-only browse/detail/seat-map queries plus the admin write
 * endpoints added in roadmap Phase 14 (venue/event/seat-category create). This service never knows
 * about seat sale state (ADR-0001) — the writes here are catalog-only (venues, events, price
 * tiers), never availability.
 */
@Service
public class EventService {

    /**
     * The public catalog only ever lists {@code ON_SALE} events: {@code DRAFT} is unannounced, and
     * {@code SOLD_OUT}/{@code CLOSED} events are reachable by direct link but not browsable
     * (docs/user-flow.md screen 2). The admin-scoped listing (roadmap Phase 14) is a separate
     * method, {@link #listEventsForAdmin}, on its own {@code /api/v1/admin/events} path.
     */
    private static final EventStatus BROWSABLE_STATUS = EventStatus.ON_SALE;

    /** Open bounds for the optional startsAt range — see {@code EventRepository.findForBrowse}. */
    private static final Instant OPEN_LOWER_BOUND = Instant.EPOCH;
    private static final Instant OPEN_UPPER_BOUND = Instant.parse("9999-12-31T23:59:59Z");

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final VenueRepository venueRepository;
    private final EventMapper eventMapper;

    public EventService(EventRepository eventRepository,
                        SeatRepository seatRepository,
                        VenueRepository venueRepository,
                        EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.venueRepository = venueRepository;
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

    /**
     * Admin listing (roadmap Phase 14): every status, same optional filters as {@link
     * #listEvents}. Lives on a distinct {@code /api/v1/admin/events} path (not a {@code ?status=}
     * escape hatch on the public path) so the gateway can enforce the {@code ADMIN} role by a
     * simple path rule — this service performs no local JWT/role check itself (see
     * {@code AdminEventController}).
     */
    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listEventsForAdmin(String city, String query, Instant from, Instant to) {
        List<Event> events = eventRepository.findForAdmin(
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

    /**
     * Admin create (roadmap Phase 14). {@code status} defaults to {@code DRAFT} when omitted.
     * 404 (via {@link VenueNotFoundException}) if {@code venueId} does not exist.
     */
    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new VenueNotFoundException(request.venueId()));
        EventStatus status = request.status() == null ? EventStatus.DRAFT : request.status();
        Event event = new Event(venue, request.title(), request.description(), request.startsAt(), status);
        Event saved = eventRepository.save(event);
        return eventMapper.toDetail(saved);
    }

    /**
     * Admin create (roadmap Phase 14): adds one or more price tiers to an existing event. 404 if
     * {@code eventId} does not exist; {@link SeatCategoryConflictException} (409) if a
     * name/section already used by this event is submitted again
     * ({@code uq_seat_categories_event_name}/{@code uq_seat_categories_event_section}).
     */
    @Transactional
    public List<SeatCategoryDto> addSeatCategories(Long eventId, List<CreateSeatCategoryRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new InvalidSeatCategoryRequestException("At least one seat category is required");
        }
        Event event = loadEvent(eventId);
        List<SeatCategory> created = new ArrayList<>();
        for (CreateSeatCategoryRequest request : requests) {
            SeatCategory category = new SeatCategory(event, request.name(), request.price(), request.section());
            event.addSeatCategory(category);
            created.add(category);
        }
        try {
            // `event` is already managed (loaded in this same transaction) — flushing directly
            // (rather than calling save()/saveAndFlush(), which would merge() an entity that
            // already has an id) lets JPA cascade-persist the new children in place, so the
            // `SeatCategory` instances in `created` get their generated ids set on themselves
            // instead of on a merge() copy we would otherwise discard.
            eventRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new SeatCategoryConflictException(eventId);
        }
        return eventMapper.toSeatCategoryDtos(created);
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
