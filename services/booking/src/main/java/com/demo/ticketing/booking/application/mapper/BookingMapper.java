package com.demo.ticketing.booking.application.mapper;

import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingItem;
import com.demo.ticketing.booking.web.dto.BookingItemDto;
import com.demo.ticketing.booking.web.dto.BookingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps booking entities to the wire DTOs. Never expose {@link Booking}/{@link BookingItem} directly. */
@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        List<BookingItemDto> items = booking.getItems().stream()
                .map(this::toItemDto)
                .toList();
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getEventId(),
                booking.getStatus(),
                booking.getTotal(),
                booking.getCreatedAt(),
                booking.getExpiresAt(),
                items);
    }

    public List<BookingResponse> toResponses(List<Booking> bookings) {
        return bookings.stream().map(this::toResponse).toList();
    }

    private BookingItemDto toItemDto(BookingItem item) {
        return new BookingItemDto(item.getSeatId(), item.getPrice());
    }
}
