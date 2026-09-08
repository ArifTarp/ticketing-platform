package com.demo.ticketing.booking.application.mapper;

import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingItem;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.web.dto.BookingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test (no Spring context, no Docker) for the entity -> DTO mapping. */
class BookingMapperTest {

    private final BookingMapper mapper = new BookingMapper();

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Test
    void toResponseMapsFieldsAndItemsNeverExposingTheEntity() {
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        Booking booking = withId(new Booking(1L, 10L, new BigDecimal("370.00"), expiresAt), 100L);
        booking.addItem(withId(new BookingItem(booking, 1000L, new BigDecimal("250.00")), 1L));
        booking.addItem(withId(new BookingItem(booking, 1001L, new BigDecimal("120.00")), 2L));

        BookingResponse response = mapper.toResponse(booking);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.eventId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.total()).isEqualByComparingTo("370.00");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).seatId()).isEqualTo(1000L);
        assertThat(response.items().get(0).price()).isEqualByComparingTo("250.00");
    }

    @Test
    void toResponseReturnsAnEmptyItemListWhenTheBookingHasNoItemsYet() {
        Booking booking = withId(
                new Booking(1L, 10L, BigDecimal.ZERO, Instant.now().plus(10, ChronoUnit.MINUTES)), 101L);

        BookingResponse response = mapper.toResponse(booking);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void toResponsesMapsEachBookingIndependently() {
        Booking first = withId(
                new Booking(1L, 10L, BigDecimal.TEN, Instant.now().plus(10, ChronoUnit.MINUTES)), 1L);
        Booking second = withId(
                new Booking(2L, 11L, BigDecimal.ONE, Instant.now().plus(10, ChronoUnit.MINUTES)), 2L);

        List<BookingResponse> responses = mapper.toResponses(List.of(first, second));

        assertThat(responses).extracting(BookingResponse::id).containsExactly(1L, 2L);
    }
}
