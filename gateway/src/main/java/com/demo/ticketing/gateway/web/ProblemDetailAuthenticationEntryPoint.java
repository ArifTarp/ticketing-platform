package com.demo.ticketing.gateway.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 401 for anything that reaches a protected route without a usable JWT, as problem+json.
 *
 * <p>The detail distinguishes "no credentials at all" from "credentials rejected" without echoing
 * the decoder's internal failure message back to the caller.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final String MISSING_TOKEN =
            "Authentication required: send a valid JWT as 'Authorization: Bearer <token>'.";
    private static final String INVALID_TOKEN =
            "The provided JWT was rejected: it is malformed, expired, or not signed by this platform.";

    private final ProblemDetailResponseWriter responseWriter;

    public ProblemDetailAuthenticationEntryPoint(ProblemDetailResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String detail = StringUtils.hasText(authorization) ? INVALID_TOKEN : MISSING_TOKEN;
        return responseWriter.write(exchange, HttpStatus.UNAUTHORIZED, detail);
    }
}
