package com.demo.ticketing.booking.config;

import com.demo.ticketing.messaging.command.PaymentRequestedCommand;
import com.demo.ticketing.messaging.config.KafkaConsumerConfigSupport;
import com.demo.ticketing.messaging.config.KafkaProducerFactorySupport;
import com.demo.ticketing.messaging.event.BookingCancelledEvent;
import com.demo.ticketing.messaging.event.BookingConfirmedEvent;
import com.demo.ticketing.messaging.event.PaymentCompletedEvent;
import com.demo.ticketing.messaging.event.PaymentFailedEvent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Wires booking's own Kafka producer/consumer beans on top of the shared {@code messaging} module
 * support classes, per {@code services/booking/CLAUDE.md}'s "Kafka contracts" section.
 *
 * <p>Producers: {@code PaymentRequestedCommand} to {@code payment.commands},
 * {@code BookingConfirmedEvent}/{@code BookingCancelledEvent} to {@code booking.events}.
 *
 * <h2>Consuming two payload types off one topic ({@code payment.events})</h2>
 * {@code PaymentCompletedEvent}/{@code PaymentFailedEvent} both arrive on {@code payment.events}.
 * Two deliberate correctness fixes versus the "obvious" version of this wiring:
 * <ul>
 *   <li><strong>Different consumer groups, not the same one.</strong> If both listeners shared one
 *       {@code group.id}, they would be two members of the same consumer group reading a
 *       single-partition topic -- Kafka assigns each partition to exactly one group member, so
 *       only <em>one</em> of the two listeners would ever receive any message at all (the other
 *       would sit idle after rebalancing). Each listener here uses its own group id so each reads
 *       the entire topic independently, exactly like two unrelated consumers.</li>
 *   <li><strong>A header-based {@link RecordFilterStrategy}, not reliance on
 *       {@code JsonDeserializer.VALUE_DEFAULT_TYPE} alone.</strong> {@code VALUE_DEFAULT_TYPE}
 *       (used by {@link KafkaConsumerConfigSupport#consumerFactory}) blindly force-deserializes
 *       <em>every</em> record into the configured type regardless of what was actually published.
 *       Since the shared {@code KafkaJsonSupport} object mapper disables
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES}, a {@code PaymentFailedEvent} record landing on the
 *       {@code PaymentCompletedEvent} consumer would silently deserialize into a
 *       {@code PaymentCompletedEvent} with null {@code amount}/{@code providerRef} instead of
 *       failing loudly -- exactly the kind of "looks fine, corrupts data" bug a saga can't afford
 *       (it could confirm a booking whose payment actually failed). {@link #typeHeaderFilter} reads
 *       the {@code __TypeId__} header that {@code JsonSerializer} attaches by default (the actual
 *       published class name) and discards (does not invoke the listener for) any record whose
 *       header doesn't match the listener's expected type, so a wrong-type record is silently
 *       skipped by this listener -- correctly picked up by the sibling listener/group instead --
 *       rather than being corrupted into the wrong shape.</li>
 * </ul>
 * Both consumer factories still go through {@link KafkaConsumerConfigSupport} for the DLQ error
 * handler (retry + publish to {@code payment.events.DLT}), per the DLQ convention.
 */
@Configuration
public class KafkaConfig {

    private static final String PAYMENT_COMPLETED_GROUP = "booking-service-payment-completed";
    private static final String PAYMENT_FAILED_GROUP = "booking-service-payment-failed";

    /** Default header key {@code JsonSerializer}/{@code JsonDeserializer} use for the published Java type. */
    private static final String TYPE_ID_HEADER = "__TypeId__";

    @Bean
    public KafkaTemplate<String, PaymentRequestedCommand> paymentRequestedTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaProducerFactorySupport.kafkaTemplate(bootstrapServers, PaymentRequestedCommand.class);
    }

    @Bean
    public KafkaTemplate<String, BookingConfirmedEvent> bookingConfirmedTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaProducerFactorySupport.kafkaTemplate(bootstrapServers, BookingConfirmedEvent.class);
    }

    @Bean
    public KafkaTemplate<String, BookingCancelledEvent> bookingCancelledTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaProducerFactorySupport.kafkaTemplate(bootstrapServers, BookingCancelledEvent.class);
    }

    /**
     * Untyped template used only to write to the dead-letter topic, never to consume from it.
     * Built directly (not via {@link KafkaProducerFactorySupport#producerFactory}, which always
     * types the key as {@code String}) since {@link KafkaConsumerConfigSupport#listenerContainerFactory}
     * requires exactly {@code KafkaOperations<Object, Object>}.
     */
    @Bean
    public KafkaTemplate<Object, Object> deadLetterTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public ConsumerFactory<String, PaymentCompletedEvent> paymentCompletedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaConsumerConfigSupport.consumerFactory(
                bootstrapServers, PAYMENT_COMPLETED_GROUP, PaymentCompletedEvent.class);
    }

    @Bean
    public ConsumerFactory<String, PaymentFailedEvent> paymentFailedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaConsumerConfigSupport.consumerFactory(
                bootstrapServers, PAYMENT_FAILED_GROUP, PaymentFailedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> paymentCompletedListenerContainerFactory(
            ConsumerFactory<String, PaymentCompletedEvent> paymentCompletedConsumerFactory,
            KafkaOperations<Object, Object> deadLetterTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> factory =
                KafkaConsumerConfigSupport.listenerContainerFactory(paymentCompletedConsumerFactory, deadLetterTemplate);
        factory.setRecordFilterStrategy(typeHeaderFilter(PaymentCompletedEvent.class));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedListenerContainerFactory(
            ConsumerFactory<String, PaymentFailedEvent> paymentFailedConsumerFactory,
            KafkaOperations<Object, Object> deadLetterTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> factory =
                KafkaConsumerConfigSupport.listenerContainerFactory(paymentFailedConsumerFactory, deadLetterTemplate);
        factory.setRecordFilterStrategy(typeHeaderFilter(PaymentFailedEvent.class));
        return factory;
    }

    /**
     * @return a filter that discards (returns {@code true} for) any record whose {@code __TypeId__}
     * header does not equal {@code expectedType}'s fully-qualified name -- see class javadoc.
     */
    private <T> RecordFilterStrategy<String, T> typeHeaderFilter(Class<?> expectedType) {
        String expectedTypeId = expectedType.getName();
        return record -> {
            var header = record.headers().lastHeader(TYPE_ID_HEADER);
            if (header == null) {
                return false; // no type info available; let it through and let the listener/Jackson deal with it
            }
            String actualTypeId = new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
            return !expectedTypeId.equals(actualTypeId);
        };
    }
}
