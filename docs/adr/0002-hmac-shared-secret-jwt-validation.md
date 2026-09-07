# ADR-0002: JWTs are signed and validated with a shared HMAC secret (HS512), not RS256/JWKS

## Status

Accepted

## Context

The auth service issues JWTs (`sub`, `email`, `roles`, 1h expiry). Two kinds of component must
validate them:

- the **gateway**, which rejects unauthenticated traffic at the edge before it reaches any service;
- **every downstream service**, which per root `CLAUDE.md` validates the token itself and never
  calls auth for authorization ("defense in depth" — the gateway is not their authority).

That means the verification key has to reach the gateway and, from Phase 5 onward, event, booking
and payment as well. There are two standard ways to do this:

1. **Symmetric (HMAC / HS*)** — one secret, used both to sign and to verify. Every validator holds
   the same string; distribution is a single config value / env var.
2. **Asymmetric (RSA or EC / RS256, ES256) + JWKS** — auth holds a private signing key and publishes
   the matching public key at a JWKS endpoint (e.g. `/.well-known/jwks.json`); validators fetch and
   cache it, and can verify without being able to sign. This also requires key-id (`kid`) handling,
   a rotation story, and a network dependency (or a bundled public key) in every validator.

Today there is exactly one issuer (auth), one trust domain (this monorepo, one Docker Compose
network), and no third party consuming our tokens.

## Decision

JWTs are signed by auth and verified by the gateway (and later by each service) with a **shared
symmetric HMAC secret**. The secret is supplied to both sides through the same environment
variable, `TICKETING_JWT_SECRET`, with an identical demo-only default committed in each
`application.yml`:

- auth: `auth.jwt.secret` → `io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes())`
- gateway: `security.jwt.secret` → `NimbusReactiveJwtDecoder.withSecretKey(...)`

The concrete algorithm is **HS512**, and the gateway is pinned to that single algorithm rather than
accepting "any HS\*". This is not an arbitrary choice: jjwt's `signWith(key)` selects the strongest
HMAC algorithm the key length supports, and the 64-character demo secret is used as 64 raw bytes
(512 bits), so auth emits `{"alg":"HS512"}`. Pinning one algorithm keeps a token signed with a
weaker algorithm — over the same secret — from being accepted.

No JWKS endpoint is exposed and no key pair is generated.

## Consequences

- **Smallest possible change:** validation is a decoder bean plus one config property per component.
  No key generation, no `kid`, no JWKS fetch/cache, no extra endpoint on auth, and no startup
  ordering dependency of "gateway must reach auth before it can validate anything".
- **The signing capability is spread across every validator.** With HMAC, "can verify" and
  "can sign" are the same power. If the gateway (or, later, the booking service) is compromised, the
  attacker can mint tokens for any user and any role — an RS256/JWKS setup would have leaked only a
  public key. This is an accepted risk for an interview-showcase demo running inside one Compose
  network with one issuer; it would not be acceptable in production or with any third-party
  validator.
- **Rotation is a coordinated restart.** Changing `TICKETING_JWT_SECRET` invalidates every live
  token and must be applied to all components at once; there is no `kid`-based overlap window.
- **Algorithm coupling.** Because auth's algorithm is derived implicitly from the secret's byte
  length, changing the secret's length silently changes the algorithm and would break the gateway's
  pinned `security.jwt.mac-algorithm`. The pin makes that failure loud (401s) instead of silent, and
  both values are documented in `gateway/CLAUDE.md`.
- **Migrating to RS256/JWKS later is contained** and does not change any service's business code:
  auth generates/loads an RSA key pair, signs with the private key, and exposes
  `GET /.well-known/jwks.json`; the gateway swaps
  `NimbusReactiveJwtDecoder.withSecretKey(...)` for `NimbusReactiveJwtDecoder.withJwkSetUri(...)`
  and holds no secret at all; each service does the same; `TICKETING_JWT_SECRET` disappears in
  favour of a key-store location on auth only. The trigger for doing this is any of: a second token
  issuer, a validator outside this trust boundary, or a real deployment.
