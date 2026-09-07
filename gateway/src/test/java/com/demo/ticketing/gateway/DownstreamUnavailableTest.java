package com.demo.ticketing.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the client sees when the downstream service is simply not running: a 503 RFC 7807 body from
 * the gateway's own fallback, never a raw connection error — and after a couple of failures the
 * circuit breaker opens so we stop hammering a dead service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.circuitbreaker.instances.authCircuitBreaker.sliding-window-size=2",
        "resilience4j.circuitbreaker.instances.authCircuitBreaker.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.authCircuitBreaker.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.authCircuitBreaker.wait-duration-in-open-state=60s"
})
class DownstreamUnavailableTest {

    /** A port nothing is listening on, so every attempt fails at connect time. */
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
        registry.add("services.auth.uri", () -> "http://localhost:" + DEAD_PORT);
    }

    @Autowired
    private WebTestClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void unreachableDownstreamYields503ProblemJsonAndEventuallyOpensTheBreaker() {
        for (int attempt = 0; attempt < 2; attempt++) {
            expectFallback();
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("authCircuitBreaker").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Breaker open: still a well-formed 503 for the client, now short-circuited.
        expectFallback();
    }

    private void expectFallback() {
        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"alice@example.com\",\"password\":\"secret\"}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("Service Unavailable");
    }
}
