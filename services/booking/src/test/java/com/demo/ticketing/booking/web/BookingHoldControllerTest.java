package com.demo.ticketing.booking.web;

import com.demo.ticketing.booking.web.dto.HoldBookingRequest;
import com.demo.ticketing.booking.web.dto.HoldSeatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP + DB behaviour of {@code POST /api/v1/bookings/hold}, minus the concurrency race itself
 * (that is {@link SeatHoldConcurrencyTest} — the core deliverable of this phase). Same Testcontainers
 * pattern as {@link BookingControllerTest}, plus a real Redis container since this endpoint is the
 * first to touch {@code SeatHoldLockService}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingHoldControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ResponseEntity<JsonNode> hold(HoldBookingRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/bookings/hold", new HttpEntity<>(request, headers), JsonNode.class);
    }

    private void seedSeatAvailability(long eventId, long seatId, String status) {
        jdbcTemplate.update("""
                        INSERT INTO seat_availability (event_id, seat_id, status) VALUES (?, ?, ?)
                        """,
                eventId, seatId, status);
    }

    @Test
    void holdingAvailableSeatsCreatesAPendingBookingWithSnapshottedPricesAndMarksSeatsHeld() {
        long eventId = 5000L;
        HoldBookingRequest request = new HoldBookingRequest(1L, eventId, List.of(
                new HoldSeatRequest(1L, new BigDecimal("100.00")),
                new HoldSeatRequest(2L, new BigDecimal("150.00"))));

        ResponseEntity<JsonNode> response = hold(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("userId").asLong()).isEqualTo(1L);
        assertThat(body.get("eventId").asLong()).isEqualTo(eventId);
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("total").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(body.get("items")).hasSize(2);

        Instant expiresAt = Instant.parse(body.get("expiresAt").asText());
        assertThat(expiresAt).isAfter(Instant.now().plus(java.time.Duration.ofMinutes(9)));
        assertThat(expiresAt).isBefore(Instant.now().plus(java.time.Duration.ofMinutes(11)));

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM seat_availability WHERE event_id = ? ORDER BY seat_id",
                String.class, eventId);
        assertThat(statuses).containsExactly("HELD", "HELD");
    }

    @Test
    void holdingASeatThatIsAlreadyHeldReturns409AndRollsBackTheWholeRequest() {
        long eventId = 5001L;
        seedSeatAvailability(eventId, 10L, "HELD");
        HoldBookingRequest request = new HoldBookingRequest(1L, eventId, List.of(
                new HoldSeatRequest(9L, new BigDecimal("50.00")),
                new HoldSeatRequest(10L, new BigDecimal("50.00"))));

        ResponseEntity<JsonNode> response = hold(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        // No partial hold: seat 9 must not have been left HELD even though it was available.
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM seat_availability WHERE event_id = ? AND seat_id = 9",
                String.class, eventId);
        assertThat(statuses).isEmpty();
        Long bookingCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE event_id = ?", Long.class, eventId);
        assertThat(bookingCount).isZero();
    }

    @Test
    void holdingASoldSeatReturns409() {
        long eventId = 5002L;
        seedSeatAvailability(eventId, 20L, "SOLD");
        HoldBookingRequest request = new HoldBookingRequest(1L, eventId,
                List.of(new HoldSeatRequest(20L, new BigDecimal("50.00"))));

        ResponseEntity<JsonNode> response = hold(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void holdingMoreThanSixSeatsReturns400() {
        long eventId = 5003L;
        List<HoldSeatRequest> seats = List.of(
                new HoldSeatRequest(1L, BigDecimal.TEN), new HoldSeatRequest(2L, BigDecimal.TEN),
                new HoldSeatRequest(3L, BigDecimal.TEN), new HoldSeatRequest(4L, BigDecimal.TEN),
                new HoldSeatRequest(5L, BigDecimal.TEN), new HoldSeatRequest(6L, BigDecimal.TEN),
                new HoldSeatRequest(7L, BigDecimal.TEN));
        HoldBookingRequest request = new HoldBookingRequest(1L, eventId, seats);

        ResponseEntity<JsonNode> response = hold(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void holdingWithADuplicateSeatIdReturns400() {
        long eventId = 5004L;
        HoldBookingRequest request = new HoldBookingRequest(1L, eventId, List.of(
                new HoldSeatRequest(1L, BigDecimal.TEN),
                new HoldSeatRequest(1L, BigDecimal.TEN)));

        ResponseEntity<JsonNode> response = hold(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void holdingWithAnEmptySeatListReturns400() {
        HoldBookingRequest request = new HoldBookingRequest(1L, 5005L, List.of());

        ResponseEntity<JsonNode> response = hold(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
