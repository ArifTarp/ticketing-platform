package com.demo.ticketing.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Writes RFC 7807 {@code application/problem+json} bodies straight onto a reactive response.
 *
 * <p>Needed because Spring Security's reactive entry point / access-denied handler run before any
 * {@code @RestControllerAdvice}, so they would otherwise emit a bare, body-less 401/403. Root
 * {@code CLAUDE.md} requires every error the platform surfaces to be problem+json.
 *
 * <p>These handlers also run before Spring Cloud Gateway's own {@code globalcors} filter, so an
 * error response they write would otherwise ship with no CORS headers and the browser would
 * silently swallow it as a network error — {@link #addCorsHeaders} re-applies the same allowed
 * origin here. The origin comes from {@code app.cors.allowed-origin}, the same property
 * {@code application.yml}'s {@code globalcors} block resolves, so the two stay in sync from one
 * source instead of two independent literals.
 */
@Component
public class ProblemDetailResponseWriter {

    private final ObjectMapper objectMapper;
    private final String allowedOrigin;

    public ProblemDetailResponseWriter(
            ObjectMapper objectMapper,
            @Value("${app.cors.allowed-origin}") String allowedOrigin) {
        this.objectMapper = objectMapper;
        this.allowedOrigin = allowedOrigin;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setInstance(URI.create(exchange.getRequest().getPath().value()));

        ServerHttpResponse response = exchange.getResponse();
        addCorsHeaders(exchange, response);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(problemDetail);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private void addCorsHeaders(ServerWebExchange exchange, ServerHttpResponse response) {
        String origin = exchange.getRequest().getHeaders().getOrigin();
        if (origin != null && allowedOrigin.equals(origin)) {
            response.getHeaders().add("Access-Control-Allow-Origin", origin);
            response.getHeaders().add("Access-Control-Allow-Credentials", "true");
            response.getHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        }
    }
}
