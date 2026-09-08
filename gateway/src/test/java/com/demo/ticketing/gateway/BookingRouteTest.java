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
 * Edge behaviour of the booking route (Phase 7): {@code /api/v1/bookings/**} is protected (unlike
 * auth), forwards the path verbatim and the {@code Authorization} header unchanged, and gets the
 * same Resilience4j treatment as every other sync route.
 *
 * <p>No Docker: the "booking service" here is a local {@link MockWebServer} stub on a random port,
 * injected into the {@code services.booking.uri} property — same pattern as
 * {@link AuthRouteSecurityTest} / {@link AuthRouteResilienceTest}, just against a protected route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Keep the breaker out of this test's way; breaker-open behaviour is covered generically
        // by DownstreamUnavailableTest against the auth route.
        "resilience4j.circuitbreaker.instances.bookingCircuitBreaker.minimum-number-of-calls=1000",
        "resilience4j.circuitbreaker.instances.bookingCircuitBreaker.sliding-window-size=1000"
})
class BookingRouteTest {

    private static final MockWebServer BOOKING_STUB = new MockWebServer();

    static {
        try {
            BOOKING_STUB.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        registry.add("services.booking.uri", () -> "http://localhost:" + BOOKING_STUB.getPort());
    }

    @AfterAll
    static void stopStub() throws IOException {
        BOOKING_STUB.shutdown();
    }

    @Autowired
    private WebTestClient client;

    @Test
    void protectedPathWithoutTokenIsRejectedWith401ProblemJsonAndNeverReachesBooking() {
        // Other @Test methods in this class share the same static MockWebServer instance and run
        // in an unspecified order, so assert on the delta rather than an absolute request count.
        int before = BOOKING_STUB.getRequestCount();

        client.get().uri("/api/v1/bookings/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.instance").isEqualTo("/api/v1/bookings/1");

        assertThat(BOOKING_STUB.getRequestCount() - before).isZero();
    }

    @Test
    void validTokenPassesTheEdgeAndForwardsThePathAndAuthorizationHeaderUnchanged()
            throws InterruptedException {
        String token = TestJwt.valid();
        BOOKING_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":1,\"status\":\"PENDING\"}"));

        client.get().uri("/api/v1/bookings/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PENDING");

        RecordedRequest forwarded = BOOKING_STUB.takeRequest();
        // Verbatim forwarding, no StripPrefix: booking maps its controller at /api/v1/bookings/**.
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/bookings/1");
        assertThat(forwarded.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
        // Same identity-propagation rule as auth's route: no trusted headers invented at the edge.
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
    }

    @Test
    void listQueryParametersArePassedThroughUnchanged() throws InterruptedException {
        BOOKING_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        client.get().uri("/api/v1/bookings?userId=42&status=PENDING")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = BOOKING_STUB.takeRequest();
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/bookings?userId=42&status=PENDING");
    }

    @Test
    void holdPostIsNotRetriedOnServerError() {
        // Only one response queued on purpose: a retried POST would find an empty queue and hang,
        // surfacing as a 503 fallback instead of the expected 500 — proving the Retry filter is
        // GET-only, exactly like the auth route's register/login POSTs.
        int before = BOOKING_STUB.getRequestCount();
        BOOKING_STUB.enqueue(new MockResponse().setResponseCode(500));

        client.post().uri("/api/v1/bookings/hold")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":1,\"seatIds\":[1,2]}")
                .exchange()
                .expectStatus().isEqualTo(500);

        assertThat(BOOKING_STUB.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void idempotentGetIsRetriedOnServerError() {
        int before = BOOKING_STUB.getRequestCount();
        BOOKING_STUB.enqueue(new MockResponse().setResponseCode(500));
        BOOKING_STUB.enqueue(new MockResponse().setResponseCode(500));
        BOOKING_STUB.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":1}"));

        client.get().uri("/api/v1/bookings/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .exchange()
                .expectStatus().isOk();

        assertThat(BOOKING_STUB.getRequestCount() - before).isEqualTo(3);
    }
}
