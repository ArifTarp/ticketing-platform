package com.demo.ticketing.event.web;

import com.demo.ticketing.event.application.exception.EventNotFoundException;
import com.demo.ticketing.event.application.exception.InvalidSeatCategoryRequestException;
import com.demo.ticketing.event.application.exception.SeatCategoryConflictException;
import com.demo.ticketing.event.application.exception.VenueNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates domain/validation exceptions into RFC 7807 {@code application/problem+json} bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEventNotFound(EventNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** {@code POST /events} with a {@code venueId} that does not exist. */
    @ExceptionHandler(VenueNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleVenueNotFound(VenueNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** {@code POST /events/{eventId}/seat-categories} with an empty request body. */
    @ExceptionHandler(InvalidSeatCategoryRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSeatCategoryRequest(
            InvalidSeatCategoryRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * {@code POST /events/{eventId}/seat-categories} reusing a name/section already used by that
     * event ({@code uq_seat_categories_event_name}/{@code uq_seat_categories_event_section}).
     */
    @ExceptionHandler(SeatCategoryConflictException.class)
    public ResponseEntity<ProblemDetail> handleSeatCategoryConflict(SeatCategoryConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Safety net for any other unexpected DB constraint violation on a write endpoint — degrades to
     * problem+json instead of Spring Boot's default error body; the raw SQL exception message is
     * deliberately not exposed to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected data integrity error occurred");
    }

    /** Malformed JSON or an invalid enum literal (e.g. an unknown {@code status}) in a request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    /** e.g. a non-numeric {@code eventId} or an unparseable {@code from}/{@code to} instant. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
