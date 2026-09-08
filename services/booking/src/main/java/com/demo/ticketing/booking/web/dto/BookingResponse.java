package com.demo.ticketing.booking.web.dto;

import com.demo.ticketing.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Wire shape for a booking: the "my tickets" list/detail screens' data source. */
public record BookingResponse(Long id,
                              Long userId,
                              Long eventId,
                              BookingStatus status,
                              BigDecimal total,
                              Instant createdAt,
                              Instant expiresAt,
                              List<BookingItemDto> items) {
}
