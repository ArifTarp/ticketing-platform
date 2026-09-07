package com.demo.ticketing.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resilience4j behaviour on the auth route: the time limiter turns a slow downstream into a
 * 503 problem+json fallback, and the retry filter only replays safe/idempotent methods.
 *
 * <p>The circuit breaker is deliberately configured never to open in this context
 * (huge {@code minimum-number-of-calls}) so the test methods stay independent of each other;
 * breaker opening is covered by {@link DownstreamUnavailableTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.timelimiter.instances.authCircuitBreaker.timeout-duration=1s",
        "resilience4j.circuitbreaker.instances.authCircuitBreaker.minimum-number-of-calls=1000",
        "resilience4j.circuitbreaker.instances.authCircuitBreaker.sliding-window-size=1000"
})
class AuthRouteResilienceTest {

    private static final MockWebServer AUTH_STUB = new MockWebServer();

    static {
        try {
            AUTH_STUB.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        registry.add("services.auth.uri", () -> "http://localhost:" + AUTH_STUB.getPort());
    }

    @AfterAll
    static void stopStub() throws IOException {
        AUTH_STUB.shutdown();
    }

    @Autowired
    private WebTestClient client;

    @Test
    void slowDownstreamIsCutOffByTheTimeLimiterAndFallsBackTo503ProblemJson() {
        // 5s downstream delay vs a 1s time limiter: the gateway must not hang waiting for it.
        AUTH_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setHeadersDelay(5, TimeUnit.SECONDS));

        client.mutate().responseTimeout(Duration.ofSeconds(15)).build()
                .post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("Service Unavailable")
                .jsonPath("$.detail").exists();
    }

    @Test
    void idempotentGetIsRetriedOnServerError() {
        int before = AUTH_STUB.getRequestCount();
        AUTH_STUB.enqueue(new MockResponse().setResponseCode(500));
        AUTH_STUB.enqueue(new MockResponse().setResponseCode(500));
        AUTH_STUB.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        client.get().uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.ok").isEqualTo(true);

        assertThat(AUTH_STUB.getRequestCount() - before).isEqualTo(3);
    }

    @Test
    void nonIdempotentPostIsNotRetriedOnServerError() {
        // Only one response is queued on purpose: if the gateway retried the POST, the second
        // attempt would find an empty queue, hang, and end up as a 503 fallback instead of a 500.
        int before = AUTH_STUB.getRequestCount();
        AUTH_STUB.enqueue(new MockResponse().setResponseCode(500));

        client.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"alice@example.com\",\"password\":\"secret\"}")
                .exchange()
                .expectStatus().isEqualTo(500);

        assertThat(AUTH_STUB.getRequestCount() - before).isEqualTo(1);
    }
}
