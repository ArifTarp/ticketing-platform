package com.demo.ticketing.payment.config;

import com.demo.ticketing.messaging.command.PaymentRequestedCommand;
import com.demo.ticketing.messaging.config.KafkaConsumerConfigSupport;
import com.demo.ticketing.messaging.config.KafkaProducerFactorySupport;
import com.demo.ticketing.messaging.event.PaymentCompletedEvent;
import com.demo.ticketing.messaging.event.PaymentFailedEvent;

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
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Wires payment's own Kafka producer/consumer beans on top of the shared {@code messaging} module
 * support classes, per {@code services/payment/CLAUDE.md}'s "Kafka contracts" section.
 *
 * <p>Consumer: {@code PaymentRequestedCommand} from {@code payment.commands}. Producers:
 * {@code PaymentCompletedEvent}/{@code PaymentFailedEvent} to {@code payment.events}. Both go
 * through {@link KafkaConsumerConfigSupport}/{@link KafkaProducerFactorySupport} rather than
 * hand-rolled factories, and the listener container factory is wired with the dead-letter
 * publishing recoverer per the DLQ convention (retry then publish to {@code payment.commands.DLT}).
 */
@Configuration
public class KafkaConfig {

    private static final String PAYMENT_REQUESTED_GROUP = "payment-service-payment-requested";

    @Bean
    public ConsumerFactory<String, PaymentRequestedCommand> paymentRequestedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaConsumerConfigSupport.consumerFactory(
                bootstrapServers, PAYMENT_REQUESTED_GROUP, PaymentRequestedCommand.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentRequestedCommand> paymentRequestedListenerContainerFactory(
            ConsumerFactory<String, PaymentRequestedCommand> paymentRequestedConsumerFactory,
            KafkaOperations<Object, Object> deadLetterTemplate) {
        return KafkaConsumerConfigSupport.listenerContainerFactory(paymentRequestedConsumerFactory, deadLetterTemplate);
    }

    @Bean
    public KafkaTemplate<String, PaymentCompletedEvent> paymentCompletedTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaProducerFactorySupport.kafkaTemplate(bootstrapServers, PaymentCompletedEvent.class);
    }

    @Bean
    public KafkaTemplate<String, PaymentFailedEvent> paymentFailedTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return KafkaProducerFactorySupport.kafkaTemplate(bootstrapServers, PaymentFailedEvent.class);
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
}
