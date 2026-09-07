package com.demo.ticketing.messaging.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Reusable {@link ConsumerFactory}/{@link ConcurrentKafkaListenerContainerFactory} construction
 * with a dead-letter-topic error handler wired in, per root CLAUDE.md's non-negotiable that
 * "every consumer needs a dead-letter path."
 *
 * <h2>DLQ convention</h2>
 * On listener failure, a message is retried a fixed number of times with a fixed backoff, then
 * published to {@code <source-topic>.DLT} (see {@link com.demo.ticketing.messaging.topic.KafkaTopics#deadLetterTopic}),
 * preserving the original key so downstream inspection/replay tooling can still key by
 * bookingId. Each service is responsible for:
 * <ul>
 *   <li>using {@link #listenerContainerFactory} to build its {@code @KafkaListener} container
 *       factory, passing its own dead-letter {@link KafkaOperations} (a plain
 *       {@code KafkaTemplate<Object, Object>} is sufficient since the DLT payload is only ever
 *       written, never consumed by a typed listener), and</li>
 *   <li>deduping on the payload's {@code eventId} field inside the listener method itself
 *       (idempotency store is service-specific business logic — Phase 7/8's job, not built
 *       here).</li>
 * </ul>
 */
public final class KafkaConsumerConfigSupport {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_INTERVAL_MS = 1000L;

    public static <T> ConsumerFactory<String, T> consumerFactory(
            String bootstrapServers, String groupId, Class<T> payloadType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, payloadType.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.demo.ticketing.messaging.*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    public static <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerContainerFactory(
            ConsumerFactory<String, T> consumerFactory, KafkaOperations<Object, Object> deadLetterTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler(deadLetterTemplate));
        return factory;
    }

    public static DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> deadLetterTemplate) {
        // publishes to <topic>.DLT (recoverer's default), preserving the original record key.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(BACKOFF_INTERVAL_MS, MAX_ATTEMPTS - 1L));
        return handler;
    }

    private KafkaConsumerConfigSupport() {
    }
}
