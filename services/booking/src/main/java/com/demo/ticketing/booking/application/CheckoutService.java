package com.demo.ticketing.booking.application;

import com.demo.ticketing.booking.application.event.PaymentRequestedInternalEvent;
import com.demo.ticketing.booking.application.exception.BookingNotFoundException;
import com.demo.ticketing.booking.application.exception.BookingNotPendingException;
import com.demo.ticketing.booking.application.mapper.BookingMapper;
import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingStatus;
import com.demo.ticketing.booking.domain.SagaState;
import com.demo.ticketing.booking.infra.BookingRepository;
import com.demo.ticketing.booking.infra.SagaStateRepository;
import com.demo.ticketing.booking.web.dto.BookingResponse;
import com.demo.ticketing.messaging.command.PaymentRequestedCommand;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Step 2 of the checkout saga (root CLAUDE.md): starts the saga for an existing {@code PENDING}
 * booking by recording {@code SagaState(PAYMENT_REQUESTED, IN_PROGRESS)} and raising the
 * publish-after-commit internal event that {@code KafkaOutboxPublisher} turns into a real
 * {@code PaymentRequestedCommand} on {@code payment.commands} once this transaction commits.
 */
@Service
public class CheckoutService {

    private static final String STEP_PAYMENT_REQUESTED = "PAYMENT_REQUESTED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final BookingRepository bookingRepository;
    private final SagaStateRepository sagaStateRepository;
    private final BookingMapper bookingMapper;
    private final ApplicationEventPublisher eventPublisher;

    public CheckoutService(BookingRepository bookingRepository,
                            SagaStateRepository sagaStateRepository,
                            BookingMapper bookingMapper,
                            ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.bookingMapper = bookingMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public BookingResponse checkout(Long bookingId) {
        Booking booking = bookingRepository.findWithItemsById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingNotPendingException(bookingId, "status is " + booking.getStatus());
        }
        // Checkout raced the sweep and lost: the hold has technically expired even though the
        // sweep hasn't run/committed EXPIRED yet. Treat it the same as not-pending rather than
        // starting a saga for a booking that's about to be swept out from under it.
        if (booking.getExpiresAt().isBefore(Instant.now())) {
            throw new BookingNotPendingException(bookingId, "hold has already expired");
        }

        SagaState sagaState = sagaStateRepository.findByBookingId(bookingId).orElse(null);
        if (sagaState != null
                && STEP_PAYMENT_REQUESTED.equals(sagaState.getStep())
                && STATUS_IN_PROGRESS.equals(sagaState.getStatus())) {
            // A checkout for this booking is already in flight: the original
            // PaymentRequestedCommand is presumably still on its way through the saga (a lost-
            // response retry of this same POST would otherwise mint and publish a second distinct
            // command, since the booking itself stays PENDING and nothing else blocks a second
            // call). Don't create a new SagaState row or publish another command -- just return
            // the current booking state.
            return bookingMapper.toResponse(booking);
        }

        if (sagaState == null) {
            sagaStateRepository.save(new SagaState(bookingId, STEP_PAYMENT_REQUESTED, STATUS_IN_PROGRESS));
        } else {
            // sagaState exists but is in some other step/status (e.g. left over from a prior,
            // already-terminated saga attempt) -- move it back to PAYMENT_REQUESTED/IN_PROGRESS
            // in place rather than inserting a duplicate, since booking_id is unique.
            sagaState.transitionTo(STEP_PAYMENT_REQUESTED, STATUS_IN_PROGRESS);
        }

        PaymentRequestedCommand command = new PaymentRequestedCommand(
                UUID.randomUUID(), booking.getId(), booking.getUserId(), booking.getTotal(), Instant.now());
        eventPublisher.publishEvent(new PaymentRequestedInternalEvent(command));

        return bookingMapper.toResponse(booking);
    }
}
