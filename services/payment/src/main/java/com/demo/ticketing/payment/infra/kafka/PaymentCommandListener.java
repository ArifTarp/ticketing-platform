package com.demo.ticketing.payment.infra.kafka;

import com.demo.ticketing.messaging.command.PaymentRequestedCommand;
import com.demo.ticketing.messaging.topic.KafkaTopics;
import com.demo.ticketing.payment.application.PaymentProcessingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code PaymentRequestedCommand} off {@code payment.commands} and delegates to
 * {@link PaymentProcessingService#process}, which is the actual (idempotent, transactional)
 * business logic. This listener itself does no DB work and no Kafka publishing — the
 * publish-after-commit side effect happens via {@code KafkaOutboxPublisher}'s
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, triggered by the internal
 * application event {@link PaymentProcessingService#process} raises before returning.
 *
 * <p>Dead-letter handling (retry then publish to {@code payment.commands.DLT}) is wired on the
 * container factory itself ({@code KafkaConfig#paymentRequestedListenerContainerFactory}), per
 * root CLAUDE.md's "every consumer needs a dead-letter path" — this listener does not need its
 * own try/catch for that.
 */
@Component
public class PaymentCommandListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);

    private final PaymentProcessingService paymentProcessingService;

    public PaymentCommandListener(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMMANDS, containerFactory = "paymentRequestedListenerContainerFactory")
    public void onPaymentRequested(PaymentRequestedCommand command) {
        log.info("Consumed PaymentRequestedCommand messageId={} bookingId={}",
                command.messageId(), command.bookingId());
        paymentProcessingService.process(command.messageId(), command.bookingId(), command.amount());
    }
}
