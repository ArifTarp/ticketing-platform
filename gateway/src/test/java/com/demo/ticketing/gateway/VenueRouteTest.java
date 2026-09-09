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
 * Edge behaviour of the venue route (Phase 14): {@code POST /api/v1/venues} is routed to the same
 * target as event ({@code services.event.uri}) and requires the {@code ADMIN} role — see
 * {@code docs/business-rules.md}: "Only ADMIN may create/update venues and events" and
 * {@code SecurityConfig}.
 *
 * <p>No Docker: the "event service" here is a local {@link MockWebServer} stub on a random port,
 * injected into the {@code services.event.uri} property — same pattern as {@link EventRouteTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Keep the breaker out of this test's way; breaker-open behaviour is covered generically
        // by DownstreamUnavailableTest against the auth route.
        "resilience4j.circuitbreaker.instances.venueCircuitBreaker.minimum-number-of-calls=1000",
        "resilience4j.circuitbreaker.instances.venueCircuitBreaker.sliding-window-size=1000"
})
class VenueRouteTest {

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
    void postVenuesWithoutTokenIsRejectedWith401AndNeverReachesEvent() {
        int before = EVENT_STUB.getRequestCount();

        client.post().uri("/api/v1/venues")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Zorlu PSM\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(EVENT_STUB.getRequestCount() - before).isZero();
    }

    @Test
    void postVenuesWithUserRoleTokenIsForbiddenAndNeverReachesEvent() {
        int before = EVENT_STUB.getRequestCount();

        client.post().uri("/api/v1/venues")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Zorlu PSM\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(EVENT_STUB.getRequestCount() - before).isZero();
    }

    @Test
    void postVenuesWithAdminRoleTokenIsForwardedToEvent() throws InterruptedException {
        EVENT_STUB.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":1,\"name\":\"Zorlu PSM\"}"));

        client.post().uri("/api/v1/venues")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Zorlu PSM\"}")
                .exchange()
                .expectStatus().isCreated();

        RecordedRequest forwarded = EVENT_STUB.takeRequest();
        assertThat(forwarded.getMethod()).isEqualTo("POST");
        // Verbatim forwarding, no StripPrefix: event maps its venues controller at
        // /api/v1/venues (same service as /api/v1/events, different resource prefix).
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/venues");
        assertThat(forwarded.getBody().readUtf8()).isEqualTo("{\"name\":\"Zorlu PSM\"}");
    }
}
