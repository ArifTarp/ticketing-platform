package com.demo.ticketing.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    private HttpEntity<String> jsonRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<JsonNode> register(String email, String password) {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        return restTemplate.postForEntity("/api/v1/auth/register", jsonRequest(body), JsonNode.class);
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        return restTemplate.postForEntity("/api/v1/auth/login", jsonRequest(body), JsonNode.class);
    }

    @Test
    void registerWithNewEmailReturnsJwtToken() {
        ResponseEntity<JsonNode> response = register("alice@example.com", "supersecret1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("token").asText()).isNotBlank();
        assertThat(response.getBody().has("passwordHash")).isFalse();
        assertThat(response.getBody().has("password")).isFalse();
    }

    @Test
    void registerWithAlreadyUsedEmailReturns409ProblemDetail() {
        register("bob@example.com", "supersecret1");

        ResponseEntity<JsonNode> response = register("bob@example.com", "anotherpassword");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().get("detail").asText())
                .isEqualTo("An account with this email already exists");
    }

    @Test
    void loginWithCorrectCredentialsReturnsValidJwt() {
        register("carol@example.com", "supersecret1");

        ResponseEntity<JsonNode> response = login("carol@example.com", "supersecret1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = response.getBody().get("token").asText();
        assertThat(token).isNotBlank();

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        assertThat(claims.get("email", String.class)).isEqualTo("carol@example.com");
        assertThat(claims.get("roles", java.util.List.class)).containsExactly("USER");
    }

    @Test
    void loginWithWrongPasswordReturns401ProblemDetail() {
        register("dave@example.com", "supersecret1");

        ResponseEntity<JsonNode> response = login("dave@example.com", "wrongpassword");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().get("detail").asText())
                .isEqualTo("Invalid email or password");
    }

    @Test
    void loginWithUnknownEmailReturns401ProblemDetail() {
        ResponseEntity<JsonNode> response = login("unknown@example.com", "whatever123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("detail").asText())
                .isEqualTo("Invalid email or password");
    }

    @Test
    void registerWithShortPasswordReturns400ProblemDetail() {
        ResponseEntity<JsonNode> response = register("erin@example.com", "short");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
