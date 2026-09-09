package com.demo.ticketing.booking.web;

import com.demo.ticketing.booking.web.dto.HoldBookingRequest;
import com.demo.ticketing.booking.web.dto.HoldSeatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the ADR-0004 TOCTOU bug fixed in {@code BookingHoldService.persistHold}:
 * the Redis lock only serializes concurrent holds per {@code (eventId, seatId)}, never per
 * {@code (userId, eventId)}, so two concurrent {@code POST /bookings/hold} requests for two
 * <em>different</em> seats from the same user/event could each run the find-or-create check on the
 * existing {@code PENDING} booking before either INSERT committed, both see "none exists," and each
 * create their own separate one-seat booking instead of one shared multi-seat booking.
 *
 * <p>Same {@link CountDownLatch}-starting-gate pattern as {@code SeatHoldConcurrencyTest}, repeated
 * ({@link RepeatedTest}) to rule out flakiness: two distinct seats, same {@code (userId, eventId)},
 * released at (as close to) the same instant. Both must succeed with {@code 200} (they are different
 * seats, no seat-level conflict) and must end up as exactly ONE {@code PENDING} booking containing
 * BOTH seats — not two separate bookings.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PendingBookingRaceConcurrencyTest {

    private static final int REPETITIONS = 10;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    // Distinct (userId, eventId) per repetition so RepeatedTest iterations never interfere with
    // each other's PENDING booking / max-6-seats bookkeeping.
    private static final AtomicLong SEQUENCE = new AtomicLong(96_000L);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterAll
    static void shutdownPool() {
        POOL.shutdownNow();
    }

    private ResponseEntity<JsonNode> hold(HoldBookingRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/bookings/hold", new HttpEntity<>(request, headers), JsonNode.class);
    }

    @RepeatedTest(REPETITIONS)
    void twoConcurrentHoldsOnDifferentSeatsForTheSameUserAndEventMergeIntoOneBooking() throws Exception {
        long userId = 7L;
        long eventId = SEQUENCE.incrementAndGet();
        long seatIdA = SEQUENCE.incrementAndGet();
        long seatIdB = SEQUENCE.incrementAndGet();

        HoldBookingRequest requestA = new HoldBookingRequest(userId, eventId,
                List.of(new HoldSeatRequest(seatIdA, new BigDecimal("30.00"))));
        HoldBookingRequest requestB = new HoldBookingRequest(userId, eventId,
                List.of(new HoldSeatRequest(seatIdB, new BigDecimal("40.00"))));

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<ResponseEntity<JsonNode>> attemptA = holdAttempt(requestA, bothReady, go);
        Callable<ResponseEntity<JsonNode>> attemptB = holdAttempt(requestB, bothReady, go);

        Future<ResponseEntity<JsonNode>> resultA = POOL.submit(attemptA);
        Future<ResponseEntity<JsonNode>> resultB = POOL.submit(attemptB);

        assertThat(bothReady.await(5, TimeUnit.SECONDS))
                .as("both threads reached the starting gate for eventId %d", eventId)
                .isTrue();
        go.countDown();

        ResponseEntity<JsonNode> responseA = resultA.get(10, TimeUnit.SECONDS);
        ResponseEntity<JsonNode> responseB = resultB.get(10, TimeUnit.SECONDS);

        // Different seats -> no seat-level conflict -> both requests must succeed.
        assertThat(responseA.getStatusCode()).as("holder A for eventId %d", eventId).isEqualTo(HttpStatus.OK);
        assertThat(responseB.getStatusCode()).as("holder B for eventId %d", eventId).isEqualTo(HttpStatus.OK);

        long bookingIdA = responseA.getBody().get("id").asLong();
        long bookingIdB = responseB.getBody().get("id").asLong();

        // The core assertion: exactly ONE PENDING booking for this (userId, eventId), not two.
        assertThat(bookingIdA)
                .as("both concurrent holders for the same (userId=%d, eventId=%d) must land on the "
                        + "same booking, not two separate ones", userId, eventId)
                .isEqualTo(bookingIdB);

        Long pendingBookingCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE user_id = ? AND event_id = ? AND status = 'PENDING'",
                Long.class, userId, eventId);
        assertThat(pendingBookingCount).as("eventId %d", eventId).isEqualTo(1L);

        Long itemCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM booking_items WHERE booking_id = ?", Long.class, bookingIdA);
        assertThat(itemCount).as("eventId %d", eventId).isEqualTo(2L);

        Long seatItemCountA = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM booking_items WHERE booking_id = ? AND seat_id = ?",
                Long.class, bookingIdA, seatIdA);
        Long seatItemCountB = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM booking_items WHERE booking_id = ? AND seat_id = ?",
                Long.class, bookingIdA, seatIdB);
        assertThat(seatItemCountA).as("eventId %d seatIdA", eventId).isEqualTo(1L);
        assertThat(seatItemCountB).as("eventId %d seatIdB", eventId).isEqualTo(1L);
    }

    private Callable<ResponseEntity<JsonNode>> holdAttempt(HoldBookingRequest request,
                                                             CountDownLatch bothReady, CountDownLatch go) {
        return () -> {
            bothReady.countDown();
            go.await();
            return hold(request);
        };
    }
}
