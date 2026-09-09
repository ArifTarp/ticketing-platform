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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
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

    @Autowired
    private StringRedisTemplate redisTemplate;

    private ResponseEntity<JsonNode> hold(HoldBookingRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/bookings/hold", new HttpEntity<>(request, headers), JsonNode.class);
    }

    private String redisHoldKey(long eventId, long seatId) {
        return "booking:seat-hold:" + eventId + ":" + seatId;
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

    /** ADR-0004: a second hold call for the same (userId, eventId) appends to the existing booking. */
    @Test
    void aSecondHoldCallForTheSameUserAndEventAppendsToTheExistingPendingBooking() {
        long eventId = 5006L;
        HoldBookingRequest first = new HoldBookingRequest(1L, eventId,
                List.of(new HoldSeatRequest(1L, new BigDecimal("100.00"))));
        HoldBookingRequest second = new HoldBookingRequest(1L, eventId,
                List.of(new HoldSeatRequest(2L, new BigDecimal("150.00"))));

        ResponseEntity<JsonNode> firstResponse = hold(first);
        ResponseEntity<JsonNode> secondResponse = hold(second);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        long firstBookingId = firstResponse.getBody().get("id").asLong();
        long secondBookingId = secondResponse.getBody().get("id").asLong();
        assertThat(secondBookingId).isEqualTo(firstBookingId);

        JsonNode secondBody = secondResponse.getBody();
        assertThat(secondBody.get("items")).hasSize(2);
        assertThat(secondBody.get("total").decimalValue()).isEqualByComparingTo("250.00");

        Long bookingCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE event_id = ?", Long.class, eventId);
        assertThat(bookingCount).isEqualTo(1L);
    }

    /** ADR-0004: the max-6-seats rule is cumulative across appended calls, not per request. */
    @Test
    void appendingSeatsPastTheCumulativeSixSeatLimitReturns400() {
        long eventId = 5007L;
        HoldBookingRequest first = new HoldBookingRequest(1L, eventId, List.of(
                new HoldSeatRequest(1L, BigDecimal.TEN), new HoldSeatRequest(2L, BigDecimal.TEN),
                new HoldSeatRequest(3L, BigDecimal.TEN), new HoldSeatRequest(4L, BigDecimal.TEN),
                new HoldSeatRequest(5L, BigDecimal.TEN)));
        HoldBookingRequest second = new HoldBookingRequest(1L, eventId, List.of(
                new HoldSeatRequest(6L, BigDecimal.TEN), new HoldSeatRequest(7L, BigDecimal.TEN)));

        ResponseEntity<JsonNode> firstResponse = hold(first);
        ResponseEntity<JsonNode> secondResponse = hold(second);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Long bookingItemCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM booking_items bi JOIN bookings b ON b.id = bi.booking_id "
                        + "WHERE b.event_id = ?", Long.class, eventId);
        assertThat(bookingItemCount).isEqualTo(5L);
    }

    /**
     * Regression test for the concurrency bug fixed alongside this test: ADR-0004's append path
     * extends the booking's DB {@code expires_at} to a fresh 10-minute window, but before the fix
     * only the newly-requested seat's Redis key was refreshed — a seat held by an earlier call kept
     * its original TTL and could expire out of Redis while the DB still considered it validly held.
     *
     * <p>Rather than sleeping past a real 10-minute TTL, this shrinks seat A's Redis TTL directly
     * (simulating most of the window having already elapsed) right after the first hold call, then
     * asserts the append call pushes it back out to (approximately) a fresh {@code HOLD_TTL} —
     * proving {@code BookingHoldService} actually refreshed it, not just that time hadn't passed.
     */
    @Test
    void appendingSeatsToAnExistingPendingBookingRefreshesTheRedisTtlOfPreviouslyHeldSeats() {
        long eventId = 5008L;
        long seatA = 1L;
        long seatB = 2L;

        HoldBookingRequest first = new HoldBookingRequest(1L, eventId,
                List.of(new HoldSeatRequest(seatA, new BigDecimal("100.00"))));
        ResponseEntity<JsonNode> firstResponse = hold(first);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String seatAKey = redisHoldKey(eventId, seatA);
        assertThat(redisTemplate.hasKey(seatAKey)).isTrue();

        // Simulate the earlier call's hold being close to expiry, well before the DB's expires_at
        // will be — the exact window the bug leaves seat A vulnerable in.
        redisTemplate.expire(seatAKey, Duration.ofSeconds(5));
        assertThat(redisTemplate.getExpire(seatAKey)).isBetween(1L, 5L);

        HoldBookingRequest second = new HoldBookingRequest(1L, eventId,
                List.of(new HoldSeatRequest(seatB, new BigDecimal("150.00"))));
        ResponseEntity<JsonNode> secondResponse = hold(second);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Seat A's Redis TTL must have been refreshed back out to (approximately) a fresh 10-minute
        // HOLD_TTL by the append, not left at the shrunk value from before.
        Long seatATtlAfterAppend = redisTemplate.getExpire(seatAKey);
        assertThat(seatATtlAfterAppend).isGreaterThan(500L);

        assertThat(redisTemplate.hasKey(redisHoldKey(eventId, seatB))).isTrue();
    }
}
