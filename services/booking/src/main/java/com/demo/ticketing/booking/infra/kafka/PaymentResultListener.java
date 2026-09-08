package com.demo.ticketing.booking.infra.kafka;

import com.demo.ticketing.booking.application.SagaCompletionService;
import com.demo.ticketing.messaging.event.PaymentCompletedEvent;
import com.demo.ticketing.messaging.event.PaymentFailedEvent;
import com.demo.ticketing.messaging.topic.KafkaTopics;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code payment.events} (steps 3/4 of the checkout saga, root CLAUDE.md). Two separate
 * {@code @KafkaListener} methods -- one per payload type -- rather than a single
 * {@code @KafkaHandler} class, since each type needs its own {@code ConsumerFactory}/listener
 * container factory (see {@code KafkaConfig}) with its own {@code JsonDeserializer.VALUE_DEFAULT_TYPE}.
 * Both delegate all saga logic (dedup, race-guarded transition, seat mutation, publish) to
 * {@link SagaCompletionService}.
 */
@Component
public class PaymentResultListener {

    private final SagaCompletionService sagaCompletionService;

    public PaymentResultListener(SagaCompletionService sagaCompletionService) {
        this.sagaCompletionService = sagaCompletionService;
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_EVENTS,
            containerFactory = "paymentCompletedListenerContainerFactory")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        sagaCompletionService.onCompleted(event.messageId(), event.bookingId());
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_EVENTS,
            containerFactory = "paymentFailedListenerContainerFactory")
    public void onPaymentFailed(PaymentFailedEvent event) {
        sagaCompletionService.onFailed(event.messageId(), event.bookingId(), event.reason());
    }
}
