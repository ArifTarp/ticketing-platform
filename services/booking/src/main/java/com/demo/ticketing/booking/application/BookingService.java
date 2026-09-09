package com.demo.ticketing.booking.application;

import com.demo.ticketing.booking.application.exception.BookingNotFoundException;
import com.demo.ticketing.booking.application.mapper.BookingMapper;
import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.domain.SeatAvailability;
import com.demo.ticketing.booking.infra.BookingRepository;
import com.demo.ticketing.booking.infra.SeatAvailabilityRepository;
import com.demo.ticketing.booking.web.dto.BookingResponse;
import com.demo.ticketing.booking.web.dto.SeatAvailabilityResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only booking use cases (Phase 7 CRUD slice). Mutating use cases — hold, checkout, the saga
 * consumers, the timeout sweep — are built by {@code saga-orchestrator} on top of this schema.
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final SeatAvailabilityRepository seatAvailabilityRepository;

    public BookingService(BookingRepository bookingRepository,
                           BookingMapper bookingMapper,
                           SeatAvailabilityRepository seatAvailabilityRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.seatAvailabilityRepository = seatAvailabilityRepository;
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId) {
        return bookingMapper.toResponse(loadBooking(bookingId));
    }

    /**
     * {@code status == null} means "any status" for this user; {@code eventId == null} means "any
     * event". {@code eventId + status=PENDING} is what the frontend uses on page refresh to look up
     * a user's in-progress hold for one event — see {@code BookingRepository}'s javadoc for why
     * that combination is guaranteed at most one row.
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> listBookings(Long userId, Long eventId, BookingStatus status) {
        List<Booking> bookings;
        if (eventId == null) {
            bookings = status == null
                    ? bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                    : bookingRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            bookings = status == null
                    ? bookingRepository.findByUserIdAndEventIdOrderByCreatedAtDesc(userId, eventId)
                    : bookingRepository.findByUserIdAndEventIdAndStatusOrderByCreatedAtDesc(userId, eventId, status);
        }
        return bookingMapper.toResponses(bookings);
    }

    /** Backs {@code GET /api/v1/bookings/availability?eventId=} -- business-rules.md's "Seat map contract". */
    @Transactional(readOnly = true)
    public List<SeatAvailabilityResponse> getAvailability(Long eventId) {
        return seatAvailabilityRepository.findByEventId(eventId).stream()
                .map(this::toAvailabilityResponse)
                .toList();
    }

    private SeatAvailabilityResponse toAvailabilityResponse(SeatAvailability availability) {
        return new SeatAvailabilityResponse(availability.getSeatId(), availability.getStatus());
    }

    private Booking loadBooking(Long bookingId) {
        return bookingRepository.findWithItemsById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }
}
