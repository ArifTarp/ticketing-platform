package com.demo.ticketing.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 403 as problem+json: the caller is authenticated, but the token's roles do not allow this route.
 *
 * <p>No route needs a role yet (the first one lands with the ADMIN-gated endpoints in Phase 14),
 * but the contract is wired and tested now so role-gating a route later is a one-line change in
 * {@code SecurityConfig}.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements ServerAccessDeniedHandler {

    private static final String DETAIL =
            "Your token is valid but does not grant access to this resource.";

    private final ProblemDetailResponseWriter responseWriter;

    public ProblemDetailAccessDeniedHandler(ProblemDetailResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return responseWriter.write(exchange, HttpStatus.FORBIDDEN, DETAIL);
    }
}
