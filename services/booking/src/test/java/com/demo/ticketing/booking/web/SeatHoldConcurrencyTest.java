package com.demo.ticketing.booking.web;

import com.demo.ticketing.booking.web.dto.HoldBookingRequest;
import com.demo.ticketing.booking.web.dto.HoldSeatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The core deliverable of Phase 7's follow-up pass (docs/roadmap.md): two concurrent clients hold
 * the <strong>same single seat</strong> and exactly one must get {@code 200}, the other an
 * immediate {@code 409} — resolved by the Redis distributed lock ({@code SeatHoldLockService}), not
 * a DB unique-constraint race or optimistic-lock retry.
 *
 * <p>Uses a {@link CountDownLatch} pair so both HTTP calls are actually released at (as close to)
 * the same instant as the JVM/thread scheduler allows, rather than firing two sequential requests —
 * a sequential fire wouldn't exercise the race at all. The whole race is repeated across many
 * distinct seats in one test run (not just once) so the assertion is a reliability guarantee, not a
 * single lucky sample; a naive read-then-write implementation would flake on some iterations under
 * this pattern, an atomic {@code SET NX EX} never will.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeatHoldConcurrencyTest {

    private static final int ITERATIONS = 25;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

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

    @Test
    void twoConcurrentHoldsOnTheSameSeatResolveToExactlyOneWinnerEveryTime() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            // A distinct eventId per iteration (not one shared eventId across all 25 iterations):
            // ADR-0004 makes BookingHoldService append new seats to an existing PENDING booking for
            // the same (userId, eventId) pair rather than always creating a new one. User A/B reuse
            // the same userId (1L/2L) across every iteration, so a shared eventId would accumulate
            // all 25 iterations' seats onto one growing booking per user and trip the cumulative
            // max-6-seats rule after iteration 6. Each iteration gets its own fresh (userId,
            // eventId) pair instead, keeping every iteration an independent "first hold" case.
            long eventId = 42_000L + i;
            long seatId = 42_000L + i;
            CountDownLatch bothReady = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Callable<ResponseEntity<JsonNode>> userAAttempt = holdAttempt(eventId, seatId, 1L, bothReady, go);
            Callable<ResponseEntity<JsonNode>> userBAttempt = holdAttempt(eventId, seatId, 2L, bothReady, go);

            Future<ResponseEntity<JsonNode>> resultA = POOL.submit(userAAttempt);
            Future<ResponseEntity<JsonNode>> resultB = POOL.submit(userBAttempt);

            // Wait until both threads are past their HTTP-client setup and blocked on `go`, then
            // release them together so the hold requests actually land at (as close to) the same
            // instant — this is what forces the Redis-lock race rather than a sequential call pair.
            assertThat(bothReady.await(5, TimeUnit.SECONDS))
                    .as("both threads reached the starting gate for seat %d", seatId)
                    .isTrue();
            go.countDown();

            ResponseEntity<JsonNode> responseA = resultA.get(10, TimeUnit.SECONDS);
            ResponseEntity<JsonNode> responseB = resultB.get(10, TimeUnit.SECONDS);

            List<HttpStatus> statuses = List.of(
                    HttpStatus.valueOf(responseA.getStatusCode().value()),
                    HttpStatus.valueOf(responseB.getStatusCode().value()));

            long okCount = statuses.stream().filter(HttpStatus.OK::equals).count();
            long conflictCount = statuses.stream().filter(HttpStatus.CONFLICT::equals).count();

            assertThat(okCount)
                    .as("iteration %d (seat %d): exactly one winner, got statuses %s", i, seatId, statuses)
                    .isEqualTo(1);
            assertThat(conflictCount)
                    .as("iteration %d (seat %d): exactly one loser, got statuses %s", i, seatId, statuses)
                    .isEqualTo(1);

            // Durable outcome check: the DB must agree there is exactly one HELD row for this seat,
            // and exactly one booking_item referencing it (not zero, not two).
            Long heldCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM seat_availability WHERE event_id = ? AND seat_id = ? AND status = 'HELD'",
                    Long.class, eventId, seatId);
            assertThat(heldCount).as("iteration %d (seat %d)", i, seatId).isEqualTo(1L);

            Long bookingItemCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM booking_items WHERE seat_id = ?", Long.class, seatId);
            assertThat(bookingItemCount).as("iteration %d (seat %d)", i, seatId).isEqualTo(1L);
        }
    }

    private Callable<ResponseEntity<JsonNode>> holdAttempt(long eventId, long seatId, long userId,
                                                             CountDownLatch bothReady, CountDownLatch go) {
        HoldBookingRequest request = new HoldBookingRequest(userId, eventId,
                List.of(new HoldSeatRequest(seatId, new BigDecimal("100.00"))));
        return () -> {
            bothReady.countDown();
            go.await();
            return hold(request);
        };
    }
}
