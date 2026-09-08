package com.demo.ticketing.booking.infra.kafka;

import com.demo.ticketing.booking.application.event.BookingCancelledInternalEvent;
import com.demo.ticketing.booking.application.event.BookingConfirmedInternalEvent;
import com.demo.ticketing.booking.application.event.PaymentRequestedInternalEvent;
import com.demo.ticketing.messaging.event.BookingCancelledEvent;
import com.demo.ticketing.messaging.event.BookingConfirmedEvent;
import com.demo.ticketing.messaging.topic.KafkaTopics;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.demo.ticketing.messaging.command.PaymentRequestedCommand;

/**
 * The concrete "outbox = publish-after-commit" mechanism (root CLAUDE.md non-negotiable: "publish
 * events only after the local DB commit"). {@code CheckoutService}/{@code SagaCompletionService}/
 * {@code HoldExpirySweepService} never call {@code KafkaTemplate.send} directly inside their
 * {@code @Transactional} methods; instead they raise the plain in-process events in
 * {@code application.event}, and the handlers below -- each {@code @TransactionalEventListener(phase
 * = AFTER_COMMIT)} -- only fire once the surrounding transaction has actually committed. If the
 * transaction rolls back, these handlers never run and nothing is published, so a crash between
 * "DB write" and "Kafka send" can never leave a message published for state that doesn't exist.
 */
@Component
public class KafkaOutboxPublisher {

    private final KafkaTemplate<String, PaymentRequestedCommand> paymentRequestedTemplate;
    private final KafkaTemplate<String, BookingConfirmedEvent> bookingConfirmedTemplate;
    private final KafkaTemplate<String, BookingCancelledEvent> bookingCancelledTemplate;

    public KafkaOutboxPublisher(KafkaTemplate<String, PaymentRequestedCommand> paymentRequestedTemplate,
                                 KafkaTemplate<String, BookingConfirmedEvent> bookingConfirmedTemplate,
                                 KafkaTemplate<String, BookingCancelledEvent> bookingCancelledTemplate) {
        this.paymentRequestedTemplate = paymentRequestedTemplate;
        this.bookingConfirmedTemplate = bookingConfirmedTemplate;
        this.bookingCancelledTemplate = bookingCancelledTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRequested(PaymentRequestedInternalEvent internalEvent) {
        PaymentRequestedCommand command = internalEvent.command();
        paymentRequestedTemplate.send(KafkaTopics.PAYMENT_COMMANDS, command.bookingId().toString(), command);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedInternalEvent internalEvent) {
        BookingConfirmedEvent event = internalEvent.event();
        bookingConfirmedTemplate.send(KafkaTopics.BOOKING_EVENTS, event.bookingId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledInternalEvent internalEvent) {
        BookingCancelledEvent event = internalEvent.event();
        bookingCancelledTemplate.send(KafkaTopics.BOOKING_EVENTS, event.bookingId().toString(), event);
    }
}
