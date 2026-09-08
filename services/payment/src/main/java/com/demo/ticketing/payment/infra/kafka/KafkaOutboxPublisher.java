package com.demo.ticketing.payment.infra.kafka;

import com.demo.ticketing.messaging.event.PaymentCompletedEvent;
import com.demo.ticketing.messaging.event.PaymentFailedEvent;
import com.demo.ticketing.messaging.topic.KafkaTopics;
import com.demo.ticketing.payment.application.event.PaymentCompletedInternalEvent;
import com.demo.ticketing.payment.application.event.PaymentFailedInternalEvent;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The concrete "outbox = publish-after-commit" mechanism (root CLAUDE.md non-negotiable: "publish
 * events only after the local DB commit"). {@code PaymentProcessingService} never calls
 * {@code KafkaTemplate.send} directly inside its {@code @Transactional} method; instead it raises
 * a plain in-process event in {@code application.event}, and the handlers below — each
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — only fire once the surrounding
 * transaction has actually committed. If the transaction rolls back, these handlers never run and
 * nothing is published, so a crash between "DB write" and "Kafka send" can never leave a message
 * published for a {@code Payment} row that doesn't exist. Mirrors
 * {@code services/booking}'s own {@code KafkaOutboxPublisher} (same pattern, independently wired).
 */
@Component
public class KafkaOutboxPublisher {

    private final KafkaTemplate<String, PaymentCompletedEvent> paymentCompletedTemplate;
    private final KafkaTemplate<String, PaymentFailedEvent> paymentFailedTemplate;

    public KafkaOutboxPublisher(KafkaTemplate<String, PaymentCompletedEvent> paymentCompletedTemplate,
                                 KafkaTemplate<String, PaymentFailedEvent> paymentFailedTemplate) {
        this.paymentCompletedTemplate = paymentCompletedTemplate;
        this.paymentFailedTemplate = paymentFailedTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedInternalEvent internalEvent) {
        PaymentCompletedEvent event = internalEvent.event();
        paymentCompletedTemplate.send(KafkaTopics.PAYMENT_EVENTS, event.bookingId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedInternalEvent internalEvent) {
        PaymentFailedEvent event = internalEvent.event();
        paymentFailedTemplate.send(KafkaTopics.PAYMENT_EVENTS, event.bookingId().toString(), event);
    }
}
