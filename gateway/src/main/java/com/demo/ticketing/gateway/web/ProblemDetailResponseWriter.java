package com.demo.ticketing.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 */
@Component
public class ProblemDetailResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemDetailResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setInstance(URI.create(exchange.getRequest().getPath().value()));

        ServerHttpResponse response = exchange.getResponse();
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
}
