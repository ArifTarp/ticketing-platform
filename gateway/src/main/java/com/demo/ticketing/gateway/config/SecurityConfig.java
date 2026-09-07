package com.demo.ticketing.gateway.config;

import com.demo.ticketing.gateway.web.ProblemDetailAccessDeniedHandler;
import com.demo.ticketing.gateway.web.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * The edge authorization rule: auth is public, everything else needs a valid JWT.
 *
 * <p>Deny-by-default — a new route added in a later phase is protected unless it is explicitly
 * listed in {@link #PUBLIC_PATHS}. No role checks live here yet; the first one arrives with the
 * ADMIN-gated admin routes (Phase 14).
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

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder,
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
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Both handlers must be set on the resource-server spec as well as on
                        // exceptionHandling: the bearer-token filter installs its own defaults
                        // otherwise, and we would leak a body-less 401.
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
