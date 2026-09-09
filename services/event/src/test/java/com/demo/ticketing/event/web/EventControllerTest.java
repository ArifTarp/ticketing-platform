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
        // Istanbul now also hosts TechHub Convention Center's 8 ON_SALE V3 workshop events, on
        // top of Demo Arena's "Neon Nights Live".
        JsonNode body = get("/api/v1/events?city=istanbul").getBody();

        assertThat(titlesOf(body)).containsExactlyInAnyOrder(
                "Neon Nights Live",
                "React ile Modern Frontend Geliştirme Atölyesi",
                "Kubernetes: Production'a Hazır mısınız?",
                "LLM Tabanlı Uygulama Geliştirme",
                "Sistem Tasarımı Derinlemesine",
                "Veri Mühendisliğine Giriş: Apache Kafka ve Spark",
                "GraphQL ile API Tasarımı",
                "Terraform ile Altyapıyı Kod Olarak Yönetmek",
                "Yapay Zeka Destekli Yazılım Geliştirme Araçları");
    }

    @Test
    void listEventsFiltersByFreeTextTitleSearch() {
        JsonNode body = get("/api/v1/events?q=acoustic").getBody();

        assertThat(titlesOf(body)).containsExactly("Acoustic Evening");
    }

    @Test
    void listEventsFiltersByStartDateRange() {
        // A tight window isolates "Acoustic Evening" (starts +45 days) from the V3 workshop
        // events, several of which also start more than 40 days out.
        String from = Instant.now().plus(44, ChronoUnit.DAYS).toString();
        String to = Instant.now().plus(46, ChronoUnit.DAYS).toString();

        JsonNode body = get("/api/v1/events?from=" + from + "&to=" + to).getBody();

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
        // V2 seeds 2 venues / 4 events / 78 seats / 6 categories; V3 adds 2 larger venues (241 +
        // 168 = 409 seats) and 14 developer/tech workshop events, each with 4 price tiers.
        Map<String, Object> counts = jdbcTemplate.queryForMap(
                "SELECT (SELECT count(*) FROM venues) AS venues, (SELECT count(*) FROM events) AS events, "
                        + "(SELECT count(*) FROM seats) AS seats, "
                        + "(SELECT count(*) FROM seat_categories) AS categories");

        assertThat(counts.get("venues")).isEqualTo(4L);
        assertThat(counts.get("events")).isEqualTo(18L);
        assertThat(counts.get("seats")).isEqualTo(487L);
        assertThat(counts.get("categories")).isEqualTo(62L);
    }

    private ResponseEntity<JsonNode> post(String path, Object body) {
        return restTemplate.postForEntity(path, body, JsonNode.class);
    }

    private long seedVenueId() {
        return jdbcTemplate.queryForObject("SELECT id FROM venues LIMIT 1", Long.class);
    }

    @Test
    void createVenueReturns201WithTheCreatedVenue() {
        Map<String, String> body = Map.of(
                "name", "New Venue " + Instant.now().toEpochMilli(),
                "address", "123 Main St",
                "city", "Ankara");

        ResponseEntity<JsonNode> response = post("/api/v1/venues", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = response.getBody();
        assertThat(created.get("id").asLong()).isPositive();
        assertThat(created.get("name").asText()).isEqualTo(body.get("name"));
        assertThat(created.get("address").asText()).isEqualTo("123 Main St");
        assertThat(created.get("city").asText()).isEqualTo("Ankara");

        // Tests share one Postgres container/schema (no per-test rollback) — clean up so
        // seedDataIsPresentForTheDemo's counts stay accurate regardless of execution order.
        jdbcTemplate.update("DELETE FROM venues WHERE id = ?", created.get("id").asLong());
    }

    @Test
    void createVenueReturns400ProblemDetailWhenAFieldIsBlank() {
        Map<String, String> body = Map.of("name", "", "address", "123 Main St", "city", "Ankara");

        ResponseEntity<JsonNode> response = post("/api/v1/venues", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void createEventReturns201WithTheCreatedEventDefaultingStatusToDraft() {
        long venueId = seedVenueId();
        Map<String, Object> body = Map.of(
                "venueId", venueId,
                "title", "New Admin Event " + Instant.now().toEpochMilli(),
                "description", "created by an admin test",
                "startsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString());

        ResponseEntity<JsonNode> response = post("/api/v1/events", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = response.getBody();
        assertThat(created.get("id").asLong()).isPositive();
        assertThat(created.get("title").asText()).isEqualTo(body.get("title"));
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("venue").get("id").asLong()).isEqualTo(venueId);
        assertThat(created.get("seatCategories")).isEmpty();

        jdbcTemplate.update("DELETE FROM events WHERE id = ?", created.get("id").asLong());
    }

    @Test
    void createEventReturns404ProblemDetailWhenVenueDoesNotExist() {
        Map<String, Object> body = Map.of(
                "venueId", 999999,
                "title", "Orphan Event",
                "startsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString());

        ResponseEntity<JsonNode> response = post("/api/v1/events", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void createEventReturns400ProblemDetailWhenTitleIsMissing() {
        long venueId = seedVenueId();
        Map<String, Object> body = Map.of(
                "venueId", venueId,
                "startsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString());

        ResponseEntity<JsonNode> response = post("/api/v1/events", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void addSeatCategoriesReturns201WithTheCreatedTiers() {
        long eventId = eventIdByTitle("Midnight Rehearsal"); // DRAFT seed event, no pre-existing tiers
        List<Map<String, Object>> body = List.of(
                Map.of("name", "Front Row " + Instant.now().toEpochMilli(), "price", 99.50, "section", "A"),
                Map.of("name", "Rear " + Instant.now().toEpochMilli(), "price", 45.00, "section", "C"));

        ResponseEntity<JsonNode> response = post("/api/v1/events/" + eventId + "/seat-categories", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = response.getBody();
        assertThat(created).hasSize(2);
        assertThat(created.get(0).get("id").asLong()).isPositive();
        assertThat(created.get(0).get("price").decimalValue()).isEqualByComparingTo("99.50");

        jdbcTemplate.update("DELETE FROM seat_categories WHERE id IN (?, ?)",
                created.get(0).get("id").asLong(), created.get(1).get("id").asLong());
    }

    @Test
    void addSeatCategoriesReturns404ProblemDetailWhenEventDoesNotExist() {
        List<Map<String, Object>> body = List.of(Map.of("name", "VIP", "price", 10.00, "section", "A"));

        ResponseEntity<JsonNode> response = post("/api/v1/events/999999/seat-categories", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void addSeatCategoriesReturns400ProblemDetailWhenBodyIsEmpty() {
        long eventId = eventIdByTitle("Midnight Rehearsal");

        ResponseEntity<JsonNode> response = post("/api/v1/events/" + eventId + "/seat-categories", List.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void addSeatCategoriesReturns409ProblemDetailWhenSectionAlreadyPricedForThatEvent() {
        long eventId = eventIdByTitle("Neon Nights Live"); // already prices section A (VIP)
        List<Map<String, Object>> body = List.of(Map.of("name", "Fresh Name", "price", 10.00, "section", "A"));

        ResponseEntity<JsonNode> response = post("/api/v1/events/" + eventId + "/seat-categories", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void adminListEventsReturnsEveryStatusIncludingDraftAndClosed() {
        ResponseEntity<JsonNode> response = get("/api/v1/admin/events");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        // Unlike the public GET /api/v1/events, DRAFT and CLOSED events are included here.
        assertThat(titlesOf(body)).contains(
                "Neon Nights Live", "Acoustic Evening", "Midnight Rehearsal", "Retro Fest");

        JsonNode draft = null;
        for (JsonNode event : body) {
            if (event.get("title").asText().equals("Midnight Rehearsal")) {
                draft = event;
            }
        }
        assertThat(draft).isNotNull();
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void adminListEventsFiltersByCityCaseInsensitivelySameAsThePublicEndpoint() {
        JsonNode body = get("/api/v1/admin/events?city=istanbul").getBody();

        // Demo Arena (Istanbul) hosts Neon Nights Live (ON_SALE), Midnight Rehearsal (DRAFT) and
        // Retro Fest (CLOSED); TechHub Convention Center (also Istanbul) hosts 8 ON_SALE V3
        // workshop events. Riverside Hall/Innovation Campus Auditorium (Acoustic Evening and
        // friends) are a different city (Ankara).
        assertThat(titlesOf(body)).containsExactlyInAnyOrder(
                "Neon Nights Live", "Midnight Rehearsal", "Retro Fest",
                "React ile Modern Frontend Geliştirme Atölyesi",
                "Kubernetes: Production'a Hazır mısınız?",
                "LLM Tabanlı Uygulama Geliştirme",
                "Sistem Tasarımı Derinlemesine",
                "Veri Mühendisliğine Giriş: Apache Kafka ve Spark",
                "GraphQL ile API Tasarımı",
                "Terraform ile Altyapıyı Kod Olarak Yönetmek",
                "Yapay Zeka Destekli Yazılım Geliştirme Araçları");
    }

    @Test
    void adminListEventsFiltersByFreeTextTitleSearch() {
        JsonNode body = get("/api/v1/admin/events?q=retro").getBody();

        assertThat(titlesOf(body)).containsExactly("Retro Fest");
    }

    @Test
    void adminListEventsReturnsAnEmptyArrayWhenNothingMatches() {
        JsonNode body = get("/api/v1/admin/events?city=Atlantis").getBody();

        assertThat(body.isArray()).isTrue();
        assertThat(body).isEmpty();
    }

    private void put(String path, Object body, java.util.function.Consumer<ResponseEntity<JsonNode>> assertion) {
        assertion.accept(restTemplate.exchange(path, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(body), JsonNode.class));
    }

    @Test
    void updateEventReturns200WithTheUpdatedEvent() {
        long venueId = seedVenueId();
        Map<String, Object> createBody = Map.of(
                "venueId", venueId,
                "title", "Update Me " + Instant.now().toEpochMilli(),
                "description", "before update",
                "startsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        JsonNode created = post("/api/v1/events", createBody).getBody();
        long eventId = created.get("id").asLong();

        Map<String, Object> updateBody = Map.of(
                "title", "Updated Title",
                "description", "after update",
                "startsAt", Instant.now().plus(60, ChronoUnit.DAYS).toString(),
                "status", "ON_SALE",
                "imageUrl", "https://picsum.photos/seed/updated/800/450");

        put("/api/v1/events/" + eventId, updateBody, response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = response.getBody();
            assertThat(body.get("id").asLong()).isEqualTo(eventId);
            assertThat(body.get("title").asText()).isEqualTo("Updated Title");
            assertThat(body.get("description").asText()).isEqualTo("after update");
            assertThat(body.get("status").asText()).isEqualTo("ON_SALE");
            assertThat(body.get("imageUrl").asText()).isEqualTo("https://picsum.photos/seed/updated/800/450");
        });

        jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
    }

    @Test
    void updateEventReturns404ProblemDetailWhenTheEventDoesNotExist() {
        Map<String, Object> updateBody = Map.of(
                "title", "Ghost Event",
                "startsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                "status", "ON_SALE");

        put("/api/v1/events/999999", updateBody, response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        });
    }

    @Test
    void deleteEventReturns204AndRemovesTheEventAndItsSeatCategories() {
        long venueId = seedVenueId();
        Map<String, Object> createBody = Map.of(
                "venueId", venueId,
                "title", "Delete Me " + Instant.now().toEpochMilli(),
                "startsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        JsonNode created = post("/api/v1/events", createBody).getBody();
        long eventId = created.get("id").asLong();
        post("/api/v1/events/" + eventId + "/seat-categories",
                List.of(Map.of("name", "Delete Tier", "price", 42.00, "section", "A")));

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/events/" + eventId, org.springframework.http.HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE id = ?", Long.class, eventId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM seat_categories WHERE event_id = ?", Long.class, eventId)).isZero();
    }

    @Test
    void deleteEventReturns404ProblemDetailWhenTheEventDoesNotExist() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/v1/events/999999", org.springframework.http.HttpMethod.DELETE, null, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
