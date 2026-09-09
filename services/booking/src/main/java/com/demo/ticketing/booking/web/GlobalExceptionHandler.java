package com.demo.ticketing.booking.web;

import com.demo.ticketing.booking.application.exception.BookingContentionException;
import com.demo.ticketing.booking.application.exception.BookingNotFoundException;
import com.demo.ticketing.booking.application.exception.BookingNotPendingException;
import com.demo.ticketing.booking.application.exception.InvalidHoldRequestException;
import com.demo.ticketing.booking.application.exception.SeatUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates domain/validation exceptions into RFC 7807 {@code application/problem+json} bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBookingNotFound(BookingNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** The Redis-lock-resolved concurrent seat-race path — see {@code BookingHoldService}. */
    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleSeatUnavailable(SeatUnavailableException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidHoldRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidHoldRequest(InvalidHoldRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** {@code POST /bookings/{id}/checkout} on a non-PENDING or already-expired booking. */
    @ExceptionHandler(BookingNotPendingException.class)
    public ResponseEntity<ProblemDetail> handleBookingNotPending(BookingNotPendingException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * {@code BookingHoldService.persistHoldWithRetry} exhausted its retries on the genuine
     * {@code uq_bookings_user_event_pending} PENDING-booking race — real, if rare, contention, not
     * a bug.
     */
    @ExceptionHandler(BookingContentionException.class)
    public ResponseEntity<ProblemDetail> handleBookingContention(BookingContentionException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Safety net for a {@link DataIntegrityViolationException} that is NOT the
     * {@code uq_bookings_user_event_pending} race (see {@code BookingHoldService.persistHoldWithRetry})
     * — an unexpected DB constraint violation. Degrades to problem+json instead of Spring Boot's
     * default error body; the raw SQL exception message is deliberately not exposed to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected data integrity error occurred");
    }

    /** Bean-validation failures on {@code @Valid @RequestBody} DTOs, e.g. {@code HoldBookingRequest}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    /** e.g. a non-numeric {@code id}/{@code userId} or an unrecognized {@code status} value. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue());
    }

    /** {@code userId} is required — there is no JWT-derived default in this phase (see CLAUDE.md). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
