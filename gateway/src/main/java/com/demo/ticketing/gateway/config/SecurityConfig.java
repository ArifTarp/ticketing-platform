package com.demo.ticketing.gateway.config;

import com.demo.ticketing.gateway.web.ProblemDetailAccessDeniedHandler;
import com.demo.ticketing.gateway.web.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * The edge authorization rule: auth is public, everything else needs a valid JWT.
 *
 * <p>Deny-by-default — a new route added in a later phase is protected unless it is explicitly
 * listed in {@link #PUBLIC_PATHS}. Phase 14 adds the first role check: {@code POST} on the event
 * catalog's write endpoints and on venues requires the {@code ADMIN} role (see
 * {@code docs/business-rules.md}: "Only ADMIN may create/update venues and events"). The
 * {@code roles} JWT claim is mapped to {@code ROLE_*} authorities by
 * {@link GatewayJwtConfig#jwtAuthenticationConverter()}.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            // Registration and login: the caller has no token yet, by definition.
            "/api/v1/auth/**",
            // Liveness/readiness for Docker Compose and future orchestration.
            "/actuator/health",
            "/actuator/health/**",
            // Internal forward target of the Resilience4j fallbacks. Reached via an internal
            // forward (which bypasses this filter chain); permitted so a direct hit still returns
            // the same harmless 503 problem+json instead of a confusing 401.
            "/fallback/**"
    };

    /**
     * Paths gated by an explicit HTTP method rather than a blanket path match — see the
     * {@code authorizeExchange} ordering note on {@link #securityWebFilterChain}.
     */
    private static final String EVENTS_PATH = "/api/v1/events/**";
    private static final String VENUES_PATH = "/api/v1/venues/**";
    private static final String ADMIN_EVENTS_PATH = "/api/v1/admin/**";

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder,
            ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler) {

        return http
                // Stateless bearer-token API with no cookies or sessions, so CSRF protection and
                // the browser login mechanisms are irrelevant here.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // CORS preflight requests are plain browser-generated OPTIONS calls that
                        // never carry an Authorization header (the spec forbids it), so gating
                        // OPTIONS behind JWT auth would make every cross-origin preflight fail
                        // with 401 and the browser would never even attempt the real request.
                        // This permitAll() is not redundant with globalcors — do not remove it.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Order matters: authorizeExchange evaluates rules in declaration order and
                        // stops at the first match. The ADMIN-gated POST rules for events/venues
                        // must come BEFORE the GET-only public-catalog rule and PUBLIC_PATHS'
                        // permitAll(), otherwise a blanket path-based permitAll() on
                        // /api/v1/events/** would short-circuit before the role check ever runs and
                        // a POST would sail through unauthenticated.
                        .pathMatchers(HttpMethod.POST, EVENTS_PATH, VENUES_PATH).hasRole("ADMIN")
                        // GET /api/v1/admin/events (all statuses, for the admin table) requires
                        // ADMIN too. Unlike the POST rule above, ordering relative to the other
                        // rules is not delicate here: /api/v1/admin/** does not overlap any
                        // existing public-path prefix, so this could move freely as long as it
                        // stays before anyExchange().authenticated().
                        .pathMatchers(HttpMethod.GET, ADMIN_EVENTS_PATH).hasRole("ADMIN")
                        // event's catalog reads are public per services/event/CLAUDE.md ("No
                        // security/JWT. The catalog reads are public.") — browsing the event
                        // list/detail/seat map needs no login (docs/user-flow.md screens 2-4).
                        // Explicitly GET-only (unlike the old blanket /api/v1/events/** entry) since
                        // Phase 14 added POST write endpoints under the same prefix that require
                        // ADMIN instead, handled by the rule above.
                        .pathMatchers(HttpMethod.GET, EVENTS_PATH).permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Both handlers must be set on the resource-server spec as well as on
                        // exceptionHandling: the bearer-token filter installs its own defaults
                        // otherwise, and we would leak a body-less 401.
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
