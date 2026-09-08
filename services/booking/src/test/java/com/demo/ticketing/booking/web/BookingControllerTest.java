package com.demo.ticketing.booking.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP + DB behaviour of the read-only booking endpoints, against the real Flyway schema in a
 * throwaway Postgres. No seed migration for this service (root-agent scope) — every test builds
 * its own fixtures via {@link JdbcTemplate}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ResponseEntity<JsonNode> get(String path) {
        return restTemplate.getForEntity(path, JsonNode.class);
    }

    private long insertBooking(long userId, long eventId, String status, BigDecimal total,
                                Instant createdAt, Instant expiresAt) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO bookings (user_id, event_id, status, total, created_at, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class, userId, eventId, status, total,
                Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    private void insertBookingItem(long bookingId, long seatId, BigDecimal price) {
        jdbcTemplate.update("""
                        INSERT INTO booking_items (booking_id, seat_id, price) VALUES (?, ?, ?)
                        """,
                bookingId, seatId, price);
    }

    @Test
    void getBookingReturnsTheBookingWithItsItems() {
        Instant createdAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant expiresAt = createdAt.plus(10, ChronoUnit.MINUTES);
        long bookingId = insertBooking(1L, 10L, "PENDING", new BigDecimal("370.00"), createdAt, expiresAt);
        insertBookingItem(bookingId, 1000L, new BigDecimal("250.00"));
        insertBookingItem(bookingId, 1001L, new BigDecimal("120.00"));

        ResponseEntity<JsonNode> response = get("/api/v1/bookings/" + bookingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("id").asLong()).isEqualTo(bookingId);
        assertThat(body.get("userId").asLong()).isEqualTo(1L);
        assertThat(body.get("eventId").asLong()).isEqualTo(10L);
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("total").decimalValue()).isEqualByComparingTo("370.00");
        assertThat(body.get("expiresAt").asText()).isNotBlank();
        assertThat(body.get("items")).hasSize(2);
        assertThat(body.get("items").findValuesAsText("seatId")).containsExactlyInAnyOrder("1000", "1001");
    }

    @Test
    void getBookingReturns404ProblemDetailWhenTheBookingDoesNotExist() {
        ResponseEntity<JsonNode> response = get("/api/v1/bookings/999999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().get("detail").asText()).isEqualTo("Booking 999999 was not found");
    }

    @Test
    void getBookingReturns400ProblemDetailForABadlyTypedId() {
        ResponseEntity<JsonNode> response = get("/api/v1/bookings/not-a-number");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void listBookingsReturnsOnlyTheGivenUsersBookingsNewestFirst() {
        Instant now = Instant.now();
        long userId = 42L;
        long older = insertBooking(userId, 10L, "PENDING", new BigDecimal("50.00"),
                now.minus(2, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS).plus(10, ChronoUnit.MINUTES));
        long newer = insertBooking(userId, 11L, "CONFIRMED", new BigDecimal("80.00"),
                now.minus(1, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS).plus(10, ChronoUnit.MINUTES));
        insertBooking(99L, 12L, "PENDING", new BigDecimal("30.00"), now, now.plus(10, ChronoUnit.MINUTES));

        ResponseEntity<JsonNode> response = get("/api/v1/bookings?userId=" + userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.findValuesAsText("id")).containsExactly(String.valueOf(newer), String.valueOf(older));
    }

    @Test
    void listBookingsFiltersByStatus() {
        long userId = 43L;
        Instant now = Instant.now();
        insertBooking(userId, 10L, "PENDING", new BigDecimal("50.00"), now, now.plus(10, ChronoUnit.MINUTES));
        long confirmed = insertBooking(userId, 11L, "CONFIRMED", new BigDecimal("80.00"),
                now, now.plus(10, ChronoUnit.MINUTES));

        ResponseEntity<JsonNode> response = get("/api/v1/bookings?userId=" + userId + "&status=CONFIRMED");

        JsonNode body = response.getBody();
        assertThat(body.findValuesAsText("id")).containsExactly(String.valueOf(confirmed));
    }

    @Test
    void listBookingsReturnsAnEmptyArrayWhenTheUserHasNoBookings() {
        ResponseEntity<JsonNode> response = get("/api/v1/bookings?userId=123456789");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isArray()).isTrue();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void listBookingsReturns400ProblemDetailWhenUserIdIsMissing() {
        ResponseEntity<JsonNode> response = get("/api/v1/bookings");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void listBookingsReturns400ProblemDetailForAnUnknownStatusValue() {
        ResponseEntity<JsonNode> response = get("/api/v1/bookings?userId=1&status=NOT_A_STATUS");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void schemaHasTheFourTablesFromTheDataModel() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' ORDER BY table_name
                """, String.class);

        assertThat(tables).contains("bookings", "booking_items", "seat_availability", "saga_state");
    }
}
