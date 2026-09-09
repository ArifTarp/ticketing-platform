package com.demo.ticketing.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge behaviour of the event route (Phase 5, wired in during the Phase 7 gap-fix):
 * {@code /api/v1/events/**} is public (unlike auth/booking — see {@code SecurityConfig}, event's
 * catalog reads need no login per {@code services/event/CLAUDE.md}), forwards the path/query
 * verbatim, and gets the same Resilience4j treatment as every other sync route. Every event
 * endpoint is GET-only, so — unlike booking — there is no unsafe method to exclude from Retry.
 *
 * <p>No Docker: the "event service" here is a local {@link MockWebServer} stub on a random port,
 * injected into the {@code services.event.uri} property — same pattern as
 * {@link AuthRouteSecurityTest} / {@link BookingRouteTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Keep the breaker out of this test's way; breaker-open behaviour is covered generically
        // by DownstreamUnavailableTest against the auth route.
        "resilience4j.circuitbreaker.instances.eventCircuitBreaker.minimum-number-of-calls=1000",
        "resilience4j.circuitbreaker.instances.eventCircuitBreaker.sliding-window-size=1000"
})
class EventRouteTest {

    private static final MockWebServer EVENT_STUB = new MockWebServer();

    static {
        try {
            EVENT_STUB.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        registry.add("services.event.uri", () -> "http://localhost:" + EVENT_STUB.getPort());
    }

    @AfterAll
    static void stopStub() throws IOException {
        EVENT_STUB.shutdown();
    }

    @Autowired
    private WebTestClient client;

    @Test
    void publicPathWithoutTokenReachesEventAndForwardsThePathUnchanged() throws InterruptedException {
        EVENT_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"id\":1,\"title\":\"Rock Night\"}]"));

        client.get().uri("/api/v1/events")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].title").isEqualTo("Rock Night");

        RecordedRequest forwarded = EVENT_STUB.takeRequest();
        // Verbatim forwarding, no StripPrefix: event maps its controller at /api/v1/events/**.
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/events");
    }

    @Test
    void authorizationHeaderIsForwardedUnchangedWhenPresentAndNoIdentityHeadersAreInvented()
            throws InterruptedException {
        String token = TestJwt.valid();
        EVENT_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":1,\"title\":\"Rock Night\"}"));

        // A logged-in browser still sends its token even on a public path; the gateway must not
        // strip it, and must not invent trusted identity headers either way (same rule as auth's
        // and booking's routes).
        client.get().uri("/api/v1/events/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = EVENT_STUB.takeRequest();
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/events/1");
        assertThat(forwarded.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
    }

    @Test
    void listQueryParametersArePassedThroughUnchanged() throws InterruptedException {
        EVENT_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        client.get().uri("/api/v1/events?city=Istanbul&q=rock")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = EVENT_STUB.takeRequest();
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/events?city=Istanbul&q=rock");
    }

    @Test
    void seatMapSubPathIsRoutedToEventToo() throws InterruptedException {
        EVENT_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"eventId\":1,\"venueId\":1,\"seats\":[]}"));

        client.get().uri("/api/v1/events/1/seats")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = EVENT_STUB.takeRequest();
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/events/1/seats");
    }

    @Test
    void postEventsWithoutTokenIsRejectedWith401AndNeverReachesEvent() {
        int before = EVENT_STUB.getRequestCount();

        client.post().uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Rock Night\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(EVENT_STUB.getRequestCount() - before).isZero();
    }

    @Test
    void postEventsWithUserRoleTokenIsForbiddenAndNeverReachesEvent() {
        int before = EVENT_STUB.getRequestCount();

        client.post().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Rock Night\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(EVENT_STUB.getRequestCount() - before).isZero();
    }

    @Test
    void postEventsWithAdminRoleTokenIsForwardedToEvent() throws InterruptedException {
        EVENT_STUB.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":1,\"title\":\"Rock Night\"}"));

        client.post().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Rock Night\"}")
                .exchange()
                .expectStatus().isCreated();

        RecordedRequest forwarded = EVENT_STUB.takeRequest();
        assertThat(forwarded.getMethod()).isEqualTo("POST");
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/events");
        assertThat(forwarded.getBody().readUtf8()).isEqualTo("{\"title\":\"Rock Night\"}");
    }

    @Test
    void postSeatCategoriesSubPathWithUserRoleTokenIsForbidden() {
        int before = EVENT_STUB.getRequestCount();

        client.post().uri("/api/v1/events/1/seat-categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"VIP\",\"price\":100}")
                .exchange()
                .expectStatus().isForbidden();

        assertThat(EVENT_STUB.getRequestCount() - before).isZero();
    }

    @Test
    void idempotentGetIsRetriedOnServerError() throws InterruptedException {
        int before = EVENT_STUB.getRequestCount();
        EVENT_STUB.enqueue(new MockResponse().setResponseCode(500));
        EVENT_STUB.enqueue(new MockResponse().setResponseCode(500));
        EVENT_STUB.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":1}"));

        client.get().uri("/api/v1/events/1")
                .exchange()
                .expectStatus().isOk();

        assertThat(EVENT_STUB.getRequestCount() - before).isEqualTo(3);
        // Drain all three recorded requests: other @Test methods in this class share the same
        // static MockWebServer and call takeRequest() themselves, so any request left un-taken
        // here would be handed to the next test's takeRequest() call instead (JUnit does not
        // guarantee method execution order).
        for (int i = 0; i < 3; i++) {
            EVENT_STUB.takeRequest();
        }
    }
}
