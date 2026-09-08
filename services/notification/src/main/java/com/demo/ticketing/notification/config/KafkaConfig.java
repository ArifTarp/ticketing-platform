package com.demo.ticketing.notification.config;

import com.demo.ticketing.messaging.config.KafkaConsumerConfigSupport;
import com.demo.ticketing.messaging.event.BookingCancelledEvent;
import com.demo.ticketing.messaging.event.BookingConfirmedEvent;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Wires notification's own Kafka consumer beans on top of the shared {@code messaging} module
 * support classes, per {@code services/notification/CLAUDE.md}'s "Kafka contracts" section.
 * notification only ever consumes — it never publishes to any topic (root CLAUDE.md: "pure
 * consumer... no inbound REST"), so there are no producer {@code KafkaTemplate} beans for
 * business events here, only the dead-letter template needed by the DLQ convention.
 *
 * <h2>Consuming two payload types off one topic ({@code booking.events})</h2>
 * {@code BookingConfirmedEvent}/{@code BookingCancelledEvent} both arrive on
 * {@code booking.events}. This reuses, verbatim, the fix booking's own {@code KafkaConfig}
 * (consuming {@code payment.events}) had to apply after an earlier flawed design was caught during
 * Phase 8 review:
 * <ul>
 *   <li><strong>Different consumer groups, not the same one.</strong> Same-group listeners on a
 *       single-partition topic would only let one of the two ever receive anything at all (the
 *       other starves after rebalancing). Each listener here has its own group id so each reads
 *       the entire topic independently.</li>
 *   <li><strong>A header-based {@link RecordFilterStrategy}, not reliance on
 *       {@code JsonDeserializer.VALUE_DEFAULT_TYPE} alone.</strong> {@code VALUE_DEFAULT_TYPE}
 *       force-deserializes every record into one fixed type regardless of what was actually
 *       published; with {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled this silently corrupts data
 *       instead of failing loudly (e.g. a {@code BookingCancelledEvent} landing on the
 *       {@code BookingConfirmedEvent} consumer would deserialize into a
 *       {@code BookingConfirmedEvent} with a null {@code seatIds}). {@link #typeHeaderFilter} reads
 *       the {@code __TypeId__} header {@code JsonSerializer} attaches by default and discards any
 *       record whose header doesn't match the listener's expected type — the wrong-type record is
 *       correctly picked up by the sibling listener/group instead.</li>
 * </ul>
 * Both consumer factories go through {@link KafkaConsumerConfigSupport} for the DLQ error handler
 * (retry + publish to {@code booking.events.DLT}), per the DLQ convention.
 */
@Configuration
public class KafkaConfig {

    private static final String BOOKING_CONFIRMED_GROUP = "notification-service-booking-confirmed";
    private static final String BOOKING_CANCELLED_GROUP = "notification-service-booking-cancelled";

    /** Default header key {@code JsonSerializer}/{@code JsonDeserializer} use for the published Java type. */
    private static final String TYPE_ID_HEADER = "__TypeId__";

    /**
     * Untyped template used only to write to the dead-letter topic, never to consume from it.
     * Built directly (not via {@code KafkaProducerFactorySupport}, which always types the key as
     * {@code String}) since {@link KafkaConsumerConfigSupport#listenerContainerFactory} requires
     * exactly {@code KafkaOperations<Object, Object>}. Mirrors
     * {@code services/booking/.../KafkaConfig#deadLetterTemplate}.
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
    public ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaConsumerConfigSupport.consumerFactory(
                bootstrapServers, BOOKING_CONFIRMED_GROUP, BookingConfirmedEvent.class);
    }

    @Bean
    public ConsumerFactory<String, BookingCancelledEvent> bookingCancelledConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaConsumerConfigSupport.consumerFactory(
                bootstrapServers, BOOKING_CANCELLED_GROUP, BookingCancelledEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> bookingConfirmedListenerContainerFactory(
            ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory,
            KafkaOperations<Object, Object> deadLetterTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> factory =
                KafkaConsumerConfigSupport.listenerContainerFactory(bookingConfirmedConsumerFactory, deadLetterTemplate);
        factory.setRecordFilterStrategy(typeHeaderFilter(BookingConfirmedEvent.class));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingCancelledEvent> bookingCancelledListenerContainerFactory(
            ConsumerFactory<String, BookingCancelledEvent> bookingCancelledConsumerFactory,
            KafkaOperations<Object, Object> deadLetterTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, BookingCancelledEvent> factory =
                KafkaConsumerConfigSupport.listenerContainerFactory(bookingCancelledConsumerFactory, deadLetterTemplate);
        factory.setRecordFilterStrategy(typeHeaderFilter(BookingCancelledEvent.class));
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
            String actualTypeId = new String(header.value(), StandardCharsets.UTF_8);
            return !expectedTypeId.equals(actualTypeId);
        };
    }
}
