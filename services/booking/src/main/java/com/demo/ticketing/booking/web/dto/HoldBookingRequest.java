package com.demo.ticketing.booking.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /api/v1/bookings/hold}. {@code userId} is a plain required field, not
 * JWT-derived — mirrors {@code BookingController}'s existing {@code userId} query-param deferral
 * for the read endpoints (see {@code services/booking/CLAUDE.md}'s "userId=me note"); this service
 * does not parse JWTs in this phase.
 *
 * <p>{@code @Size(max = 6)} enforces business-rules.md's "Max 6 seats per booking" declaratively;
 * {@code BookingHoldService} additionally rejects duplicate {@code seatId}s within one request
 * (not expressible as a bean-validation annotation).
 */
public record HoldBookingRequest(
        @NotNull Long userId,
        @NotNull Long eventId,
        @NotEmpty
        @Size(max = 6, message = "a booking may hold at most 6 seats")
        @Valid
        List<HoldSeatRequest> seats) {
}
