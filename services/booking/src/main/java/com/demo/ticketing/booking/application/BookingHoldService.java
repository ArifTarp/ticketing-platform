package com.demo.ticketing.booking.application;

import com.demo.ticketing.booking.application.exception.InvalidHoldRequestException;
import com.demo.ticketing.booking.application.exception.SeatUnavailableException;
import com.demo.ticketing.booking.application.mapper.BookingMapper;
import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingItem;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.domain.SeatAvailability;
import com.demo.ticketing.booking.domain.SeatAvailabilityStatus;
import com.demo.ticketing.booking.infra.BookingRepository;
import com.demo.ticketing.booking.infra.SeatAvailabilityRepository;
import com.demo.ticketing.booking.infra.redis.SeatHoldLockService;
import com.demo.ticketing.booking.web.dto.BookingResponse;
import com.demo.ticketing.booking.web.dto.HoldBookingRequest;
import com.demo.ticketing.booking.web.dto.HoldSeatRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The concurrency-sensitive write path for Phase 7: {@code POST /api/v1/bookings/hold}. Implements
 * business-rules.md's "concurrent seat-race" edge path — the Redis lock ({@link SeatHoldLockService})
 * is the single source of truth for who wins a race on one seat; {@link SeatAvailability} is the
 * durable record of the outcome. See {@code services/booking/CLAUDE.md} for the full design writeup.
 *
 * <p>Deliberately a separate bean from the read-only {@link BookingService}: the Redis calls in
 * {@link #holdSeats} must run <em>outside</em> any DB transaction (so a slow/blocked Redis call never
 * holds a DB connection/lock open), while the actual persistence step must be one atomic transaction
 * (business-rules.md: "no partial holds"). Using {@link TransactionTemplate} explicitly — rather than
 * a {@code @Transactional} method on {@code this} — sidesteps the classic Spring self-invocation
 * proxy pitfall (an internal call to an {@code @Transactional} method on the same bean instance
 * bypasses the proxy and silently runs non-transactionally).
 */
@Service
public class BookingHoldService {

    /** business-rules.md: max 6 seats per booking, enforced cumulatively per ADR-0004. */
    private static final int MAX_SEATS_PER_BOOKING = 6;

    private final BookingRepository bookingRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final SeatHoldLockService lockService;
    private final BookingMapper bookingMapper;
    private final TransactionTemplate transactionTemplate;

    public BookingHoldService(BookingRepository bookingRepository,
                               SeatAvailabilityRepository seatAvailabilityRepository,
                               SeatHoldLockService lockService,
                               BookingMapper bookingMapper,
                               PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.seatAvailabilityRepository = seatAvailabilityRepository;
        this.lockService = lockService;
        this.bookingMapper = bookingMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Acquires the Redis lock for every requested seat (fail-fast: the first seat that is already
     * locked aborts the whole request and releases anything already acquired), then persists the
     * {@code Booking}/{@code BookingItem}s/{@code SeatAvailability} rows in one DB transaction. Any
     * failure — lock contention or a defensive DB-state conflict — rejects the entire request with
     * {@link SeatUnavailableException} (409) and leaves no partial state in either Redis or Postgres.
     */
    public BookingResponse holdSeats(HoldBookingRequest request) {
        List<Long> seatIds = distinctSeatIds(request);
        String holdToken = UUID.randomUUID().toString();
        List<Long> acquiredSeatIds = new ArrayList<>();
        try {
            for (Long seatId : seatIds) {
                if (!lockService.tryAcquire(request.eventId(), seatId, holdToken)) {
                    throw new SeatUnavailableException(List.of(seatId));
                }
                acquiredSeatIds.add(seatId);
            }
            Booking booking = transactionTemplate.execute(status -> persistHold(request));
            return bookingMapper.toResponse(booking);
        } catch (SeatUnavailableException ex) {
            // Whole-request rollback per business-rules.md's concurrent seat-race path: release
            // every lock this request acquired, whether the loop above or persistHold below is what
            // ultimately rejected it. Booking/BookingItem/SeatAvailability writes are already rolled
            // back by TransactionTemplate on the RuntimeException before we get here.
            for (Long seatId : acquiredSeatIds) {
                lockService.release(request.eventId(), seatId, holdToken);
            }
            throw ex;
        }
    }

    private List<Long> distinctSeatIds(HoldBookingRequest request) {
        List<Long> seatIds = request.seats().stream().map(HoldSeatRequest::seatId).toList();
        Set<Long> distinct = new LinkedHashSet<>(seatIds);
        if (distinct.size() != seatIds.size()) {
            throw new InvalidHoldRequestException("Duplicate seatId within the same hold request");
        }
        return List.copyOf(distinct);
    }

    /**
     * Runs inside {@link #transactionTemplate}. Only ever reached once every seat's Redis lock is
     * already held by this request, so the DB check below can never race with another concurrent
     * hold attempt on the same seat — it exists purely as defense in depth against
     * {@code SeatAvailability} disagreeing with Redis (e.g. a still-{@code HELD} row whose Redis key
     * already expired because the Phase 8 timeout sweep doesn't exist yet — see
     * {@code services/booking/CLAUDE.md}).
     *
     * <p>ADR-0004: looks up an existing {@code PENDING} booking for this {@code (userId, eventId)}
     * pair and appends the newly-locked seats to it (recomputing {@code total} and extending
     * {@code expiresAt}) instead of always creating a second {@code Booking} row. The max-6-seats
     * rule is enforced cumulatively against that existing booking's item count, not per call.
     */
    private Booking persistHold(HoldBookingRequest request) {
        Instant expiresAt = Instant.now().plus(SeatHoldLockService.HOLD_TTL);

        Booking booking = bookingRepository
                .findFirstByUserIdAndEventIdAndStatus(request.userId(), request.eventId(), BookingStatus.PENDING)
                .orElse(null);

        int existingSeatCount = booking == null ? 0 : booking.getItems().size();
        if (existingSeatCount + request.seats().size() > MAX_SEATS_PER_BOOKING) {
            throw new InvalidHoldRequestException(
                    "a booking may hold at most " + MAX_SEATS_PER_BOOKING + " seats");
        }

        boolean isNewBooking = booking == null;
        if (isNewBooking) {
            booking = new Booking(request.userId(), request.eventId(), BigDecimal.ZERO, expiresAt);
        }

        for (HoldSeatRequest seat : request.seats()) {
            SeatAvailability availability = seatAvailabilityRepository
                    .findByEventIdAndSeatId(request.eventId(), seat.seatId())
                    .orElse(null);
            if (availability == null) {
                seatAvailabilityRepository.save(
                        new SeatAvailability(request.eventId(), seat.seatId(), SeatAvailabilityStatus.HELD));
            } else if (availability.getStatus() == SeatAvailabilityStatus.AVAILABLE) {
                availability.markHeld();
            } else {
                throw new SeatUnavailableException(List.of(seat.seatId()));
            }
            booking.addItem(new BookingItem(booking, seat.seatId(), seat.price()));
        }

        booking.recalculateTotal();
        if (!isNewBooking) {
            booking.extendExpiry(expiresAt);
        }

        return bookingRepository.save(booking);
    }
}
