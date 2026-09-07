package com.demo.ticketing.event.application.mapper;

import com.demo.ticketing.event.domain.Event;
import com.demo.ticketing.event.domain.EventStatus;
import com.demo.ticketing.event.domain.Seat;
import com.demo.ticketing.event.domain.SeatCategory;
import com.demo.ticketing.event.domain.Venue;
import com.demo.ticketing.event.web.dto.EventResponse;
import com.demo.ticketing.event.web.dto.EventSummaryResponse;
import com.demo.ticketing.event.web.dto.SeatDto;
import com.demo.ticketing.event.web.dto.SeatMapResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests (no Spring context, no Docker) for the only real logic in this service:
 * composing the seat-map layout from a venue's static seats plus the event's price tiers.
 */
class EventMapperTest {

    private final EventMapper mapper = new EventMapper();

    private static final Instant STARTS_AT = Instant.now().plus(30, ChronoUnit.DAYS);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private Venue demoArena() {
        return withId(new Venue("Demo Arena", "1 Demo Street", "Istanbul"), 1L);
    }

    private Event neonNights(Venue venue) {
        Event event = withId(
                new Event(venue, "Neon Nights Live", "A synthwave night", STARTS_AT, EventStatus.ON_SALE), 10L);
        event.addSeatCategory(withId(new SeatCategory(event, "VIP", new BigDecimal("250.00"), "A"), 100L));
        event.addSeatCategory(withId(new SeatCategory(event, "Standard", new BigDecimal("120.00"), "B"), 101L));
        return event;
    }

    @Test
    void toSummaryUsesTheLowestSeatCategoryPriceAsFromPrice() {
        Event event = neonNights(demoArena());

        EventSummaryResponse summary = mapper.toSummary(event);

        assertThat(summary.id()).isEqualTo(10L);
        assertThat(summary.title()).isEqualTo("Neon Nights Live");
        assertThat(summary.venueName()).isEqualTo("Demo Arena");
        assertThat(summary.city()).isEqualTo("Istanbul");
        assertThat(summary.status()).isEqualTo(EventStatus.ON_SALE);
        assertThat(summary.fromPrice()).isEqualByComparingTo("120.00");
    }

    @Test
    void toSummaryReturnsNullFromPriceWhenTheEventHasNoSeatCategories() {
        Event event = withId(
                new Event(demoArena(), "Unpriced Event", null, STARTS_AT, EventStatus.DRAFT), 11L);

        EventSummaryResponse summary = mapper.toSummary(event);

        assertThat(summary.fromPrice()).isNull();
    }

    @Test
    void toDetailExposesTheVenueAndPriceTiersHighestPriceFirst() {
        Event event = neonNights(demoArena());

        EventResponse detail = mapper.toDetail(event);

        assertThat(detail.id()).isEqualTo(10L);
        assertThat(detail.description()).isEqualTo("A synthwave night");
        assertThat(detail.venue().name()).isEqualTo("Demo Arena");
        assertThat(detail.venue().city()).isEqualTo("Istanbul");
        assertThat(detail.seatCategories()).extracting("name").containsExactly("VIP", "Standard");
        assertThat(detail.seatCategories().get(0).price()).isEqualByComparingTo("250.00");
    }

    @Test
    void toSeatMapAttachesTheSeatCategoryThatCoversTheSeatSection() {
        Venue venue = demoArena();
        Event event = neonNights(venue);
        List<Seat> seats = List.of(
                withId(new Seat(venue, "A", "1", 1), 1000L),
                withId(new Seat(venue, "B", "3", 7), 1001L));

        SeatMapResponse seatMap = mapper.toSeatMap(event, seats);

        assertThat(seatMap.eventId()).isEqualTo(10L);
        assertThat(seatMap.venueId()).isEqualTo(1L);
        assertThat(seatMap.seats()).hasSize(2);

        SeatDto vipSeat = seatMap.seats().get(0);
        assertThat(vipSeat.seatId()).isEqualTo(1000L);
        assertThat(vipSeat.section()).isEqualTo("A");
        assertThat(vipSeat.row()).isEqualTo("1");
        assertThat(vipSeat.number()).isEqualTo(1);
        assertThat(vipSeat.seatCategory().name()).isEqualTo("VIP");
        assertThat(vipSeat.seatCategory().price()).isEqualByComparingTo("250.00");

        SeatDto standardSeat = seatMap.seats().get(1);
        assertThat(standardSeat.seatId()).isEqualTo(1001L);
        assertThat(standardSeat.seatCategory().name()).isEqualTo("Standard");
    }

    @Test
    void toSeatMapDropsSeatsInSectionsThatTheEventDoesNotSell() {
        Venue venue = demoArena();
        Event event = neonNights(venue); // sells sections A and B only
        List<Seat> seats = List.of(
                withId(new Seat(venue, "A", "1", 1), 1000L),
                withId(new Seat(venue, "C", "1", 1), 1002L));

        SeatMapResponse seatMap = mapper.toSeatMap(event, seats);

        assertThat(seatMap.seats()).hasSize(1);
        assertThat(seatMap.seats().get(0).section()).isEqualTo("A");
    }

    @Test
    void toSeatMapPreservesTheOrderTheSeatsWereGivenIn() {
        Venue venue = demoArena();
        Event event = neonNights(venue);
        List<Seat> seats = List.of(
                withId(new Seat(venue, "A", "1", 2), 1003L),
                withId(new Seat(venue, "A", "1", 1), 1004L),
                withId(new Seat(venue, "B", "1", 1), 1005L));

        SeatMapResponse seatMap = mapper.toSeatMap(event, seats);

        assertThat(seatMap.seats()).extracting("seatId").containsExactly(1003L, 1004L, 1005L);
    }
}
