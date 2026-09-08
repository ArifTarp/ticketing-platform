package com.demo.ticketing.booking.web;

import com.demo.ticketing.booking.web.dto.HoldBookingRequest;
import com.demo.ticketing.booking.web.dto.HoldSeatRequest;
import com.demo.ticketing.messaging.event.PaymentCompletedEvent;
import com.demo.ticketing.messaging.event.PaymentFailedEvent;
import com.demo.ticketing.messaging.topic.KafkaTopics;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end checkout saga coverage (Phase 8, root CLAUDE.md's "Kafka topics & the checkout saga"):
 * hold -> checkout -> synthetic {@code payment.events} message -> confirmed/cancelled outcome, the
 * hold-expiry sweep, and the idempotent-consumer dedup guard. Uses an embedded Kafka broker (same
 * pattern as the {@code messaging} module's {@code KafkaRoundTripTest}) alongside the existing
 * Postgres/Redis Testcontainers from {@link BookingHoldControllerTest}. Publishes synthetic
 * {@link PaymentCompletedEvent}/{@link PaymentFailedEvent} messages directly via a test
 * {@link KafkaTemplate}, standing in for the payment service (built in parallel by a different
 * agent) so this suite never depends on a live payment service.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {
        KafkaTopics.PAYMENT_COMMANDS, KafkaTopics.PAYMENT_EVENTS, KafkaTopics.BOOKING_EVENTS})
// `spring.embedded.kafka.brokers` is the property EmbeddedKafkaBroker publishes once it starts
// (before context refresh); referencing it here -- rather than a @DynamicPropertySource reading
// EmbeddedKafkaCondition.getBroker(), which is still null at that point in the test lifecycle --
// is the pattern Spring Kafka's own test support documents for this combination.
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class CheckoutSagaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void sweepInterval(DynamicPropertyRegistry registry) {
        // Short sweep interval so sweepExpiresAPendingBookingPastItsHoldAndReleasesTheSeat doesn't
        // have to wait 30s; harmless for the other tests since their holds' expiresAt is ~10
        // minutes out.
        registry.add("booking.sweep.interval-ms", () -> "500");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Spring registers the running broker as a bean when @EmbeddedKafka is on the test class --
    // more reliable here than EmbeddedKafkaCondition's ThreadLocal, which is only guaranteed to be
    // populated on the JUnit extension's own thread, not necessarily this test method's.
    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, Object> paymentEventsTemplate() {
        var props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(producerFactory);
    }

    private ResponseEntity<JsonNode> hold(HoldBookingRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/bookings/hold", new HttpEntity<>(request, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> checkout(long bookingId) {
        return restTemplate.postForEntity("/api/v1/bookings/" + bookingId + "/checkout", null, JsonNode.class);
    }

    private JsonNode getBooking(long bookingId) {
        return restTemplate.getForEntity("/api/v1/bookings/" + bookingId, JsonNode.class).getBody();
    }

    private String redisHoldKey(long eventId, long seatId) {
        return "booking:seat-hold:" + eventId + ":" + seatId;
    }

    @Test
    void checkoutThenPaymentCompletedConfirmsTheBookingAndSellsTheSeats() {
        long eventId = 90_000L;
        long seatId = 90_001L;
        HoldBookingRequest holdRequest = new HoldBookingRequest(1L, eventId,
                List.of(new HoldSeatRequest(seatId, new BigDecimal("120.00"))));
        ResponseEntity<JsonNode> holdResponse = hold(holdRequest);
        assertThat(holdResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        long bookingId = holdResponse.getBody().get("id").asLong();

        ResponseEntity<JsonNode> checkoutResponse = checkout(bookingId);
        assertThat(checkoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkoutResponse.getBody().get("status").asText()).isEqualTo("PENDING");

        PaymentCompletedEvent completed = new PaymentCompletedEvent(
                UUID.randomUUID(), bookingId, "test-ref", new BigDecimal("120.00"), Instant.now());
        paymentEventsTemplate().send(KafkaTopics.PAYMENT_EVENTS, String.valueOf(bookingId), completed);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(getBooking(bookingId).get("status").asText()).isEqualTo("CONFIRMED"));

        String seatStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM seat_availability WHERE event_id = ? AND seat_id = ?",
                String.class, eventId, seatId);
        assertThat(seatStatus).isEqualTo("SOLD");

        var sagaRow = jdbcTemplate.queryForMap(
                "SELECT step, status FROM saga_state WHERE booking_id = ?", bookingId);
        assertThat(sagaRow.get("step")).isEqualTo("CONFIRMED");
        assertThat(sagaRow.get("status")).isEqualTo("DONE");
    }

    @Test
    void checkoutThenPaymentFailedCancelsTheBookingAndReleasesTheSeats() {
        long eventId = 90_100L;
        long seatId = 90_101L;
        HoldBookingRequest holdRequest = new HoldBookingRequest(1L, eventId,
                List.of(new HoldSeatRequest(seatId, new BigDecimal("75.00"))));
        long bookingId = hold(holdRequest).getBody().get("id").asLong();
        checkout(bookingId);

        PaymentFailedEvent failed = new PaymentFailedEvent(
                UUID.randomUUID(), bookingId, "test-fail", Instant.now());
        paymentEventsTemplate().send(KafkaTopics.PAYMENT_EVENTS, String.valueOf(bookingId), failed);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(getBooking(bookingId).get("status").asText()).isEqualTo("CANCELLED"));

        String seatStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM seat_availability WHERE event_id = ? AND seat_id = ?",
                String.class, eventId, seatId);
        assertThat(seatStatus).isEqualTo("AVAILABLE");
        assertThat(redisTemplate.hasKey(redisHoldKey(eventId, seatId))).isFalse();
    }

    @Test
    void duplicatePaymentCompletedMessageIsProcessedExactlyOnce() {
        long eventId = 90_200L;
        long seatId = 90_201L;
        HoldBookingRequest holdRequest = new HoldBookingRequest(1L, eventId,
                List.of(new HoldSeatRequest(seatId, new BigDecimal("60.00"))));
        long bookingId = hold(holdRequest).getBody().get("id").asLong();
        checkout(bookingId);

        UUID messageId = UUID.randomUUID();
        PaymentCompletedEvent completed = new PaymentCompletedEvent(
                messageId, bookingId, "test-ref", new BigDecimal("60.00"), Instant.now());
        KafkaTemplate<String, Object> template = paymentEventsTemplate();
        template.send(KafkaTopics.PAYMENT_EVENTS, String.valueOf(bookingId), completed);
        template.send(KafkaTopics.PAYMENT_EVENTS, String.valueOf(bookingId), completed);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(getBooking(bookingId).get("status").asText()).isEqualTo("CONFIRMED"));

        // Give the (already-processed, should-be-no-op) redelivery time to be consumed too.
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Long processedCount = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM processed_messages WHERE message_id = ?",
                            Long.class, messageId);
                    assertThat(processedCount).isEqualTo(1L);
                });

        Long sagaRowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM saga_state WHERE booking_id = ?", Long.class, bookingId);
        assertThat(sagaRowCount).isEqualTo(1L);
    }

    @Test
    void sweepExpiresAPendingBookingPastItsHoldAndReleasesTheSeat() {
        long eventId = 90_300L;
        long seatId = 90_301L;
        Instant createdAt = Instant.now().minus(20, ChronoUnit.MINUTES);
        Instant expiresAt = Instant.now().minus(5, ChronoUnit.MINUTES);

        long bookingId = jdbcTemplate.queryForObject("""
                        INSERT INTO bookings (user_id, event_id, status, total, created_at, expires_at)
                        VALUES (?, ?, 'PENDING', ?, ?, ?)
                        RETURNING id
                        """,
                Long.class, 1L, eventId, new BigDecimal("45.00"),
                Timestamp.from(createdAt), Timestamp.from(expiresAt));
        jdbcTemplate.update(
                "INSERT INTO booking_items (booking_id, seat_id, price) VALUES (?, ?, ?)",
                bookingId, seatId, new BigDecimal("45.00"));
        jdbcTemplate.update(
                "INSERT INTO seat_availability (event_id, seat_id, status) VALUES (?, ?, 'HELD')",
                eventId, seatId);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(getBooking(bookingId).get("status").asText()).isEqualTo("EXPIRED"));

        String seatStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM seat_availability WHERE event_id = ? AND seat_id = ?",
                String.class, eventId, seatId);
        assertThat(seatStatus).isEqualTo("AVAILABLE");

        var sagaRow = jdbcTemplate.queryForMap(
                "SELECT step, status FROM saga_state WHERE booking_id = ?", bookingId);
        assertThat(sagaRow.get("step")).isEqualTo("EXPIRED");
        assertThat(sagaRow.get("status")).isEqualTo("DONE");
    }
}
