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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final Logger log = LoggerFactory.getLogger(BookingHoldService.class);

    /** business-rules.md: max 6 seats per booking, enforced cumulatively per ADR-0004. */
    private static final int MAX_SEATS_PER_BOOKING = 6;

    /**
     * Bound on the ADR-0004 TOCTOU retry loop (see {@link #persistHoldWithRetry}). One retry
     * resolves the realistic case (a single concurrent competitor for the same
     * {@code (userId, eventId)} pair); a small bound beyond that only guards against pathological
     * repeated contention rather than looping forever.
     */
    private static final int MAX_PENDING_BOOKING_INSERT_ATTEMPTS = 5;

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
            Booking booking = persistHoldWithRetry(request);
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
     * Runs {@link #persistHold} inside its own fresh transaction via {@link #transactionTemplate},
     * retrying (with a fresh find-or-create lookup, not a naive re-run of the same failed insert)
     * when a concurrent request for the same {@code (userId, eventId)} pair wins the DB-level race
     * guard added for this bug fix: a partial unique index, {@code uq_bookings_user_event_pending}
     * on {@code bookings(user_id, event_id) WHERE status = 'PENDING'} (see the new Flyway
     * migration). {@link #persistHold}'s find-then-create check on
     * {@code findFirstByUserIdAndEventIdAndStatus} is a classic TOCTOU race: the Redis lock only
     * serializes concurrent holds per {@code (eventId, seatId)}, never per {@code (userId,
     * eventId)}, so two concurrent hold requests for two <em>different</em> seats from the same
     * user/event can each see "no existing PENDING booking" before either's INSERT commits and each
     * try to create their own separate one-seat booking — silently defeating ADR-0004's whole point.
     *
     * <p>The DB index makes the loser's {@code bookingRepository.save(booking)} call (IDENTITY
     * generation forces an immediate, synchronous {@code INSERT}) fail with a unique-violation,
     * translated by Spring Data to {@link DataIntegrityViolationException}. {@link TransactionTemplate}
     * has already rolled back that attempt's entire transaction (including any {@code
     * SeatAvailability} rows marked {@code HELD} earlier in the same attempt) by the time this
     * catches the exception, so retrying in a brand-new transaction is safe: this request's Redis
     * locks are still held (unaffected by the DB rollback), and the retry's fresh
     * {@code findFirstByUserIdAndEventIdAndStatus} lookup now sees the winner's already-committed
     * booking and appends this request's seat(s) to it — the loser ends up correctly merged into one
     * shared multi-seat booking instead of the request failing outright.
     */
    private Booking persistHoldWithRetry(HoldBookingRequest request) {
        for (int attempt = 1; attempt <= MAX_PENDING_BOOKING_INSERT_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> persistHold(request));
            } catch (DataIntegrityViolationException raceLoss) {
                if (attempt == MAX_PENDING_BOOKING_INSERT_ATTEMPTS) {
                    throw raceLoss;
                }
                log.info("Lost the (userId={}, eventId={}) PENDING-booking race on attempt {}, retrying "
                                + "against the winner's now-committed booking",
                        request.userId(), request.eventId(), attempt);
            }
        }
        // Unreachable: the loop above always either returns or throws on its last attempt.
        throw new IllegalStateException("persistHoldWithRetry exhausted attempts without returning or throwing");
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
