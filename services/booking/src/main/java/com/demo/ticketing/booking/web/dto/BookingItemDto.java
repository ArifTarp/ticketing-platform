package com.demo.ticketing.booking.web.dto;

import java.math.BigDecimal;

/** One held/sold seat in a booking: {@code price} is the snapshot taken at hold time. */
public record BookingItemDto(Long seatId, BigDecimal price) {
}
