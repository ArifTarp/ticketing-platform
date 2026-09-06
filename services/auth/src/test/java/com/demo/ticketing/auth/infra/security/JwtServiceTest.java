package com.demo.ticketing.auth.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    // 64-char hex string -> 32 bytes, well over HS256's 256-bit minimum key length.
    private static final String SECRET = "1e5e6f3c8a2b4d7e9f0c1a3b5d7e9f1c3a5b7d9e1f3a5c7b9d1e3f5a7c9b1d3e";
    private static final long EXPIRATION_SECONDS = 3600L;

    private final JwtService jwtService = new JwtService(SECRET, EXPIRATION_SECONDS);

    @Test
    void issueTokenEmbedsSubjectEmailAndRolesClaims() {
        String token = jwtService.issueToken(42L, "alice@example.com", List.of("USER"));

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("alice@example.com");
        assertThat(claims.get("roles", List.class)).containsExactly("USER");

        long expiresInSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(expiresInSeconds).isEqualTo(EXPIRATION_SECONDS);
    }
}
