package com.demo.ticketing.booking.web;

import com.demo.ticketing.booking.application.BookingHoldService;
import com.demo.ticketing.booking.application.BookingService;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.web.dto.BookingResponse;
import com.demo.ticketing.booking.web.dto.HoldBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Booking endpoints. The read endpoints are the Phase 7 CRUD slice (unchanged); {@code POST
 * /api/v1/bookings/hold} is the Redis-locked, concurrency-sensitive endpoint added in this same
 * phase's {@code saga-orchestrator} follow-up pass — see {@code services/booking/CLAUDE.md} for the
 * full design.
 *
 * <p>Every {@code @RequestParam}/{@code @PathVariable} names its parameter explicitly on purpose —
 * see {@code EventController}'s javadoc in the event service for why (the parent pom is a plain
 * aggregator, not {@code spring-boot-starter-parent}).
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingHoldService bookingHoldService;

    public BookingController(BookingService bookingService, BookingHoldService bookingHoldService) {
        this.bookingService = bookingService;
        this.bookingHoldService = bookingHoldService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable("id") Long id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    /**
     * Lists a single user's bookings, optionally filtered by status. {@code userId} is a plain
     * numeric id in this phase — see {@code services/booking/CLAUDE.md} for why {@code userId=me}
     * resolution (JWT-derived identity) is deliberately deferred, not forgotten.
     */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> listBookings(
            @RequestParam(name = "userId") Long userId,
            @RequestParam(name = "status", required = false) BookingStatus status) {
        return ResponseEntity.ok(bookingService.listBookings(userId, status));
    }

    /**
     * Holds up to 6 seats for one event, creating a {@code PENDING} booking. Resolves the
     * concurrent seat-race edge path via a Redis distributed lock, not a DB race — see
     * {@link BookingHoldService}. 409 if any requested seat is already locked/held/sold; 400 for
     * more than 6 seats, an empty seat list, or a duplicate {@code seatId}.
     */
    @PostMapping("/hold")
    public ResponseEntity<BookingResponse> holdSeats(@Valid @RequestBody HoldBookingRequest request) {
        return ResponseEntity.ok(bookingHoldService.holdSeats(request));
    }
}
