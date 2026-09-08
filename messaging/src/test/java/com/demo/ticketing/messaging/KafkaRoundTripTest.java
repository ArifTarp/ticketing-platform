package com.demo.ticketing.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.demo.ticketing.messaging.command.PaymentRequestedCommand;
import com.demo.ticketing.messaging.event.BookingConfirmedEvent;
import com.demo.ticketing.messaging.topic.KafkaTopics;

/**
 * Phase 6 "Verify" step: proves the shared DTOs actually serialize/publish/consume correctly
 * over a real (embedded) Kafka broker before booking/payment/notification build any business
 * logic on top of them. One command and one event DTO, per the task's requirement.
 */
@SpringJUnitConfig(KafkaRoundTripTest.EmptyConfig.class)
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.PAYMENT_COMMANDS, KafkaTopics.BOOKING_EVENTS})
class KafkaRoundTripTest {

    @org.springframework.context.annotation.Configuration
    static class EmptyConfig {
    }

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void paymentRequestedCommandRoundTripsThroughEmbeddedKafka() {
        PaymentRequestedCommand sent = new PaymentRequestedCommand(
                UUID.randomUUID(),
                1L,
                2L,
                new BigDecimal("149.99"),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

        PaymentRequestedCommand received = sendAndReceive(
                KafkaTopics.PAYMENT_COMMANDS, sent.bookingId().toString(), sent, PaymentRequestedCommand.class);

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void bookingConfirmedEventRoundTripsThroughEmbeddedKafka() {
        BookingConfirmedEvent sent = new BookingConfirmedEvent(
                UUID.randomUUID(),
                1L,
                2L,
                java.util.List.of(10L, 11L),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

        BookingConfirmedEvent received = sendAndReceive(
                KafkaTopics.BOOKING_EVENTS, sent.bookingId().toString(), sent, BookingConfirmedEvent.class);

        assertThat(received).isEqualTo(sent);
    }

    private <T> T sendAndReceive(String topic, String key, T payload, Class<T> type) {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        DefaultKafkaProducerFactory<String, T> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, T> template = new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("test-group-" + UUID.randomUUID(), "true", broker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, type.getName());
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.ticketing.messaging.*");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        DefaultKafkaConsumerFactory<String, T> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);

        try (Consumer<String, T> consumer = consumerFactory.createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, topic);

            template.send(topic, key, payload);
            template.flush();

            ConsumerRecord<String, T> record =
                    KafkaTestUtils.getSingleRecord(consumer, topic, java.time.Duration.ofSeconds(10));
            assertThat(record.key()).isEqualTo(key);
            return record.value();
        } finally {
            producerFactory.destroy();
        }
    }
}
