package com.demo.ticketing.gateway;

import com.demo.ticketing.gateway.web.ProblemDetailAccessDeniedHandler;
import com.demo.ticketing.gateway.web.ProblemDetailAuthenticationEntryPoint;
import com.demo.ticketing.gateway.web.ProblemDetailResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two Spring Security failure paths must produce RFC 7807 bodies, not Spring Security's
 * default empty 401/403 responses.
 */
class ProblemDetailHandlersTest {

    private final ProblemDetailResponseWriter writer = new ProblemDetailResponseWriter(new ObjectMapper());

    @Test
    void authenticationFailureIsWrittenAsProblemJson401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bookings"));
        AuthenticationException failure = new BadCredentialsException("bad token");

        new ProblemDetailAuthenticationEntryPoint(writer).commence(exchange, failure).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        DocumentContext body = JsonPath.parse(exchange.getResponse().getBodyAsString().block());
        assertThat(body.read("$.status", Integer.class)).isEqualTo(401);
        assertThat(body.read("$.title", String.class)).isEqualTo("Unauthorized");
        assertThat(body.read("$.detail", String.class)).isNotBlank();
        assertThat(body.read("$.instance", String.class)).isEqualTo("/api/v1/bookings");
    }

    @Test
    void accessDeniedIsWrittenAsProblemJson403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/events"));

        new ProblemDetailAccessDeniedHandler(writer)
                .handle(exchange, new AccessDeniedException("nope")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        DocumentContext body = JsonPath.parse(exchange.getResponse().getBodyAsString().block());
        assertThat(body.read("$.status", Integer.class)).isEqualTo(403);
        assertThat(body.read("$.title", String.class)).isEqualTo("Forbidden");
        assertThat(body.read("$.instance", String.class)).isEqualTo("/api/v1/admin/events");
    }
}
