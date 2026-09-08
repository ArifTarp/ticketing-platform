package com.demo.ticketing.booking.application;

import com.demo.ticketing.booking.application.event.BookingCancelledInternalEvent;
import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingItem;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.domain.SagaState;
import com.demo.ticketing.booking.domain.SeatAvailability;
import com.demo.ticketing.booking.infra.BookingItemRepository;
import com.demo.ticketing.booking.infra.BookingRepository;
import com.demo.ticketing.booking.infra.SagaStateRepository;
import com.demo.ticketing.booking.infra.SeatAvailabilityRepository;
import com.demo.ticketing.booking.infra.redis.SeatHoldLockService;
import com.demo.ticketing.messaging.event.BookingCancelledEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Step 5 of the checkout saga (root CLAUDE.md): "if the hold TTL expires before payment, a
 * scheduled sweep cancels the booking, releases seats, publishes {@code BookingCancelled}." This
 * is what closes the "stale HELD row after Redis TTL elapses" gap noted in
 * {@code services/booking/CLAUDE.md} -- without this, a booking whose hold expired would stay
 * {@code PENDING} forever with no path back to {@code AVAILABLE}.
 */
@Service
public class HoldExpirySweepService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpirySweepService.class);

    private static final String STEP_EXPIRED = "EXPIRED";
    private static final String STATUS_DONE = "DONE";
    private static final String REASON_HOLD_EXPIRED = "HOLD_EXPIRED";

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final SagaStateRepository sagaStateRepository;
    private final SeatHoldLockService seatHoldLockService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public HoldExpirySweepService(BookingRepository bookingRepository,
                                   BookingItemRepository bookingItemRepository,
                                   SeatAvailabilityRepository seatAvailabilityRepository,
                                   SagaStateRepository sagaStateRepository,
                                   SeatHoldLockService seatHoldLockService,
                                   ApplicationEventPublisher eventPublisher,
                                   PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.seatAvailabilityRepository = seatAvailabilityRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.seatHoldLockService = seatHoldLockService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${booking.sweep.interval-ms:30000}")
    public void sweepExpiredHolds() {
        List<Booking> expired = bookingRepository.findByStatusAndExpiresAtBefore(BookingStatus.PENDING, Instant.now());
        for (Booking booking : expired) {
            try {
                // Explicit TransactionTemplate, not a `@Transactional` method on `this`: an
                // internal call to an `@Transactional` method on the same bean instance bypasses
                // the Spring proxy and would silently run non-transactionally (the same
                // self-invocation pitfall BookingHoldService documents).
                transactionTemplate.executeWithoutResult(status -> expireOne(booking.getId()));
            } catch (RuntimeException ex) {
                // One booking's failure must never abort the sweep for the rest of the batch.
                log.error("Failed to expire booking {} in hold-expiry sweep", booking.getId(), ex);
            }
        }
    }

    private void expireOne(Long bookingId) {
        int updated = bookingRepository.transitionFromPending(bookingId, BookingStatus.EXPIRED);
        if (updated == 0) {
            // Race guard: the payment-result consumer (or a previous sweep tick, for a booking
            // this instance re-read before its own prior update committed) already terminated it.
            log.info("Booking {} already left PENDING before the sweep could expire it, skipping", bookingId);
            return;
        }

        Booking booking = bookingRepository.findWithItemsById(bookingId).orElseThrow();
        for (BookingItem item : bookingItemRepository.findByBookingId(bookingId)) {
            seatHoldLockService.forceRelease(booking.getEventId(), item.getSeatId());
            seatAvailabilityRepository.findByEventIdAndSeatId(booking.getEventId(), item.getSeatId())
                    .ifPresent(SeatAvailability::markAvailable);
        }

        // A booking can expire without ever reaching checkout, so SagaState may not exist yet.
        SagaState sagaState = sagaStateRepository.findByBookingId(bookingId).orElse(null);
        if (sagaState == null) {
            sagaStateRepository.save(new SagaState(bookingId, STEP_EXPIRED, STATUS_DONE));
        } else {
            sagaState.transitionTo(STEP_EXPIRED, STATUS_DONE);
        }

        BookingCancelledEvent event = new BookingCancelledEvent(
                UUID.randomUUID(), bookingId, booking.getUserId(), REASON_HOLD_EXPIRED, Instant.now());
        eventPublisher.publishEvent(new BookingCancelledInternalEvent(event));
    }
}
