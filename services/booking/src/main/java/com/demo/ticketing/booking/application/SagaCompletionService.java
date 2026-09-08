package com.demo.ticketing.booking.application;

import com.demo.ticketing.booking.application.event.BookingCancelledInternalEvent;
import com.demo.ticketing.booking.application.event.BookingConfirmedInternalEvent;
import com.demo.ticketing.booking.domain.BookingItem;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.domain.SagaState;
import com.demo.ticketing.booking.domain.SeatAvailability;
import com.demo.ticketing.booking.domain.SeatAvailabilityStatus;
import com.demo.ticketing.booking.infra.BookingItemRepository;
import com.demo.ticketing.booking.infra.BookingRepository;
import com.demo.ticketing.booking.infra.ProcessedMessage;
import com.demo.ticketing.booking.infra.ProcessedMessageRepository;
import com.demo.ticketing.booking.infra.SagaStateRepository;
import com.demo.ticketing.booking.infra.SeatAvailabilityRepository;
import com.demo.ticketing.booking.infra.redis.SeatHoldLockService;
import com.demo.ticketing.messaging.event.BookingCancelledEvent;
import com.demo.ticketing.messaging.event.BookingConfirmedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Steps 4 (completion) of the checkout saga (root CLAUDE.md): reacts to
 * {@code PaymentCompletedEvent}/{@code PaymentFailedEvent} consumed from {@code payment.events} by
 * {@code PaymentResultListener}. Each public method here is one {@code @Transactional} unit of
 * work: dedupe -> race-guarded status transition -> seat mutation -> {@code SagaState} update ->
 * publish-after-commit internal event.
 */
@Service
public class SagaCompletionService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompletionService.class);

    private static final String STEP_CONFIRMED = "CONFIRMED";
    private static final String STEP_CANCELLED = "CANCELLED";
    private static final String STATUS_DONE = "DONE";

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final SagaStateRepository sagaStateRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final SeatHoldLockService seatHoldLockService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public SagaCompletionService(BookingRepository bookingRepository,
                                  BookingItemRepository bookingItemRepository,
                                  SeatAvailabilityRepository seatAvailabilityRepository,
                                  SagaStateRepository sagaStateRepository,
                                  ProcessedMessageRepository processedMessageRepository,
                                  SeatHoldLockService seatHoldLockService,
                                  ApplicationEventPublisher eventPublisher,
                                  PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.seatAvailabilityRepository = seatAvailabilityRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.seatHoldLockService = seatHoldLockService;
        this.eventPublisher = eventPublisher;
        // ADR-0005's canonical idempotency-claim pattern: a dedicated REQUIRES_NEW template, not
        // @Transactional(propagation = REQUIRES_NEW) on a sibling method of this same bean (that
        // form of self-invocation bypasses the Spring AOP proxy). See
        // services/payment/.../PaymentProcessingService.tryClaimMessage for the reference
        // implementation this mirrors.
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public void onCompleted(UUID messageId, Long bookingId) {
        if (!tryClaimMessage(messageId)) {
            log.info("Duplicate PaymentCompletedEvent messageId={} bookingId={}, skipping", messageId, bookingId);
            return;
        }

        int updated = bookingRepository.transitionFromPending(bookingId, BookingStatus.CONFIRMED);
        if (updated == 0) {
            log.info("Booking {} already left PENDING (race with sweep/duplicate), skipping completion", bookingId);
            return;
        }

        var booking = bookingRepository.findWithItemsById(bookingId).orElseThrow();
        List<Long> seatIds = seatIdsOf(bookingId);
        for (Long seatId : seatIds) {
            markSold(booking.getEventId(), seatId);
        }

        upsertSagaState(bookingId, STEP_CONFIRMED, STATUS_DONE);

        BookingConfirmedEvent event = new BookingConfirmedEvent(
                UUID.randomUUID(), bookingId, booking.getUserId(), seatIds, Instant.now());
        eventPublisher.publishEvent(new BookingConfirmedInternalEvent(event));
    }

    @Transactional
    public void onFailed(UUID messageId, Long bookingId, String reason) {
        if (!tryClaimMessage(messageId)) {
            log.info("Duplicate PaymentFailedEvent messageId={} bookingId={}, skipping", messageId, bookingId);
            return;
        }

        int updated = bookingRepository.transitionFromPending(bookingId, BookingStatus.CANCELLED);
        if (updated == 0) {
            log.info("Booking {} already left PENDING (race with sweep/duplicate), skipping cancellation", bookingId);
            return;
        }

        var booking = bookingRepository.findWithItemsById(bookingId).orElseThrow();
        List<Long> seatIds = seatIdsOf(bookingId);
        for (Long seatId : seatIds) {
            releaseSeat(booking.getEventId(), seatId);
        }

        upsertSagaState(bookingId, STEP_CANCELLED, STATUS_DONE);

        BookingCancelledEvent event = new BookingCancelledEvent(
                UUID.randomUUID(), bookingId, booking.getUserId(), "PAYMENT_FAILED", Instant.now());
        eventPublisher.publishEvent(new BookingCancelledInternalEvent(event));
    }

    /**
     * Attempts to claim {@code messageId} as newly-seen, returning {@code true} iff this call is
     * the one that gets to process it. Canonical ADR-0005 pattern: an {@code existsById} fast path,
     * then — if absent — an insert run in its own {@code REQUIRES_NEW} transaction/connection via
     * {@link #requiresNewTransactionTemplate}, so a lost id-uniqueness race (two redeliveries
     * processed concurrently) only aborts that isolated sub-transaction rather than poisoning this
     * method's outer transaction on Postgres. See {@code PaymentProcessingService.tryClaimMessage}
     * (the reference implementation this mirrors) for the full rationale on why a plain
     * {@code save()}/{@code saveAndFlush()} + catch inside the caller's own transaction is unsafe.
     */
    private boolean tryClaimMessage(UUID messageId) {
        if (processedMessageRepository.existsById(messageId)) {
            return false;
        }
        try {
            Boolean claimed = requiresNewTransactionTemplate.execute(status -> {
                processedMessageRepository.saveAndFlush(new ProcessedMessage(messageId));
                return true;
            });
            return Boolean.TRUE.equals(claimed);
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private List<Long> seatIdsOf(Long bookingId) {
        return bookingItemRepository.findByBookingId(bookingId).stream()
                .map(BookingItem::getSeatId)
                .toList();
    }

    private void markSold(Long eventId, Long seatId) {
        Optional<SeatAvailability> existing = seatAvailabilityRepository.findByEventIdAndSeatId(eventId, seatId);
        if (existing.isPresent()) {
            existing.get().markSold();
        } else {
            // Defensive: should already exist as HELD from the hold step, but never leave a sold
            // seat unrecorded.
            SeatAvailability availability = new SeatAvailability(eventId, seatId, SeatAvailabilityStatus.SOLD);
            seatAvailabilityRepository.save(availability);
        }
        // Redis TTL expiring naturally on a SOLD seat is harmless (services/booking/CLAUDE.md).
    }

    private void releaseSeat(Long eventId, Long seatId) {
        seatHoldLockService.forceRelease(eventId, seatId);
        seatAvailabilityRepository.findByEventIdAndSeatId(eventId, seatId)
                .ifPresent(SeatAvailability::markAvailable);
    }

    private void upsertSagaState(Long bookingId, String step, String status) {
        SagaState sagaState = sagaStateRepository.findByBookingId(bookingId).orElse(null);
        if (sagaState == null) {
            sagaStateRepository.save(new SagaState(bookingId, step, status));
        } else {
            sagaState.transitionTo(step, status);
        }
    }
}
