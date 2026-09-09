package com.demo.ticketing.booking.web;

import com.demo.ticketing.booking.application.BookingHoldService;
import com.demo.ticketing.booking.application.BookingService;
import com.demo.ticketing.booking.application.CheckoutService;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.web.dto.BookingResponse;
import com.demo.ticketing.booking.web.dto.HoldBookingRequest;
import com.demo.ticketing.booking.web.dto.SeatAvailabilityResponse;
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
    private final CheckoutService checkoutService;

    public BookingController(BookingService bookingService,
                              BookingHoldService bookingHoldService,
                              CheckoutService checkoutService) {
        this.bookingService = bookingService;
        this.bookingHoldService = bookingHoldService;
        this.checkoutService = checkoutService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable("id") Long id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    /**
     * Lists a single user's bookings, optionally filtered by {@code eventId} and/or {@code status}.
     * {@code userId} is a plain numeric id in this phase — see {@code services/booking/CLAUDE.md}
     * for why {@code userId=me} resolution (JWT-derived identity) is deliberately deferred, not
     * forgotten. {@code eventId + status=PENDING} is the frontend's "resume my in-progress hold on
     * page refresh" query — {@code BookingHoldService}'s append-not-duplicate invariant
     * (one {@code (userId, eventId, PENDING)} booking at a time) guarantees at most one result.
     */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> listBookings(
            @RequestParam(name = "userId") Long userId,
            @RequestParam(name = "eventId", required = false) Long eventId,
            @RequestParam(name = "status", required = false) BookingStatus status) {
        return ResponseEntity.ok(bookingService.listBookings(userId, eventId, status));
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

    /**
     * Kicks off the checkout saga (root CLAUDE.md step 2): publishes {@code PaymentRequested} to
     * {@code payment.commands} after committing {@code SagaState}. Returns the booking's current
     * (still {@code PENDING}) state -- the frontend polls {@code GET /bookings/{id}} for the
     * eventual {@code CONFIRMED}/{@code CANCELLED} outcome, per business-rules.md.
     */
    @PostMapping("/{id}/checkout")
    public ResponseEntity<BookingResponse> checkout(@PathVariable("id") Long id) {
        return ResponseEntity.ok(checkoutService.checkout(id));
    }

    /**
     * Live per-seat availability for one event (business-rules.md's "Seat map contract"): the
     * frontend merges this with event's static seat-map layout. A seat absent from this list is
     * implicitly {@code AVAILABLE}.
     */
    @GetMapping("/availability")
    public ResponseEntity<List<SeatAvailabilityResponse>> getAvailability(
            @RequestParam(name = "eventId") Long eventId) {
        return ResponseEntity.ok(bookingService.getAvailability(eventId));
    }
}
