package com.demo.ticketing.notification.infra.kafka;

import com.demo.ticketing.messaging.event.BookingCancelledEvent;
import com.demo.ticketing.messaging.event.BookingConfirmedEvent;
import com.demo.ticketing.messaging.topic.KafkaTopics;
import com.demo.ticketing.notification.domain.Notification;
import com.demo.ticketing.notification.domain.NotificationType;
import com.demo.ticketing.notification.infra.NotificationRepository;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Kafka wiring coverage for Phase 9 (services/notification/CLAUDE.md): publishes
 * {@link BookingConfirmedEvent}/{@link BookingCancelledEvent} to {@code booking.events} via a test
 * {@link KafkaTemplate}, standing in for booking, and asserts a {@link Notification} row lands in
 * Postgres — including that a replayed (same {@code messageId}) event does NOT create a duplicate
 * row. Mirrors services/payment's {@code PaymentKafkaWiringTest} (embedded Kafka + a Postgres
 * Testcontainer).
 */
@Testcontainers
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = KafkaTopics.BOOKING_EVENTS)
// See services/payment's PaymentKafkaWiringTest for why `spring.embedded.kafka.brokers` (not
// EmbeddedKafkaCondition.getBroker(), which is still null at property-resolution time) is the
// right property source for this combination.
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class BookingEventListenerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificationRepository notificationRepository;

    // Autowired from the Spring context, not fetched via EmbeddedKafkaCondition.getBroker() (which
    // relies on the JUnit5 @EmbeddedKafka extension's own static-condition wiring and was
    // observed to be null there) -- the broker bean is registered in the context regardless, per
    // services/payment's PaymentKafkaWiringTest precedent.
    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, Object> bookingEventsTemplate() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(producerFactory);
    }

    @Test
    void bookingConfirmedEventProducesOneNotificationRow() {
        long bookingId = 701_001L;
        long userId = 9001L;
        UUID messageId = UUID.randomUUID();
        String recipient = "user-" + userId + "@example.com";

        BookingConfirmedEvent event = new BookingConfirmedEvent(
                messageId, bookingId, userId, List.of(1L, 2L), Instant.now());
        bookingEventsTemplate().send(KafkaTopics.BOOKING_EVENTS, String.valueOf(bookingId), event);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findByRecipient(recipient);
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
            assertThat(notifications.get(0).getPayload()).contains(String.valueOf(bookingId));
        });
    }

    @Test
    void bookingCancelledEventProducesOneNotificationRowWithReason() {
        long bookingId = 701_002L;
        long userId = 9002L;
        UUID messageId = UUID.randomUUID();
        String recipient = "user-" + userId + "@example.com";

        BookingCancelledEvent event = new BookingCancelledEvent(
                messageId, bookingId, userId, "PAYMENT_FAILED", Instant.now());
        bookingEventsTemplate().send(KafkaTopics.BOOKING_EVENTS, String.valueOf(bookingId), event);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findByRecipient(recipient);
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.BOOKING_CANCELLED);
            assertThat(notifications.get(0).getPayload()).contains("PAYMENT_FAILED");
        });
    }

    @Test
    void replayingTheSameMessageIdDoesNotCreateADuplicateNotification() {
        long bookingId = 701_003L;
        long userId = 9003L;
        UUID messageId = UUID.randomUUID();
        String recipient = "user-" + userId + "@example.com";

        BookingConfirmedEvent event = new BookingConfirmedEvent(
                messageId, bookingId, userId, List.of(3L), Instant.now());
        KafkaTemplate<String, Object> template = bookingEventsTemplate();
        template.send(KafkaTopics.BOOKING_EVENTS, String.valueOf(bookingId), event);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(notificationRepository.findByRecipient(recipient)).hasSize(1));

        // Replay the exact same messageId — must be a no-op, not a second row.
        template.send(KafkaTopics.BOOKING_EVENTS, String.valueOf(bookingId), event);

        // Give the (would-be duplicate) consumption a chance to happen, then assert it never does.
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findByRecipient(recipient)).hasSize(1));
    }
}
