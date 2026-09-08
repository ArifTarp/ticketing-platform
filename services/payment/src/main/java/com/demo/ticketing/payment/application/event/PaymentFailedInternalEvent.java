package com.demo.ticketing.payment.application.event;

import com.demo.ticketing.messaging.event.PaymentFailedEvent;

/**
 * Plain in-process event raised by {@link com.demo.ticketing.payment.application.PaymentProcessingService}
 * right before its {@code @Transactional} method returns a fresh {@code FAILED} outcome. See
 * {@link PaymentCompletedInternalEvent}'s javadoc for why this is not published to Kafka directly.
 *
 * @param event the fully-formed outbound event, built with a fresh random {@code messageId}.
 */
public record PaymentFailedInternalEvent(PaymentFailedEvent event) {
}
