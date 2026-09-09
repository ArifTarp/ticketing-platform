package com.demo.ticketing.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Mints JWTs that look exactly like the ones the auth service issues (same shared secret, same
 * MAC algorithm, same {@code sub}/{@code email}/{@code roles} claims), so gateway tests never
 * need auth (or Docker) running.
 *
 * <p>Note on the algorithm: auth signs with jjwt's {@code signWith(key)}, which auto-selects the
 * strongest HMAC algorithm the key length supports. The demo secret is a 64-character string used
 * as raw bytes = 512 bits, so auth actually emits <b>HS512</b>, not HS256.
 */
final class TestJwt {

    /** Same literal as the {@code TICKETING_JWT_SECRET} local-dev default on both sides. */
    static final String SECRET = "1e5e6f3c8a2b4d7e9f0c1a3b5d7e9f1c3a5b7d9e1f3a5c7b9d1e3f5a7c9b1d3e";

    /** Same length/shape as SECRET, but a different value — used for the "wrong signer" case. */
    static final String WRONG_SECRET = "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff6666aaaa7777bbbb8888";

    private TestJwt() {
    }

    /** A token the gateway must accept: right secret, right algorithm, not expired. */
    static String valid() {
        Instant now = Instant.now();
        return signed(JWSAlgorithm.HS512, SECRET, now, now.plusSeconds(3600));
    }

    /** Same as {@link #valid()} but with an ADMIN role, for the Phase 14 role-gated routes. */
    static String validAdmin() {
        Instant now = Instant.now();
        return signed(JWSAlgorithm.HS512, SECRET, now, now.plusSeconds(3600), List.of("ADMIN"));
    }

    /** Right secret and algorithm, but the expiry is already in the past. */
    static String expired() {
        Instant now = Instant.now();
        return signed(JWSAlgorithm.HS512, SECRET, now.minusSeconds(7200), now.minusSeconds(3600));
    }

    /** Correct shape, signed by someone who does not hold the shared secret. */
    static String signedWithWrongSecret() {
        Instant now = Instant.now();
        return signed(JWSAlgorithm.HS512, WRONG_SECRET, now, now.plusSeconds(3600));
    }

    /** Right secret, but a different HMAC algorithm than the one the gateway is pinned to. */
    static String signedWithHs256() {
        Instant now = Instant.now();
        return signed(JWSAlgorithm.HS256, SECRET, now, now.plusSeconds(3600));
    }

    static String signed(JWSAlgorithm algorithm, String secret, Instant issuedAt, Instant expiresAt) {
        return signed(algorithm, secret, issuedAt, expiresAt, List.of("USER"));
    }

    static String signed(JWSAlgorithm algorithm, String secret, Instant issuedAt, Instant expiresAt,
                          List<String> roles) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("42")
                .claim("email", "alice@example.com")
                .claim("roles", roles)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        try {
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
        return jwt.serialize();
    }
}
