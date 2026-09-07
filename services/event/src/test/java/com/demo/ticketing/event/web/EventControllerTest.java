package com.demo.ticketing.event.web;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP + DB behaviour of the catalog endpoints, against the real Flyway schema and the seeded demo
 * data (V2 migration) in a throwaway Postgres.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Set<String> FORBIDDEN_FIELD_NAMES =
            Set.of("status", "available", "availability", "sold", "held", "state", "reserved");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long eventIdByTitle(String title) {
        return jdbcTemplate.queryForObject("SELECT id FROM events WHERE title = ?", Long.class, title);
    }

    private ResponseEntity<JsonNode> get(String path) {
        return restTemplate.getForEntity(path, JsonNode.class);
    }

    private static List<String> titlesOf(JsonNode arrayNode) {
        return arrayNode.findValuesAsText("title");
    }

    /** Recursively asserts that no object anywhere in the tree carries a seat-state field. */
    private static void assertCarriesNoAvailabilityField(JsonNode node) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(name ->
                    assertThat(FORBIDDEN_FIELD_NAMES).as("field '%s' in seat map response", name)
                            .doesNotContain(name.toLowerCase()));
        }
        node.forEach(EventControllerTest::assertCarriesNoAvailabilityField);
    }

    @Test
    void listEventsReturnsOnlyOnSaleEventsWithTheFieldsTheEventListScreenRenders() {
        ResponseEntity<JsonNode> response = get("/api/v1/events");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(titlesOf(body)).contains("Neon Nights Live", "Acoustic Evening");
        assertThat(titlesOf(body)).doesNotContain("Midnight Rehearsal", "Retro Fest");

        JsonNode neon = body.get(0);
        assertThat(neon.get("title").asText()).isEqualTo("Neon Nights Live");
        assertThat(neon.get("venueName").asText()).isEqualTo("Demo Arena");
        assertThat(neon.get("city").asText()).isEqualTo("Istanbul");
        assertThat(neon.get("status").asText()).isEqualTo("ON_SALE");
        assertThat(neon.get("startsAt").asText()).isNotBlank();
        assertThat(neon.get("fromPrice").decimalValue()).isEqualByComparingTo("60.00");
    }

    @Test
    void listEventsFiltersByCityCaseInsensitively() {
        JsonNode body = get("/api/v1/events?city=istanbul").getBody();

        assertThat(titlesOf(body)).containsExactly("Neon Nights Live");
    }

    @Test
    void listEventsFiltersByFreeTextTitleSearch() {
        JsonNode body = get("/api/v1/events?q=acoustic").getBody();

        assertThat(titlesOf(body)).containsExactly("Acoustic Evening");
    }

    @Test
    void listEventsFiltersByStartDateRange() {
        String from = Instant.now().plus(40, ChronoUnit.DAYS).toString();

        JsonNode body = get("/api/v1/events?from=" + from).getBody();

        assertThat(titlesOf(body)).containsExactly("Acoustic Evening");
    }

    @Test
    void listEventsReturnsAnEmptyArrayWhenNothingMatches() {
        JsonNode body = get("/api/v1/events?city=Atlantis").getBody();

        assertThat(body.isArray()).isTrue();
        assertThat(body).isEmpty();
    }

    @Test
    void getEventReturnsDetailWithVenueAndPriceTiers() {
        long eventId = eventIdByTitle("Neon Nights Live");

        ResponseEntity<JsonNode> response = get("/api/v1/events/" + eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("title").asText()).isEqualTo("Neon Nights Live");
        assertThat(body.get("description").asText()).isNotBlank();
        assertThat(body.get("status").asText()).isEqualTo("ON_SALE");
        assertThat(body.get("venue").get("name").asText()).isEqualTo("Demo Arena");
        assertThat(body.get("venue").get("city").asText()).isEqualTo("Istanbul");
        assertThat(body.get("seatCategories").findValuesAsText("name"))
                .containsExactly("VIP", "Standard", "Balcony");
        assertThat(body.get("seatCategories").get(0).get("price").decimalValue())
                .isEqualByComparingTo("250.00");
    }

    @Test
    void getEventReturns404ProblemDetailWhenTheEventDoesNotExist() {
        ResponseEntity<JsonNode> response = get("/api/v1/events/999999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().get("detail").asText()).isEqualTo("Event 999999 was not found");
    }

    @Test
    void getEventSeatsReturnsTheStaticLayoutWithPricingAndNoAvailability() {
        long eventId = eventIdByTitle("Neon Nights Live");

        ResponseEntity<JsonNode> response = get("/api/v1/events/" + eventId + "/seats");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("eventId").asLong()).isEqualTo(eventId);
        assertThat(body.get("venueId").asLong()).isPositive();

        JsonNode seats = body.get("seats");
        assertThat(seats).hasSize(66); // Demo Arena: A 2x8 + B 3x10 + C 2x10

        JsonNode first = seats.get(0);
        assertThat(first.get("seatId").asLong()).isPositive();
        assertThat(first.get("section").asText()).isEqualTo("A");
        assertThat(first.get("row").asText()).isEqualTo("1");
        assertThat(first.get("number").asInt()).isEqualTo(1);
        assertThat(first.get("seatCategory").get("name").asText()).isEqualTo("VIP");
        assertThat(first.get("seatCategory").get("price").decimalValue()).isEqualByComparingTo("250.00");

        // ADR-0001: the event service knows nothing about AVAILABLE/HELD/SOLD.
        assertCarriesNoAvailabilityField(body);
    }

    @Test
    void getEventSeatsOmitsVenueSectionsThatTheEventDoesNotSell() {
        long eventId = eventIdByTitle("Retro Fest"); // priced for section B only

        JsonNode body = get("/api/v1/events/" + eventId + "/seats").getBody();

        assertThat(body.get("seats")).hasSize(30); // section B: 3 rows x 10 seats
        assertThat(body.get("seats").findValuesAsText("section")).containsOnly("B");
    }

    @Test
    void getEventSeatsReturns404ProblemDetailWhenTheEventDoesNotExist() {
        ResponseEntity<JsonNode> response = get("/api/v1/events/999999/seats");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void badlyTypedPathVariableReturns400ProblemDetail() {
        ResponseEntity<JsonNode> response = get("/api/v1/events/not-a-number");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void seedDataIsPresentForTheDemo() {
        Map<String, Object> counts = jdbcTemplate.queryForMap(
                "SELECT (SELECT count(*) FROM venues) AS venues, (SELECT count(*) FROM events) AS events, "
                        + "(SELECT count(*) FROM seats) AS seats, "
                        + "(SELECT count(*) FROM seat_categories) AS categories");

        assertThat(counts.get("venues")).isEqualTo(2L);
        assertThat(counts.get("events")).isEqualTo(4L);
        assertThat(counts.get("seats")).isEqualTo(78L);
        assertThat(counts.get("categories")).isEqualTo(6L);
    }
}
