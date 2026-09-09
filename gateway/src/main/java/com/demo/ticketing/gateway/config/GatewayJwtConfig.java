package com.demo.ticketing.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Edge JWT validation: HMAC (symmetric) verification against the same shared secret the auth
 * service signs with. See {@code docs/adr/0002-hmac-shared-secret-jwt-validation.md}.
 *
 * <p>The gateway only ever <em>verifies</em>; it never mints a token. Downstream services validate
 * the very same token independently (defense in depth) — the gateway is not their authority.
 *
 * <p>Algorithm note: auth signs with jjwt's {@code signWith(key)}, which picks the strongest HMAC
 * algorithm the key length supports. The demo secret is 64 characters used as raw bytes = 512 bits,
 * so auth emits <b>HS512</b>. The algorithm is pinned to exactly one value here (no "accept any
 * HS*") so a token signed with a weaker algorithm is rejected rather than silently accepted.
 */
@Configuration
public class GatewayJwtConfig {

    /** JOSE algorithm name -> JCA secret-key algorithm name. */
    private static final Map<String, String> JCA_ALGORITHMS = Map.of(
            "HS256", "HmacSHA256",
            "HS384", "HmacSHA384",
            "HS512", "HmacSHA512");

    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret,
                                         @Value("${security.jwt.mac-algorithm}") String macAlgorithmName) {
        String jcaAlgorithm = JCA_ALGORITHMS.get(macAlgorithmName);
        if (jcaAlgorithm == null) {
            throw new IllegalStateException(
                    "Unsupported security.jwt.mac-algorithm '" + macAlgorithmName
                            + "'. Supported: " + JCA_ALGORITHMS.keySet());
        }
        SecretKey signingKey =
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), jcaAlgorithm);

        // Default validators apply: signature, `exp` and `nbf` (with Spring's 60s clock skew).
        // auth sets no `iss`/`aud`, so there is nothing further to assert here — adding an issuer
        // check is the first thing to do if a second token issuer ever appears.
        return NimbusReactiveJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.from(macAlgorithmName))
                .build();
    }

    /**
     * Maps the {@code roles} claim (a JSON array such as {@code ["ADMIN"]}, as issued by auth —
     * see root {@code CLAUDE.md}) to Spring Security {@code ROLE_*} authorities, so
     * {@code .hasRole("ADMIN")} in {@link SecurityConfig} works.
     *
     * <p>Spring Security's {@code hasRole(x)} auto-prefixes {@code ROLE_} when checking, so the
     * authority produced here must be exactly {@code ROLE_<value>} — never double-prefixed.
     */
    @Bean
    public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        Converter<Jwt, Collection<GrantedAuthority>> rolesConverter = jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .filter(Objects::nonNull)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        };

        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }
}
