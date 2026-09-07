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
 * Edge behaviour of the gateway: the auth route is open, everything else needs a valid JWT, and
 * every rejection is RFC 7807 {@code application/problem+json} — never a bare empty 401.
 *
 * <p>No Docker: the "auth service" here is a local {@link MockWebServer} stub on a random port,
 * injected into the {@code services.auth.uri} property.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthRouteSecurityTest {

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
    void authRouteIsPubliclyAccessibleWithoutAToken() throws InterruptedException {
        AUTH_STUB.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"token\":\"stub-token\"}"));

        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"alice@example.com\",\"password\":\"secret\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.token").isEqualTo("stub-token");

        RecordedRequest forwarded = AUTH_STUB.takeRequest();
        // Path is forwarded verbatim: auth maps its controller at /api/v1/auth/**, so no StripPrefix.
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/auth/login");
    }

    @Test
    void forwardsTheAuthorizationHeaderDownstreamUnchangedAndAddsNoIdentityHeaders()
            throws InterruptedException {
        String token = TestJwt.valid();
        AUTH_STUB.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        client.get().uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = AUTH_STUB.takeRequest();
        assertThat(forwarded.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
        // Downstream services validate the JWT themselves (root CLAUDE.md) — the gateway must not
        // invent trusted identity headers.
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
        assertThat(forwarded.getHeader("X-User-Roles")).isNull();
    }

    @Test
    void protectedPathWithoutTokenIsRejectedWith401ProblemJson() {
        client.get().uri("/api/v1/events")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Unauthorized")
                .jsonPath("$.detail").exists()
                .jsonPath("$.instance").isEqualTo("/api/v1/events");
    }

    @Test
    void protectedPathWithGarbageTokenIsRejectedWith401ProblemJson() {
        client.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt-at-all")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void protectedPathWithTokenSignedByTheWrongSecretIsRejectedWith401() {
        client.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.signedWithWrongSecret())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void protectedPathWithExpiredTokenIsRejectedWith401() {
        client.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.expired())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void protectedPathWithTokenSignedUsingADifferentMacAlgorithmIsRejectedWith401() {
        // Right secret, wrong algorithm: the decoder is pinned to one MAC algorithm on purpose.
        client.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.signedWithHs256())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedPathWithValidTokenPassesTheEdge() {
        // No route exists for /api/v1/events yet (Phase 5), so a 404 from the gateway itself is the
        // expected outcome. What matters is that the token was accepted: not 401, not 403.
        client.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.valid())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void actuatorHealthIsOpen() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
