package com.demo.ticketing.messaging.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared Jackson {@link ObjectMapper} configuration for (de)serializing the command/event
 * records in {@code com.demo.ticketing.messaging.command}/{@code .event}. Each service's own
 * {@code JsonSerializer}/{@code JsonDeserializer} beans should use this instead of a bare
 * {@code new ObjectMapper()} so Instant/UUID fields round-trip consistently.
 */
public final class KafkaJsonSupport {

    public static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    private KafkaJsonSupport() {
    }
}
