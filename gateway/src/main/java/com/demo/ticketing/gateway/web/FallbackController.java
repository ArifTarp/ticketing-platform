package com.demo.ticketing.gateway.web;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.Set;

/**
 * Terminal response for a route whose circuit breaker tripped, timed out, or could not connect.
 *
 * <p>Reached only through an internal {@code forward:/fallback/{service}} from the route's
 * CircuitBreaker filter, so it carries no business logic — it just turns "downstream is not
 * answering" into a well-formed 503 problem+json instead of a leaked Netty/connect error.
 *
 * <p>Deliberately generic over {@code {service}}: Phases 5/7 add their routes with
 * {@code fallbackUri: forward:/fallback/event} etc. and need no new code here.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<ProblemDetail> fallback(@PathVariable("service") String service,
                                                  ServerWebExchange exchange) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The '" + service + "' service is not responding. Please retry in a few moments.");
        problemDetail.setInstance(originalRequestPath(exchange));

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    /**
     * The path the client actually called, not {@code /fallback/...} — the gateway stashes it on
     * the exchange before routing.
     */
    private URI originalRequestPath(ServerWebExchange exchange) {
        Set<URI> originalUrls = exchange.getAttribute(
                ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUrls != null && !originalUrls.isEmpty()) {
            URI original = originalUrls.iterator().next();
            return URI.create(original.getRawPath());
        }
        return URI.create(exchange.getRequest().getPath().value());
    }
}
