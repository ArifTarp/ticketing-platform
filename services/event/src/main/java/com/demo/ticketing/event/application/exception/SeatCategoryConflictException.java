package com.demo.ticketing.event.application.exception;

/**
 * Translates a {@link org.springframework.dao.DataIntegrityViolationException} on
 * {@code uq_seat_categories_event_name}/{@code uq_seat_categories_event_section} into a
 * {@code 409 Conflict} problem+json instead of leaking the raw SQL exception (see
 * {@code GlobalExceptionHandler}).
 */
public class SeatCategoryConflictException extends RuntimeException {

    public SeatCategoryConflictException(Long eventId) {
        super("One or more seat categories for event " + eventId
                + " conflict with an existing name or section");
    }
}
