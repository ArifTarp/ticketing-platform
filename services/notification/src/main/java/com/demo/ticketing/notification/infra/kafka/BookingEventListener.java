package com.demo.ticketing.notification.infra.kafka;

import com.demo.ticketing.messaging.event.BookingCancelledEvent;
import com.demo.ticketing.messaging.event.BookingConfirmedEvent;
import com.demo.ticketing.messaging.topic.KafkaTopics;
import com.demo.ticketing.notification.application.NotificationService;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code booking.events} (root CLAUDE.md's saga step 6: "notification consumes
 * booking.events and logs a 'sent' notification"). Two separate {@code @KafkaListener} methods —
 * one per payload type, each with its own consumer group and container factory (see
 * {@code KafkaConfig}) — rather than a single listener relying on
 * {@code JsonDeserializer.VALUE_DEFAULT_TYPE}, per the house convention booking's
 * {@code PaymentResultListener} established. Both delegate all business logic (dedup, DB write) to
 * {@link NotificationService}.
 */
@Component
public class BookingEventListener {

    private final NotificationService notificationService;

    public BookingEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = KafkaTopics.BOOKING_EVENTS,
            containerFactory = "bookingConfirmedListenerContainerFactory")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        notificationService.onBookingConfirmed(
                event.messageId(), event.bookingId(), event.userId(), event.seatIds(), event.confirmedAt());
    }

    @KafkaListener(
            topics = KafkaTopics.BOOKING_EVENTS,
            containerFactory = "bookingCancelledListenerContainerFactory")
    public void onBookingCancelled(BookingCancelledEvent event) {
        notificationService.onBookingCancelled(
                event.messageId(), event.bookingId(), event.userId(), event.reason(), event.cancelledAt());
    }
}
