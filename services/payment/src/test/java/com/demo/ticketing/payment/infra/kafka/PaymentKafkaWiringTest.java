package com.demo.ticketing.payment.infra.kafka;

import com.demo.ticketing.messaging.command.PaymentRequestedCommand;
import com.demo.ticketing.messaging.event.PaymentCompletedEvent;
import com.demo.ticketing.messaging.event.PaymentFailedEvent;
import com.demo.ticketing.messaging.topic.KafkaTopics;
import com.demo.ticketing.payment.domain.Payment;
import com.demo.ticketing.payment.domain.PaymentStatus;
import com.demo.ticketing.payment.infra.PaymentRepository;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Kafka wiring coverage for Phase 8 (services/payment/CLAUDE.md): publishes a
 * {@link PaymentRequestedCommand} to {@code payment.commands} via a test {@link KafkaTemplate},
 * standing in for booking (built in parallel by a different agent), and asserts both that a
 * {@link Payment} row lands in Postgres AND that the corresponding
 * {@link PaymentCompletedEvent}/{@link PaymentFailedEvent} is published to {@code payment.events}.
 * Uses an embedded Kafka broker (same pattern as the {@code messaging} module's
 * {@code KafkaRoundTripTest} and services/booking's {@code CheckoutSagaTest}) alongside the
 * existing Postgres Testcontainer from {@code PaymentProcessingServiceTest}.
 */
@Testcontainers
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.PAYMENT_COMMANDS, KafkaTopics.PAYMENT_EVENTS})
// See services/booking's CheckoutSagaTest for why `spring.embedded.kafka.brokers` (not
// EmbeddedKafkaCondition.getBroker(), which is still null at property-resolution time) is the
// right property source for this combination.
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class PaymentKafkaWiringTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PaymentRepository paymentRepository;

    // Autowired from the Spring context, not fetched via EmbeddedKafkaCondition.getBroker() (which
    // relies on the JUnit5 @EmbeddedKafka extension's own static-condition wiring and was
    // observed to be null here) -- the broker bean is registered in the context regardless, per
    // the messaging module's own KafkaRoundTripTest precedent.
    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, Object> paymentCommandsTemplate() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Deliberately does NOT use {@link EmbeddedKafkaBroker#consumeFromAnEmbeddedTopic}: that helper
     * unconditionally seeks to the *beginning* of the topic regardless of {@code auto.offset.reset},
     * which would make this test's consumer also read the other test method's leftover record on
     * the shared {@code payment.events} topic (same embedded broker/context across both
     * {@code @Test} methods) and try to force-deserialize it as the wrong event type. Manually
     * {@code assign}+{@code seekToEnd} instead, so each test's consumer only ever sees records
     * published after it is created — the ordering this method's every caller already relies on
     * (consumer created, then the command that triggers the event is published).
     */
    private <T> Consumer<String, T> paymentEventsConsumer(Class<T> type) {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("test-group-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, type.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.ticketing.messaging.*");
        DefaultKafkaConsumerFactory<String, T> consumerFactory = new DefaultKafkaConsumerFactory<>(props);
        Consumer<String, T> consumer = consumerFactory.createConsumer();
        TopicPartition partition = new TopicPartition(KafkaTopics.PAYMENT_EVENTS, 0);
        consumer.assign(List.of(partition));
        consumer.seekToEnd(List.of(partition));
        // seekToEnd is lazy -- the actual seek only resolves on the next position()/poll() call.
        // Forcing it here (rather than leaving it to the first real poll() in getSingleRecord)
        // guarantees this consumer's read position is pinned to "end of log so far" *before* the
        // caller publishes the command that triggers the event, closing the race where an
        // unresolved seek could otherwise still pick up a leftover record from another test method.
        consumer.position(partition);
        return consumer;
    }

    @Test
    void lowAmountCommandCompletesThePaymentAndPublishesPaymentCompletedEvent() {
        long bookingId = 501_001L;
        BigDecimal amount = new BigDecimal("42.50");
        UUID messageId = UUID.randomUUID();

        try (Consumer<String, PaymentCompletedEvent> consumer = paymentEventsConsumer(PaymentCompletedEvent.class)) {
            PaymentRequestedCommand command =
                    new PaymentRequestedCommand(messageId, bookingId, 7L, amount, Instant.now());
            paymentCommandsTemplate().send(KafkaTopics.PAYMENT_COMMANDS, String.valueOf(bookingId), command);

            Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                List<Payment> payments = paymentRepository.findByBookingId(bookingId);
                assertThat(payments).hasSize(1);
                assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            });

            ConsumerRecord<String, PaymentCompletedEvent> record =
                    KafkaTestUtils.getSingleRecord(consumer, KafkaTopics.PAYMENT_EVENTS, Duration.ofSeconds(15));
            assertThat(record.key()).isEqualTo(String.valueOf(bookingId));
            PaymentCompletedEvent event = record.value();
            assertThat(event.bookingId()).isEqualTo(bookingId);
            assertThat(event.amount()).isEqualByComparingTo(amount);
            assertThat(event.providerRef()).startsWith("mock-");
            assertThat(event.messageId()).isNotEqualTo(messageId);
        }
    }

    @Test
    void thresholdAmountCommandFailsThePaymentAndPublishesPaymentFailedEvent() {
        long bookingId = 501_002L;
        BigDecimal amount = new BigDecimal("500.00");
        UUID messageId = UUID.randomUUID();

        try (Consumer<String, PaymentFailedEvent> consumer = paymentEventsConsumer(PaymentFailedEvent.class)) {
            PaymentRequestedCommand command =
                    new PaymentRequestedCommand(messageId, bookingId, 7L, amount, Instant.now());
            paymentCommandsTemplate().send(KafkaTopics.PAYMENT_COMMANDS, String.valueOf(bookingId), command);

            Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                List<Payment> payments = paymentRepository.findByBookingId(bookingId);
                assertThat(payments).hasSize(1);
                assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
            });

            ConsumerRecord<String, PaymentFailedEvent> record =
                    KafkaTestUtils.getSingleRecord(consumer, KafkaTopics.PAYMENT_EVENTS, Duration.ofSeconds(15));
            assertThat(record.key()).isEqualTo(String.valueOf(bookingId));
            PaymentFailedEvent event = record.value();
            assertThat(event.bookingId()).isEqualTo(bookingId);
            assertThat(event.reason()).contains("500.00").contains("exceeds");
            assertThat(event.messageId()).isNotEqualTo(messageId);
        }
    }
}
