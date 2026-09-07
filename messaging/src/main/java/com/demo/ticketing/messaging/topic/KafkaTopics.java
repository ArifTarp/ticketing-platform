package com.demo.ticketing.messaging.topic;

/**
 * Canonical topic names, per root CLAUDE.md's "Kafka topics & the checkout saga" table.
 * dot.case per CLAUDE.md's Kafka naming convention. Message key on every topic below is the
 * bookingId (the saga's aggregate id), for per-booking ordering.
 */
public final class KafkaTopics {

    public static final String PAYMENT_COMMANDS = "payment.commands";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String BOOKING_EVENTS = "booking.events";

    /**
     * Dead-letter-topic naming convention (flagged as undefined in business-rules.md's
     * "Flagged gaps" section — resolved here): {@code <topic>.DLT}, matching Spring Kafka's
     * {@code DeadLetterPublishingRecoverer} default suffix so no custom resolver is needed.
     * Each service wires a {@code DefaultErrorHandler} with this recoverer per consumer; see
     * {@link com.demo.ticketing.messaging.config.KafkaConsumerConfigSupport}.
     */
    public static String deadLetterTopic(String topic) {
        return topic + ".DLT";
    }

    private KafkaTopics() {
    }
}
