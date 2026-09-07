package com.demo.ticketing.messaging.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Reusable {@link ProducerFactory}/{@link KafkaTemplate} construction for a given payload type.
 * Each producing service defines its own {@code @Bean} that calls
 * {@link #kafkaTemplate(String, Class)}, e.g.:
 *
 * <pre>{@code
 * @Bean
 * KafkaTemplate<String, PaymentRequestedCommand> paymentRequestedTemplate() {
 *     return KafkaProducerFactorySupport.kafkaTemplate(bootstrapServers, PaymentRequestedCommand.class);
 * }
 * }</pre>
 *
 * Kept generic-per-payload (rather than one untyped template) so producers get compile-time
 * safety on which message type goes to which topic.
 */
public final class KafkaProducerFactorySupport {

    public static <T> KafkaTemplate<String, T> kafkaTemplate(String bootstrapServers, Class<T> ignoredPayloadType) {
        return new KafkaTemplate<>(producerFactory(bootstrapServers));
    }

    public static <T> ProducerFactory<String, T> producerFactory(String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Outbox pattern (root CLAUDE.md non-negotiable: publish only after local DB commit) is
        // implemented by the calling service's transaction boundary, not here; acks=all + no
        // idempotence-breaking retries-without-idempotence gives at-least-once delivery, which is
        // what "consumers must be idempotent" is designed around.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    private KafkaProducerFactorySupport() {
    }
}
