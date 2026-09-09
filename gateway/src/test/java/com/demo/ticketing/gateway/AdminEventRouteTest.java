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
 * Edge behaviour of the admin-events route (Phase 14): {@code GET /api/v1/admin/events} is routed
 * to the same target as event ({@code services.event.uri}) and requires the {@code ADMIN} role —
 * the public {@code GET /api/v1/events} stays ON_SALE-only and unaffected; this is a separate
 * prefix for the admin screen's table of all event statuses. See {@code SecurityConfig}.
 *
 * <p>No Docker: the "event service" here is a local {@link MockWebServer} stub on a random port,
 * injected into the {@code services.event.uri} property — same pattern as {@link VenueRouteTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Keep the breaker out of this test's way; breaker-open behaviour is covered generically
        // by DownstreamUnavailableTest against the auth route.
        "resilience4j.circuitbreaker.instances.adminEventCircuitBreaker.minimum-number-of-calls=1000",
        "resilience4j.circuitbreaker.instances.adminEventCircuitBreaker.sliding-window-size=1000"
})
class AdminEventRouteTest {

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
    void getAdminEventsWithoutTokenIsRejectedWith401AndNeverReachesEvent() {
        int before = EVENT_STUB.getRequestCount();

        client.get().uri("/api/v1/admin/events")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(EVENT_STUB.getRequestCount() - before).isZero();
    }

    @Test
    void getAdminEventsWithUserRoleTokenIsForbiddenAndNeverReachesEvent() {
        int before = EVENT_STUB.getRequestCount();

        client.get().uri("/api/v1/admin/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(EVENT_STUB.getRequestCount() - before).isZero();
    }

    @Test
    void getAdminEventsWithAdminRoleTokenIsForwardedToEventVerbatimWithQueryParams()
            throws InterruptedException {
        EVENT_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        client.get().uri("/api/v1/admin/events?status=DRAFT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validAdmin())
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = EVENT_STUB.takeRequest();
        assertThat(forwarded.getMethod()).isEqualTo("GET");
        // Verbatim forwarding, no StripPrefix: event maps its admin-events controller at
        // /api/v1/admin/events (same service as /api/v1/events, different resource prefix).
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/admin/events?status=DRAFT");
    }
}
