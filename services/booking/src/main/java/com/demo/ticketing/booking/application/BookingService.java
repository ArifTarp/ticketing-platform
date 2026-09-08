package com.demo.ticketing.booking.application;

import com.demo.ticketing.booking.application.exception.BookingNotFoundException;
import com.demo.ticketing.booking.application.mapper.BookingMapper;
import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.infra.BookingRepository;
import com.demo.ticketing.booking.web.dto.BookingResponse;
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

    public BookingService(BookingRepository bookingRepository, BookingMapper bookingMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId) {
        return bookingMapper.toResponse(loadBooking(bookingId));
    }

    /** {@code status == null} means "any status" for this user. */
    @Transactional(readOnly = true)
    public List<BookingResponse> listBookings(Long userId, BookingStatus status) {
        List<Booking> bookings = status == null
                ? bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : bookingRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        return bookingMapper.toResponses(bookings);
    }

    private Booking loadBooking(Long bookingId) {
        return bookingRepository.findWithItemsById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }
}
