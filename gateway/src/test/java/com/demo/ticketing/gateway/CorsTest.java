package com.demo.ticketing.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for this session's gateway CORS fix: every error the gateway itself answers
 * (preflight, 401, 503 fallback) must carry usable {@code Access-Control-*} headers, exactly once,
 * or the browser silently treats the response as a network error and the whole fix is defeated.
 *
 * <p>No Docker: downstream is either never reached (preflight, 401 on an unrouted path) or a dead
 * port nothing listens on (503 fallback) — same pattern as {@link DownstreamUnavailableTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.circuitbreaker.instances.bookingCircuitBreaker.wait-duration-in-open-state=60s"
})
class CorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    /** A port nothing is listening on, so a routed call fails at connect time -> 503 fallback. */
    private static final int DEAD_PORT = findClosedPort();

    private static int findClosedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void routeToNowhere(DynamicPropertyRegistry registry) {
        registry.add("services.booking.uri", () -> "http://localhost:" + DEAD_PORT);
    }

    @Autowired
    private WebTestClient client;

    @Test
    void preflightOnAProtectedRouteIsAnsweredWithoutAJwtAndReflectsTheRequestedHeader() {
        // A real CORS preflight: no Authorization header (browsers never send one on OPTIONS), just
        // Origin + the method/headers the actual request intends to use.
        client.options().uri("/api/v1/bookings/hold")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> assertThat(value).containsIgnoringCase("Authorization"));
    }

    @Test
    void unauthorizedResponseCarriesExactlyOneAccessControlAllowOriginHeader() {
        client.get().uri("/api/v1/bookings")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt-at-all")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    void fallback503ResponseCarriesExactlyOneAccessControlAllowOriginHeader() {
        client.get().uri("/api/v1/bookings")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void mismatchedOriginGetsNoAccessControlHeadersOnA401() {
        client.get().uri("/api/v1/bookings")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt-at-all")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }
}
