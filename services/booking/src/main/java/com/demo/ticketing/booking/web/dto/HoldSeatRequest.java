package com.demo.ticketing.booking.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One seat the caller wants held, plus the price to snapshot onto the resulting
 * {@code BookingItem} (business-rules.md: "price snapshot at time of hold, not looked up later").
 *
 * <p><strong>Known gap — client-supplied price:</strong> per ADR-0001/root {@code CLAUDE.md}, booking
 * may never call event's REST API directly or read its database, and no Kafka catalog feed exists
 * yet (Phase 8's topics are payment/booking events only — see root {@code CLAUDE.md}'s topic table).
 * The frontend's "Seat map contract" already requires the client to call
 * {@code GET /api/v1/events/{eventId}/seats} (event, via gateway) before it can render seat prices
 * at all, so this endpoint accepts the price the client already has rather than re-deriving it
 * through a channel the architecture doesn't yet provide. This mirrors this service's existing
 * {@code userId} JWT-deferral precedent (see {@code services/booking/CLAUDE.md}) rather than
 * inventing a forbidden cross-service call. It is a deliberate, documented demo-scope
 * simplification, not a decision to trust client input in a production system — flagged for
 * {@code workflow-rules}/{@code backend-architecture} to pick a real fix (most likely: event
 * publishes a price-tier snapshot the booking service consumes/caches via Kafka) before this goes
 * anywhere near real money.
 */
public record HoldSeatRequest(
        @NotNull Long seatId,
        @NotNull @DecimalMin(value = "0.0", message = "price must not be negative") BigDecimal price) {
}
