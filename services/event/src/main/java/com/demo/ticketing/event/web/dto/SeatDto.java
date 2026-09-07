package com.demo.ticketing.event.web.dto;

/**
 * One seat in the static seat map: position + the price tier it is sold at for this event.
 *
 * <p><strong>ADR-0001 — do not add an availability/sold/held field to this record.</strong> The
 * frontend merges this layout with {@code GET /api/v1/bookings/availability?eventId=...} from the
 * booking service, client-side, by {@code seatId}. {@code SeatDtoContractTest} fails the build if
 * this record grows a seat-state field.
 */
public record SeatDto(Long seatId, String section, String row, Integer number, SeatCategoryDto seatCategory) {
}
