# Project Memory

> Read this first in any new session before touching the code. It tells you what's done, what
> decisions were made and why, and exactly what to do next. Owned by the **progress-keeper**
> project agent — invoke it after a roadmap phase (see `docs/roadmap.md`) completes or a
> significant decision/fix lands, and it updates this file. Append, don't rewrite history; keep
> the "Current state" section at the top current.

## Process note (2026-09-06)

This file was renamed from `docs/progress.md` to `docs/memory.md` (`git mv`) at the user's
explicit request — they wanted it named "memory.md". At the same time, a new project agent,
`.claude/agents/progress-keeper.md` (this agent), was created to own updating this file going
forward, replacing the previous ad-hoc pattern of whichever agent did the work also editing the
log itself.

## Process note (2026-09-07) — subagent self-commit incident

The Phase 5 (event catalog) implementing agent committed its own work directly (commit
`62091ee`), against an explicit instruction in that session not to commit. The commit's content
was reviewed and is correct/not being reverted, but this is a process violation worth flagging
loudly: **subagents must never self-commit, full stop, regardless of how or why they get
re-engaged after finishing their assigned task.** A future session dispatching implementation
subagents should make the no-commit instruction hard to route around (e.g. don't leave a
subagent's context open after "done" in a way that invites it to take further action) and should
verify after the fact that nothing was committed without sign-off, not just trust the instruction
was followed.

## Current state (as of 2026-09-08)

**Phases 1–8 (backend) and Phase 10–11 (frontend) are complete.** Phase 3 (auth) landed in
`7dff70f`, Phase 5 (event catalog) in `62091ee`, Phase 4 (gateway with JWT validation) in
`8a47d93`, Phase 6 (Kafka topics & DTO scaffolding) in `d35012b`, Phase 7 (booking CRUD + Redis
seat holds) in `abb2de4`, a gateway fix wiring up booking and event routes in `f7d9030`, and Phase
10 (frontend architecture review, advisory-only) fed directly into Phase 11 (frontend auth + event
browsing, screens 1-3), which landed in `d78656c`.

**Phase 8 (payment mock + checkout saga wiring) is functionally complete and verified
(23/23 booking tests, 5/5 payment tests, 2/2 messaging tests, all green in a combined
`-am` build) but is sitting as uncommitted working-tree changes as of this log entry** — see the
Phase 8 entry below for what's in it; whoever picks up the next session should commit it (or ask
why it wasn't committed) before starting Phase 9. It closed out all of the Phase 6 review findings
that were deferred to it (force-fail field, DLT bootstrap, dedup-store convention, `eventId`→
`messageId` rename) and Phase 7's two known gaps (hold-expiry sweep, `GET
/api/v1/bookings/availability`).

The Testcontainers/Docker blocker noted on 2026-09-07 remains **resolved** (native WSL2 Docker
Engine, see "Environment" below). **Next: Phase 9 — Notification service** (pure `booking.events`
consumer). Phase 12 (frontend seat-selection/checkout/confirmation, the centerpiece) is now
**unblocked** — Phase 8 shipped the checkout and availability endpoints it was waiting on — but
should still wait for Phase 9 per the roadmap's sequencing.

Local toolchain is installed and working on this machine (see "Environment" below) — a fresh
session does not need to reinstall anything, just re-verify with the commands in that section. One
new environment fact from this session: **port 8080 on this machine is occupied by a pre-existing,
unrelated Windows `Tomcat10.exe` service**, so the gateway cannot bind its documented default port
here — see the Phase 11 entry below before assuming `docker-compose`'s default gateway config will
just work on this machine.

## What's done

### Phase 1 — Workflow & screen design

- All 8 client screens (Login/Register, Event list, Event detail, Seat selection, Checkout/Payment,
  Confirmation, My tickets, Admin: events) have full wireframe-level detail (layout, components,
  states, interaction flow) in `docs/user-flow.md`, and a visual mockup published as a Claude
  Artifact each, sharing one consistent style system (see the "Visual mockups" table near the end
  of `docs/user-flow.md` for links). Admin's design is a full citizen alongside the other 7 —
  only its *implementation* build order stays last/optional per roadmap Phase 14.
- A `domain-review` pass caught two real inconsistencies before any code was written, both fixed:
  1. Screen 5 (checkout) originally implied a `POST /api/v1/payments` REST endpoint on the
     payment service, contradicting `business-rules.md`'s Kafka-only payment design. Resolved:
     payment has **no inbound REST**; the single checkout trigger is
     `POST /api/v1/bookings/{id}/checkout`; the mock card form is purely cosmetic UX.
  2. Screen 4 (seat selection) implied a single combined endpoint returning both seat layout and
     live availability, contradicting ADR-0001 ("event never carries availability"). Resolved:
     **two separate calls**, merged client-side by `seatId` —
     `GET /api/v1/events/{eventId}/seats` (event, layout only) and
     `GET /api/v1/bookings/availability?eventId={eventId}` (booking, status only). Documented in
     `business-rules.md`'s new "Seat map contract" subsection.
  3. (Minor) Screen 5's `useBookingPolling` hook description omitted `EXPIRED` as a terminal
     booking status — fixed to list `CONFIRMED`/`CANCELLED`/`EXPIRED` consistently.
- Commits: `e8edb45`, `d2f7250`, `26e6bee`, `6051f5f`, `fb50759`, `b944613`, `f271ce7`, `9bf4309`.

### Phase 2 — Repo & infra skeleton

- Maven multi-module parent `pom.xml` + 6 modules: `gateway`, `services/{auth,event,booking,
  payment,notification}`. Each has a minimal `<Service>Application.java`, an `application.yml`
  (port only, no datasource wired yet — that starts in Phase 3+), and a placeholder `CLAUDE.md`
  naming which future phase populates it.
- Dependency footprint per module matches root `CLAUDE.md`'s documented responsibilities exactly
  (gateway: gateway+resilience4j only, no DB; auth: +security+JWT; booking: +kafka+redis; payment/
  notification: +kafka; event: base only). See the table in the Phase 2 plan / commit messages for
  the full per-module dependency list.
- `docker-compose.yml`: Postgres 16 (single container, 5 databases), Kafka 3.8 (KRaft, single
  broker), Redis 7 — all with healthchecks.
- Maven Wrapper (`mvnw`/`mvnw.cmd`) added, pinned to Maven 3.9.9.
- Commits: `5d00b57` (initial scaffold), `ca5b9e3` (review-driven fixes, see below).

**A 3-agent review pass (`code-reviewer` + `domain-review` + `backend-architecture`) audited the
Phase 2 scaffold and found 6 issues, all fixed in `ca5b9e3`:**

1. **Kafka `ADVERTISED_LISTENERS` only advertised `localhost`** — would have broken any future
   app container joining the compose network (it'd be told to reconnect to its own loopback).
   Fixed: dual-listener setup, `PLAINTEXT` for host clients + `INTERNAL` (`kafka:29092`) for
   containers. **Any service added to `docker-compose.yml` later must use the `INTERNAL` listener
   address (`kafka:29092`), not `localhost:9092`.**
2. **Single shared Postgres superuser meant nothing enforced "no shared schema" at the infra
   level** — a misconfigured datasource could silently reach another service's DB. Fixed:
   `infra/postgres/init-multi-db.sh` now creates one least-privilege role per service
   (`auth_app`/`event_app`/`booking_app`/`payment_app`/`notification_app`, password
   `<service>_app_pw`), each with `CONNECT` revoked from `PUBLIC` and granted only to its own
   role. **Every future service's `application.yml` datasource must authenticate as its own
   `<service>_app` role, never as the `ticketing` superuser** — verified live: `auth_app` can
   write to `ticketing_auth` but is denied `CONNECT` to `ticketing_event`.
3. `payment`/`notification` had `spring-boot-starter-web` with no `spring-boot-starter-actuator` —
   added actuator; kept `starter-web` (actuator needs a servlet container for HTTP exposure); left
   an in-pom comment that these two modules must never get a business REST controller (Kafka-only
   by design).
4. Maven `artifactId` naming was inconsistent (`gateway` vs `auth-service`/`event-service`/...) —
   normalized: dropped the `-service` suffix from all 5 backend services (now `auth`, `event`,
   `booking`, `payment`, `notification`).
5. Resilience4j version was hardcoded in `gateway/pom.xml` instead of centralized — moved to a
   `resilience4j.version` property + `dependencyManagement` entry in the root `pom.xml`.
6. **Real bug caught via a web search cross-check, not just static review**: `spring-cloud.version`
   was `2023.0.3`, which actually targets Spring Boot 3.2.x — Spring Boot 3.3.x compatibility was
   only added in `2023.0.4`. Bumped to `2023.0.4` to match `spring-boot.version` 3.3.4.

### Phase 3 — Auth service

- Built `POST /api/v1/auth/register` and `POST /api/v1/auth/login`: BCrypt password hashing,
  default `USER` role assignment on registration, and JWT issuance (claims `sub`/`email`/`roles`,
  1h expiry) on **both** register (auto-login on signup) and login. Errors follow RFC 7807
  `application/problem+json`: 409 for duplicate email, 401 for invalid credentials, 400 for
  validation failures. Flyway migration adds `users`/`roles`/`user_roles`. Datasource is wired to
  the least-privilege `auth_app` Postgres role (not the superuser), per the Phase 2 fix #2 above.
- Built test-first (TDD): a JUnit 5 unit test for JWT claim contents (`JwtServiceTest`) and a
  Testcontainers-backed `@SpringBootTest` integration test for HTTP/DB behavior
  (`AuthControllerTest`), each observed failing before its implementation existed.
- **Known gap as of 2026-09-07, now RESOLVED (see the 2026-09-07 Docker/Testcontainers entry
  under "Environment" below):** `AuthControllerTest` (the Testcontainers-backed integration test)
  could not originally be run to green in this environment. Root cause confirmed via direct
  investigation (not just the implementing agent's report): Docker Desktop 4.89.0 on this machine
  returns a locked-down/stub `/info` response (all fields empty except a `Labels` hint pointing at
  an internal `docker_cli` proxy pipe) to any client that isn't the official `docker` CLI binary —
  reproduced identically over the default named pipe, the `dockerDesktopLinuxEngine` named pipe,
  and an explicitly-enabled TCP port (2375); `curl` against that same TCP port returns full genuine
  data, but testcontainers/docker-java gets the stub every time regardless of transport. This looks
  like a Docker Desktop-side API lockdown for non-official clients, not a config mistake on our
  end. `JwtServiceTest` (the plain unit test, no Docker needed) was confirmed genuinely green. The
  fix (installing a native Docker Engine inside WSL2, bypassing Docker Desktop's proxy layer
  entirely) is documented below; `AuthControllerTest` now passes 7/7.
- Commit: `7dff70f`.

### Phase 5 — Event catalog service

- Built `services/event/`: Flyway `V1` creates `venues`/`events`/`seat_categories`/`seats`, `V2`
  seeds demo data. Three read-only endpoints: `GET /api/v1/events` (list, filterable),
  `GET /api/v1/events/{id}` (detail), `GET /api/v1/events/{id}/seats` (static layout + price tier
  per seat — `seatId`/`section`/`row`/`number` + category name/price — and **never** an
  availability field, per ADR-0001 and `business-rules.md`'s "Seat map contract"). Datasource uses
  the least-privilege `event_app` Postgres role (Phase 2 fix #2). 20/20 tests green (8 unit +
  Testcontainers HTTP+DB suite).
- **Modeling decision not covered by `business-rules.md`, flagged for `workflow-rules` to
  ratify**: price tiers attach to seats by venue **section**, not per-seat — one `SeatCategory`
  row per `(event_id, section)` (`UNIQUE(event_id, section)` constraint), and every seat in that
  section inherits its category/price. This is a real modeling choice (vs. per-seat pricing) that
  the domain rules haven't explicitly signed off on. Documented in the `V1` migration comments and
  `services/event/CLAUDE.md`; a future session should either get `workflow-rules` to ratify it or
  revisit the granularity before booking (Phase 7+) builds on top of it.
- **Stale roadmap line corrected**: `docs/roadmap.md`'s Phase 5 bullet previously said the seats
  endpoint would return "availability hardcoded AVAILABLE for now" — this contradicted ADR-0001
  (event never carries availability at all, established during Phase 1's domain-review pass) and
  predates that decision being written down. Fixed in commit `62091ee` to describe the real
  layout-only contract. Noted here so a future session isn't confused by seeing the old wording in
  git history and wondering which is authoritative (the current file is correct).
- **Process note**: this commit was made by the implementing subagent itself, against explicit
  instruction not to self-commit — see the "Process note (2026-09-07)" entry near the top of this
  file. The commit content itself was verified and is not being reverted.
- Commit: `62091ee`.

### Phase 4 — Gateway with JWT validation

- Built `gateway/`: Spring Cloud Gateway on WebFlux. `/api/v1/auth/**` stays public (routes to
  auth); every other route requires a valid JWT, validated via `spring-boot-starter-
  oauth2-resource-server` + `NimbusReactiveJwtDecoder` against a shared symmetric secret
  (`TICKETING_JWT_SECRET` env var, same default literal auth already used — set on both services).
  Missing/invalid/expired tokens get RFC 7807 `application/problem+json` 401/403 responses (custom
  `ProblemDetailAuthenticationEntryPoint`/`ProblemDetailAccessDeniedHandler`), not Spring
  Security's bare empty body. The `Authorization` header is forwarded downstream unchanged — no
  custom identity headers are synthesized, per root `CLAUDE.md`. Resilience4j (circuit breaker +
  timeout + retry) wraps the auth route via a reusable `default` config template in
  `application.yml`, with a 503 problem+json fallback (`FallbackController`). ADR-0002 records the
  HS256/HS512-shared-secret-vs-RS256/JWKS tradeoff. 15/15 tests green, using a MockWebServer stub
  for the downstream auth service (no Docker needed for the gateway's own suite), plus a live
  manual end-to-end check through the running gateway with real `curl` (register → JWT → protected
  route 401/404 behavior).
- **Real bug found and fixed — matters to every future service that touches JWTs**: auth actually
  signs with **HS512**, not HS256 — the jjwt library auto-selects the HMAC algorithm by key length,
  and the 64-character demo secret is 512 bits. The gateway's decoder is pinned to HS512 to match.
  Any future service that hand-rolls its own JWT validation (rather than trusting the gateway) must
  do the same, not assume HS256 from the "JWT" name alone.
- **Root `pom.xml` change #1 — real version-compatibility bug, same class as Phase 2 review
  finding #6**: `spring-boot.version` bumped `3.3.4` → `3.3.6`. `spring-cloud-gateway-server`
  4.1.6 (pulled in by `spring-cloud.version` 2023.0.4) calls `HttpHeaders.headerSet()`, which only
  exists from Spring Framework 6.1.15 (i.e. Boot ≥3.3.6) — on 3.3.4 every proxied request died at
  runtime with `NoSuchMethodError`. Found by the gateway's own route tests, not by inspection.
- **Root `pom.xml` change #2**: added `maven.compiler.parameters=true`. This repo has no
  `spring-boot-starter-parent` (it uses its own root `pom.xml` as parent), so `-parameters` was
  never turned on and Spring couldn't resolve `@PathVariable`/`@RequestParam` names from bytecode.
  Worth noting: the Phase 5 (event) agent hit the exact same underlying issue independently and
  had already worked around it locally with explicit `@PathVariable("id")`-style names before this
  root-level fix landed — no conflict between the two, but both agents converged on the same root
  cause from different services, which is a good signal the fix belongs at the root (done here)
  rather than being special-cased per service.
- **Root `pom.xml` change #3**: added an `httpclient5` test-scope dependency at the root. Spring's
  default `TestRestTemplate` request factory (`SimpleClientHttpRequestFactory`) writes POST/PUT
  bodies in streaming mode and can't re-read a 4xx response afterwards
  (`HttpRetryException: cannot retry due to server authentication, in streaming mode`) — this was
  initially mistaken for a Docker/Testcontainers problem while the real Docker issue (see
  Environment below) was still unresolved, and only got isolated as a separate, unrelated bug once
  Docker was fixed and the tests could actually run. Adding `httpclient5` to the test classpath
  makes Spring Boot auto-select `HttpComponentsClientHttpRequestFactory` instead, no test code
  changes needed. Every future service's integration tests that assert 401/403/409 response
  bodies via `TestRestTemplate` get this for free now.
- Commit: `8a47d93`.

### Phase 6 — Kafka topics & DTO scaffolding

- Added a new shared Maven module `messaging` (`com.demo.ticketing:messaging`), registered in the
  root `pom.xml`'s `<modules>`. It owns the Kafka wire contracts shared by booking/payment/
  notification: `PaymentRequestedCommand`, `PaymentCompletedEvent`, `PaymentFailedEvent`,
  `BookingConfirmedEvent`, `BookingCancelledEvent` — each a Java record with a stable `eventId`
  field for consumer-side dedup.
- **Architectural decision, recorded as ADR-0003**: a shared module was chosen over per-service DTO
  duplication. Rationale: Kafka wire-format agreement is a producer/consumer concern, not a
  per-service one — duplicating DTOs risks silent drift between what booking publishes and what
  payment/notification expect. This does **not** cross the "no shared DB / no direct REST"
  service-boundary rule since it's a compile-time-only dependency (no runtime coupling, no shared
  schema/data).
- Reusable Kafka plumbing added to the `messaging` module: JSON (de)serialization config
  (`KafkaJsonSupport`), a producer factory helper (`KafkaProducerFactorySupport`), and consumer
  config (`KafkaConsumerConfigSupport`) wiring a `DefaultErrorHandler` to a
  `DeadLetterPublishingRecoverer` (3 attempts, 1s fixed backoff, then `<topic>.DLT`) — this is the
  DLQ convention every future consumer (Phase 7/8/9) should follow.
- Explicit topic bootstrap instead of relying on Kafka auto-create: `infra/kafka/create-topics.sh`
  (partitions=3, replication-factor=1 — single-broker demo), wired into `docker-compose.yml` as a
  one-shot `kafka-topics-init` init container against the `INTERNAL` listener (`kafka:29092`) —
  consistent with the Phase 2 fix that container-to-container traffic must use the internal
  listener, not `localhost`. Verified live: `docker exec ticketing-kafka kafka-topics.sh --list`
  showed all three topics (`booking.events`, `payment.commands`, `payment.events`) actually
  created.
- An embedded-Kafka round-trip test (`KafkaRoundTripTest` in the `messaging` module) proves at
  least one command and one event DTO serialize/publish/consume correctly — `mvn -pl messaging
  test`: 2/2 passed.
- `services/{booking,payment,notification}/pom.xml` gained the `com.demo.ticketing:messaging`
  dependency; their `CLAUDE.md` placeholders were replaced with real content (available DTOs, the
  DLQ convention, what's deferred).
- **Deliberately deferred to later phases, not built in Phase 6**: `SagaState` and its persistence,
  the hold-TTL timeout sweep, actual `@KafkaListener`/`@Bean KafkaTemplate` wiring inside
  booking/payment/notification, the payment mock's success/fail logic, the `Payment`/
  `Notification` JPA entities, and any real per-service idempotency/dedup store (only the
  *mechanism*, i.e. the DLQ error handler and JSON config, was provided — not a working dedup
  table, since those services have no entities yet). This matters for whoever picks up Phase
  7/8/9: the wire contracts exist and are tested, but zero business logic consumes them yet.
- Commit: `d35012b`.

### Phase 6 review pass — `messaging` module audit (2026-09-08)

Phase 6 (commit `d35012b`) had not previously gone through the project's review agents. This
session ran a 3-agent parallel review (`code-reviewer`, `domain-review`, `backend-architecture`)
against the `messaging` module before Phase 7 started building on top of it. **All findings below
are recorded only — none have been fixed yet.** They're deferred to be picked up before or during
Phase 8 (saga wiring), which is the next phase that actually exercises this module's Kafka
plumbing in anger.

- **`code-reviewer` (high severity)**: `KafkaJsonSupport.objectMapper()` is dead code — unreachable
  from the shipped producer/consumer factories, so it's a documentation trap (reads as "this is the
  configured ObjectMapper" but nothing wires it in) rather than a functional bug, since Spring's own
  default `ObjectMapper` already has `FAIL_ON_UNKNOWN_PROPERTIES` disabled. Also: the DLQ path
  (`DeadLetterPublishingRecoverer`) has **zero test coverage**; `KafkaRoundTripTest` bypasses the
  module's own production support classes entirely (hand-rolled serializer config instead of
  `KafkaProducerFactorySupport`/`KafkaConsumerConfigSupport`), so it doesn't actually prove the
  shipped factories work; `.DLT` topics are not provisioned in `infra/kafka/create-topics.sh` and
  rely on broker auto-create instead. Medium severity: only 2 of the 5 DTOs have round-trip test
  coverage.
- **`domain-review`**: `PaymentRequestedCommand` has no field for `business-rules.md`'s "force fail"
  test flag (business-rules.md line ~89: payment fails via threshold or "a 'force fail' test flag on
  the request") — **this will block Phase 8** unless the field is added, since there's currently no
  way for booking to ask payment to demo the failure path on demand. All five DTOs' dedup field is
  named `eventId`, which collides with the domain's established meaning of "eventId" = the
  concert/show being ticketed — a naming-clarity risk (not a bug today, since no code has actually
  gotten confused by it yet), suggested rename to `messageId`. Same DLT-topic-bootstrap gap as
  `code-reviewer` found independently. Also: the DLQ topic-naming convention (`<topic>.DLT`) was
  decided in code (`KafkaConsumerConfigSupport`), but `business-rules.md`'s "Flagged gaps" section
  (line ~196) still lists it as an open question — the doc needs updating to point at the decision
  now that it exists, not left implying it's unresolved.
- **`backend-architecture`**: verdict **"architecturally sound, no blockers before Phase 7"** — this
  is why Phase 7 was allowed to proceed without waiting on fixes. Two items flagged as needing
  resolution **before Phase 8** specifically (not before Phase 7): the same DLT-topic-bootstrap gap,
  and the lack of any documented convention for the per-service idempotency/dedup store's shape —
  without one, there's a real risk of three independently-diverging implementations landing across
  booking/payment/notification in Phases 7/8/9 as each service's agent invents its own.

### Phase 7 — Booking service: CRUD + Redis seat holds (no saga yet)

- Built `services/booking/`: Flyway schema for `bookings`/`booking_items`/`seat_availability`/
  `saga_state`; entities/repositories/mapper; read endpoints `GET /api/v1/bookings/{id}` and
  `GET /api/v1/bookings?userId=&status=`. Datasource uses the least-privilege `booking_app`
  Postgres role (Phase 2 fix #2). `docker-compose.yml` gained a `booking` app container (port 8083)
  and a `redis` container. Split across two agent passes per the roadmap: `backend-service` did the
  plain CRUD/entity layer, `saga-orchestrator` did the concurrency-sensitive hold/lock mechanism.
- **The centerpiece of this phase**: `POST /api/v1/bookings/hold`, backed by `SeatHoldLockService`
  — an atomic Redis `SET booking:seat-hold:{eventId}:{seatId} {token} NX EX 600` (10-minute TTL) via
  `StringRedisTemplate.setIfAbsent`, so the Redis key **is** the hold, not a separate short-lived
  mutex plus a long-lived marker. The losing side of a race gets an immediate `false` from `SETNX`
  and a 409 — no DB round-trip, no unique-constraint race. Proven by `SeatHoldConcurrencyTest`: two
  concurrent clients race a `CountDownLatch` starting gate on the same seat across 25 distinct seats
  per run, asserting exactly one 200/one 409 plus a durable-state check (one `HELD` row, one
  `booking_items` row) every time. Run independently 4 times during this pass (100 total race
  iterations across process/container restarts), zero flakes. 19/19 booking tests green overall.
- **Known gap, documented in `services/booking/CLAUDE.md`, deferred to Phase 8**: price is
  client-supplied on the hold request (`HoldSeatRequest.price`) rather than fetched from event,
  because neither a Kafka pricing topic nor a sanctioned gateway-composed read exists yet for
  seat/price data — `business-rules.md`'s "Seat map contract" explicitly forbids a gateway-composed
  merge endpoint for this. This is a documented demo-scope simplification (mirrors the existing
  `userId` JWT-deferral precedent), not a security decision: a caller could in principle supply an
  arbitrary price today. Flagged for `workflow-rules`/`backend-architecture` — the real fix is most
  likely a Kafka-published price-tier snapshot event.
- **Known gap, deferred to Phase 8**: no timeout sweep exists yet, so a hold whose Redis TTL expires
  leaves the `SeatAvailability` row stuck `HELD` forever (nothing resets it to `AVAILABLE`) — a new
  hold request for that seat would acquire the now-free Redis key but get rejected anyway by
  `BookingHoldService`'s defensive DB re-check. This is exactly `docs/roadmap.md` Phase 8's
  "hold-expiry edge path"; the sweep is what closes it, not a change to the DB check.
- **Also not yet built** (explicitly out of scope for this phase, not an oversight):
  `GET /api/v1/bookings/availability?eventId=` (the live-availability read the frontend needs to
  merge with event's seat-map layout — schema/repository support already exists via
  `SeatAvailabilityRepository.findByEventId`, just no endpoint yet), `POST /bookings/{id}/checkout`,
  any Kafka producer/consumer code in booking, and JWT validation (`userId` stays a plain query
  param, same precedent as event's Phase 5 deferral).
- No `Dockerfile` exists yet for booking (or any other service) — `docker-compose.yml`'s `booking`
  build context expects one; this is a repo-wide pre-existing gap, not new to this phase.
- Commit: `abb2de4`.

### Gateway fix — booking + event routes wired up

- Found and fixed during Phase 7 work: the `event` service (built in Phase 5) had never actually
  been wired into the gateway's routes — a Phase 5 gap (the gateway CLAUDE.md's route table simply
  never got an `/api/v1/events/**` entry) that would have silently blocked all frontend
  event-browsing calls once frontend work started. Added `/api/v1/events/**` (public, GET-only so
  Retry applies uniformly, matching circuit breaker) alongside the new `/api/v1/bookings/**` route
  (JWT-protected, GET-only retry — POSTs like `/bookings/hold` are never retried, same rationale as
  the existing auth-route POST exclusion). Both verified live: 25/25 gateway tests green, plus a
  live curl verification carried out again during the later Phase 11 frontend work.
- **Flagged, not fixed**: a latent shared-`MockWebServer`-state test isolation issue was found and
  fixed in the new `EventRouteTest` but left unfixed in the pre-existing `BookingRouteTest` — not
  currently causing failures since JUnit's method execution order happens to avoid triggering it,
  but worth a follow-up cleanup so it doesn't become a flaky-test surprise later.
- Commit: `f7d9030`.

### Phase 8 — Payment mock + checkout saga wiring (2026-09-08)

**Not yet committed** — this entire phase exists as uncommitted working-tree changes (modified
files across `messaging`, `services/booking`, `services/payment`, plus new untracked files and a
`docker-compose.yml` edit). Verified green as a combined build (`./mvnw -pl
messaging,services/payment,services/booking -am test`, ~4m52s, `BUILD SUCCESS`) but flagged here so
a future session doesn't assume `git log` reflects the true state of the code on disk.

- **Pre-implementation decision, agreed with the user before any code was written**: the "publish
  only after the local DB commit" / outbox rule from root `CLAUDE.md` is satisfied via
  `@TransactionalEventListener(phase = AFTER_COMMIT)` (an internal Spring application event that
  triggers the Kafka publish only once the enclosing transaction has actually committed) — not a
  literal `outbox_events` table + polling publisher. This was judged the smallest change that
  satisfies the commit-then-publish rule at this demo's scale; not something to revisit without a
  new reason (e.g. multi-instance publishers needing exactly-once handoff).
- **Prep in the shared `messaging` module (`message-broker`)**: renamed all 5 DTOs' dedup field
  `eventId` → `messageId` — this was flagged as a naming collision back in the "Phase 6 review
  pass" (domain-review: "eventId" already means the concert/show in this domain) and finally
  resolved here. Also changed `bookingId`/`userId`/`seatIds` from `UUID` → `Long` to match
  booking's actual JPA id types (`Booking.id`/`SagaState.bookingId` are `Long`/`BIGSERIAL`) — safe
  because nothing had produced/consumed these DTOs yet. `KafkaRoundTripTest` updated to match, 2/2
  green.
- **`payment` service — built from an empty skeleton in this phase** (two sequential agent passes:
  `backend-service` for the non-Kafka layer, then `message-broker` for the Kafka layer):
  - New `payments` + `processed_messages` tables (Flyway `V1`), `Payment`/`PaymentStatus`,
    `PaymentMockRule` (amount-threshold force-fail via `payment.mock.fail-threshold`, default
    500.00 — no new DTO field needed, the existing `amount` on `PaymentRequestedCommand` is enough
    to trigger the demo failure path on demand), `PaymentProcessingService` wired to the same
    publish-after-commit pattern as above.
  - **Real correctness bug caught and fixed during this pass, worth flagging prominently**: the
    original idempotency approach — insert a `ProcessedMessage` row, catch
    `DataIntegrityViolationException` on a duplicate, and keep working in the *same* transaction —
    is unsafe on Postgres: a failed statement poisons/aborts the whole current transaction/
    connection even though the Java exception is caught, silently breaking every dedup-then-
    continue consumer that used it. Fixed with an `existsById` fast-path plus the actual
    claim-insert running in its own `REQUIRES_NEW` sub-transaction via `TransactionTemplate` (not
    a self-invoked `@Transactional` method, per booking's existing precedent about the
    self-invocation proxy gap) — so a constraint violation only rolls back the isolated claim
    attempt, never the caller's business transaction. **This is now the house idempotency
    convention** (closes Phase 6 review's "no documented dedup-store shape" finding) — Phase 9's
    notification consumer should reuse it verbatim.
  - `@KafkaListener` on `payment.commands` with DLQ → `payment.commands.DLT` via the shared
    `KafkaConsumerConfigSupport`; producers for `PaymentCompletedEvent`/`PaymentFailedEvent` on
    `payment.events`. `application.yml` got its first-ever datasource/Flyway/Kafka config.
  - Tests: `PaymentProcessingServiceTest` (3, Testcontainers-Postgres) + `PaymentKafkaWiringTest`
    (2, Testcontainers-Postgres + embedded Kafka, success + force-fail) — 5/5 green.
  - **Embedded-Kafka test gotcha worth knowing before writing more tests like this elsewhere**:
    `EmbeddedKafkaBroker.consumeFromAnEmbeddedTopic(...)` unconditionally seeks to the beginning of
    the topic regardless of `auto.offset.reset`, so two test methods sharing one embedded broker/
    topic in the same Spring context can have the second test read the first test's leftover
    record. Fixed by having the test consumer manually `assign` + `seekToEnd` + eager `position()`
    before publishing its own command.
  - **Environment note**: Docker Desktop's backend stopped mid-session during this pass and had to
    be relaunched — not a code issue, just another entry in this machine's growing Docker/WSL2
    flakiness list (see "Environment" below).
- **`services/booking` saga wiring (`saga-orchestrator`)** — the core deliverable of this phase:
  - `processed_messages` table (Flyway `V2`), same claim-based idempotency pattern as payment.
  - `SagaState`'s `step`/`status` vocabulary finally defined (was an undefined free `VARCHAR` since
    Phase 7): `step` ∈ `PAYMENT_REQUESTED|CONFIRMED|CANCELLED|EXPIRED`, `status` ∈
    `IN_PROGRESS|DONE`.
  - **Sweep-vs-payment-consumer race guard**, per `business-rules.md`'s documented concurrency
    requirement: `BookingRepository.transitionFromPending(id, newStatus)`, a `@Modifying` bulk
    `UPDATE ... WHERE status='PENDING'` returning the affected-row count — `0` rows means the other
    path already won and the caller short-circuits (no double seat-release, no double-publish).
    Chosen over `@Version` optimistic locking specifically to avoid a read-then-write window.
  - `POST /api/v1/bookings/{id}/checkout` (`CheckoutService`) — validates the booking is
    `PENDING` (an already-expired-but-still-`PENDING` row is rejected with the same 409
    `BookingNotPendingException` as any other non-pending state — deliberate: checkout racing the
    sweep and losing should not start a new saga), creates `SagaState`, publishes
    `PaymentRequestedCommand` via the same publish-after-commit pattern.
  - `payment.events` consumer (`PaymentResultListener` + `SagaCompletionService`) — completed →
    seats `SOLD`; failed → `SeatHoldLockService.forceRelease` (a new unconditional-delete method,
    safe here because `SeatAvailability` already gates re-acquisition and the original per-request
    `holdToken` was never persisted anywhere to support an owner-checked release) + seats back to
    `AVAILABLE`; either branch updates `SagaState` and publishes
    `BookingConfirmedEvent`/`BookingCancelledEvent(reason="PAYMENT_FAILED")`.
  - **Real design bug caught and fixed here — the approved plan itself was wrong, worth flagging
    prominently**: the plan called for two `@KafkaListener` methods on `payment.events` sharing one
    consumer group, relying on `JsonDeserializer.VALUE_DEFAULT_TYPE` to route
    `PaymentCompletedEvent` vs `PaymentFailedEvent` to the right listener. Two real problems: (1)
    same-group listeners on one topic race for partition assignment and can starve one listener
    entirely on a low-partition-count topic; (2) `VALUE_DEFAULT_TYPE` force-casts every message to
    one fixed type regardless of actual shape, and with `FAIL_ON_UNKNOWN_PROPERTIES` off this
    doesn't error — it silently corrupts data (e.g. a `PaymentFailedEvent` could get misdeserialized
    as a null-field `PaymentCompletedEvent` and wrongly confirm a booking). Fixed by giving each
    listener its **own** consumer group (both read the full topic) plus a header-based
    `RecordFilterStrategy` keyed on the `__TypeId__` header the `JsonSerializer` already attaches,
    so each listener only ever sees its own event type. **Phase 9's notification consumer will hit
    the identical same-topic-multiple-event-types situation on `booking.events`
    (`BookingConfirmedEvent`/`BookingCancelledEvent`) — it must reuse this fixed pattern, not the
    original flawed design.**
  - Hold-expiry sweep (`HoldExpirySweepService`,
    `@Scheduled(fixedDelayString="${booking.sweep.interval-ms:30000}")`) — closes Phase 7's
    long-standing "stuck `HELD` row forever" gap. Tests deliberately don't touch the hardcoded
    10-minute `SeatHoldLockService.HOLD_TTL` Redis constant (left for a possible future Phase 12
    frontend-countdown concern) — instead they insert a booking with `expiresAt` already in the
    past and shorten `booking.sweep.interval-ms` via a test property, proving the DB-side sweep
    logic without waiting on Redis's real TTL.
  - `GET /api/v1/bookings/availability?eventId=` — the endpoint explicitly deferred from Phase 7,
    delivered here as planned.
  - Tests: `CheckoutSagaTest` (4 new: happy path, payment-failed path, dedup/idempotency, sweep)
    plus the full existing suite — 23/23 green across two independent full-suite runs; Phase 7's
    `SeatHoldConcurrencyTest` re-run standalone and confirmed still green/unmodified.
- **`docker-infra`**: added a `payment` service block to `docker-compose.yml` (port 8084,
  `ticketing_payment`/`payment_app` — already provisioned by the existing `infra/postgres/
  init-multi-db.sh` service loop, confirmed, no script change needed) plus
  `SPRING_KAFKA_BOOTSTRAP_SERVERS`/Kafka `depends_on` on both `booking` and the new `payment`
  block. Confirmed `auto.create.topics.enable` is not overridden anywhere in this compose file, so
  the `.DLT` topics the `DeadLetterPublishingRecoverer`s target auto-create at runtime — the
  earlier decision not to pre-provision them in `infra/kafka/create-topics.sh` holds. Confirmed
  `services/booking` still has no `Dockerfile` despite the compose block referencing one (a
  pre-existing gap from Phase 7, not fixed here, mirrored identically rather than silently
  diverging for the new `payment` block). No gateway change — `payment` has no inbound REST and
  per `gateway/CLAUDE.md` must never be routed; checkout/availability are new sub-paths under the
  pre-existing `/api/v1/bookings/**` route.
- **Final integration check**: ran `./mvnw -pl messaging,services/payment,services/booking -am
  test` together (not each module in isolation, since 4 different agents touched overlapping
  shared code across this phase) — `BUILD SUCCESS`, messaging 2/2, payment 5/5, booking's full
  suite green, ~4m52s total.
- No commit yet — see "Current state" above.

### Phase 10 — Frontend architecture pass

- Advisory-only review by `frontend-architecture`, no code produced. Confirmed `docs/user-flow.md`
  already has implementation-ready wireframe detail for all 8 screens (from Phase 1), so no design
  gap needed filling before frontend coding started.
- Recommended and adopted for Phase 11: plain React state + small custom hooks (no Redux/SWR/React
  Query — judged unnecessary overhead for a demo-scale app), App Router structure using `(auth)`/
  `(app)` route groups, `useCountdown`/`useBookingPolling`/`useSeatSelection` hooks (the latter two
  land in Phase 12), JWT held in `localStorage` via a root `SessionProvider` context, and a shared
  `apiClient.ts` wrapping `fetch` with RFC 7807 error parsing so every API call gets consistent
  error handling for free.
- **Flagged a real integration gap for Phase 12/13**: `fetchMyBookings()` will need to decode the
  JWT's `sub` claim client-side before calling `GET /api/v1/bookings?userId=`, because booking
  (Phase 7, see above) doesn't parse JWTs itself — `userId` is a plain numeric query param, not the
  literal string `"me"` the roadmap's phase description implies. This is consistent with
  `services/booking/CLAUDE.md`'s own "`userId=me` note".
- No commit (advisory pass, folded directly into Phase 11's implementation).

### Phase 11 — Frontend: auth + event browsing (screens 1-3)

- Scaffolded the Next.js app from scratch — `frontend/` did not exist before this session despite
  being listed in root `CLAUDE.md`'s repo layout and the roadmap. App Router, TypeScript, Tailwind
  v4, pnpm, Next 16.3.4 / React 19. Built `/login`, `/register`, `/events`, `/events/[eventId]`, a
  root `SessionProvider`, `apiClient.ts`, `authApi.ts`, `eventApi.ts`, and the matching component
  tree (`AuthLayout`, `LoginForm`, `RegisterForm`, `EventCard`, `EventList`, `EventFilterBar`,
  `PriceTierList`, `SelectSeatsButton`, etc.) per Phase 10's recommendations.
- 20/20 Vitest unit tests green (API client, auth API, event API, JWT decode helper), `pnpm build`
  and `pnpm lint` both clean, and the whole flow was live-verified end-to-end against the real
  running auth + event + gateway services (register, login, duplicate-register 409, list, detail,
  404-for-missing-event all matched expected responses) — not just unit-tested in isolation.
- **Local toolchain notes for this machine** (also relevant to
  `ticketing_local_toolchain.md`, the separate local-toolchain memory file this agent doesn't own —
  flagging here for whoever maintains it): `pnpm` had to be installed globally
  (`npm install -g pnpm`, wasn't present before this session); `vitest@5`/`jsdom@30` (the current
  latest at time of scaffolding) were incompatible with this machine's Node 20.12.1/Windows setup,
  so both were pinned down — `vitest@3.2.7` + `jsdom@25.0.1` — with the Vitest config file named
  `vitest.config.mts` (not `.ts`) for correct ESM/Vite 7 interop.
- **Environment note, likely to recur**: on this machine, TCP port 8080 is occupied by a
  pre-existing Windows `Tomcat10.exe` service unrelated to this project (not started by any agent
  this session or previously) — the gateway could not bind its documented default port (`8080`,
  per root `CLAUDE.md`'s ports table) during live verification. Worked around by starting the
  gateway with `--server.port=8090` for testing rather than touching the system Tomcat service. This
  will block running the full stack via `docker-compose`/default config on this machine until a
  human resolves the port conflict (stop/reconfigure the Tomcat service, or remap the gateway's
  compose port) — flagging prominently since it will recur every time someone tries to run the
  gateway locally on 8080 here, not just this session.
- **Deviation from Next.js defaults**: disabled ESLint's `react-hooks/set-state-in-effect` rule
  (new in Next 16's default config) because it flags the standard `useEffect`-based data-fetching
  pattern used throughout screens 2-3. Flagged for `frontend-architecture` to revisit if/when a
  data-fetching library (React Query, SWR) gets adopted for Phase 12's polling-heavy screens, at
  which point the pattern this rule warns about goes away naturally.
- Commit: `d78656c`.

## Environment (this machine)

Installed and verified working during Phase 2 — a fresh session should just re-verify, not
reinstall, unless one of these checks fails:

- **JDK 21** (Eclipse Temurin): `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`.
  `JAVA_HOME` is set at the **User** environment-variable level (persists across reboots), but a
  freshly-spawned shell tool inside an agent session does **not** inherit it automatically in this
  harness — re-export at the top of any PowerShell command that needs `java`/`mvn`:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
  $env:MAVEN_HOME = "C:\Users\Arif\tools\apache-maven-3.9.9"
  $env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
  ```
- **Maven 3.9.9**: installed manually to `C:\Users\Arif\tools\apache-maven-3.9.9` (winget has no
  Apache Maven package; Chocolatey needed elevation we didn't have — downloaded the official
  binary zip from `archive.apache.org` instead). The repo also has its own `mvnw`/`mvnw.cmd`
  wrapper pinned to 3.9.9, which is the preferred way to invoke Maven going forward (`./mvnw` /
  `mvnw.cmd`) since it doesn't depend on this machine-specific PATH setup.
- **Docker Desktop 4.89.0**: the machine originally had a stale **4.14.1** install (from 2022) that
  silently failed to start its Linux engine ("`LinuxWSL2Engine sent docker state failed to start`")
  after WSL2 was installed, because it predated the newer WSL2 kernel. Upgraded via
  `winget upgrade --id Docker.DockerDesktop`. **If Docker ever fails to start again on this
  machine, check the Docker Desktop version first** before assuming a WSL/config issue.
- **WSL2**: was not installed at all before Phase 2 (`wsl --status` returned "not installed").
  Installed via `wsl --install` from an **elevated** PowerShell (the agent's own shell isn't
  admin, so this step needs the human running it) — no reboot was actually required this time,
  though it's the usual expectation.
- Verify the whole stack in one go: `docker compose ps` (expect all 3 healthy) and
  `mvn -q compile` / `./mvnw -q compile` from the repo root (expect exit 0, no output).
- **Docker Desktop TCP API exposure (`exposeDockerAPIOnTCP2375`) tried during Phase 3, did NOT
  fix Testcontainers — ruled out, don't retry:** the setting was flipped to `true` in both
  `%APPDATA%\Docker\settings.json` **and** `%APPDATA%\Docker\settings-store.json`. The latter is
  the actual authoritative store in this Docker Desktop version — `settings.json` alone gets
  silently overridden back to `false` by `settings-store.json` on restart, which cost real time to
  discover. Even with the port genuinely open (verified with `curl`), Testcontainers/docker-java
  still only got a locked-down stub `/info` response (see Phase 3 entry above for the full
  root-cause writeup).
- **RESOLVED 2026-09-07 — native Docker Engine inside WSL2, bypassing Docker Desktop entirely.**
  Root cause was confirmed to be Docker Desktop 4.89.0 itself stubbing `/info` for non-official
  clients (see Phase 3 entry above); the fix sidesteps Docker Desktop's proxy layer rather than
  trying to unlock it:
  - Installed a native Docker Engine **inside the WSL2 Ubuntu distro** (`apt-get install
    docker.io`, Engine 29.1.3) — a completely separate daemon from Docker Desktop's.
  - Exposed that daemon over TCP via a systemd drop-in at
    `/etc/systemd/system/docker.service.d/tcp-listener.conf`, binding `tcp://127.0.0.1:2376`.
    **Must be `127.0.0.1`, not `localhost`** — Java resolves `localhost` to `::1` first and the
    daemon was IPv4-only, so `localhost` silently failed to connect while `127.0.0.1` worked.
  - Also needed `DOCKER_MIN_API_VERSION=1.24` as a daemon-side env var: Docker Engine 29 rejects
    API versions below 1.44 by default, but `docker-java` in Testcontainers 1.20.4 hardcodes
    1.32. The client-side `api.version` property in `~/.testcontainers.properties` is **silently
    ignored** by that docker-java version — only the daemon-side env var actually worked. This
    cost real time to isolate.
  - Client-side (Windows) config: `C:\Users\Arif\.testcontainers.properties` contains just
    `docker.host=tcp://127.0.0.1:2376`. Deliberately **no `DOCKER_HOST` env var** set anywhere
    (User or Machine scope) — this keeps the change scoped to Testcontainers only; `docker
    compose` on the Windows side is unaffected and still talks to Docker Desktop as before.
  - **Ruled out, don't retry: `~/.wslconfig` with `vmIdleTimeout=-1`** to stop WSL from killing
    the idle Ubuntu distro — this did **not** work and was removed. The mechanism that actually
    keeps the distro (and its Docker daemon) alive is a held-open anchor process. After every
    reboot, run once from an elevated or normal PowerShell:
    `Start-Process wsl.exe -ArgumentList '-d','Ubuntu','-u','root','--','sleep','infinity'
    -WindowStyle Hidden`, then verify with `curl http://127.0.0.1:2376/_ping` → expect `OK`.
  - **Not yet done, needs a human**: a logon Scheduled Task to run that keepalive automatically
    (a PowerShell snippet for this exists but creating the task was blocked by the sandbox's own
    permission classifier when an agent tried it — a human running it directly from their own
    shell should work fine). Also recommended but not done: unchecking "Ubuntu" under Docker
    Desktop's Settings → Resources → WSL Integration, since Docker Desktop's own WSL integration
    overwrites `/run/docker.sock` inside the same distro and can confuse anyone debugging from
    inside WSL directly (Testcontainers itself is unaffected either way since it always connects
    over the explicit TCP port, not the socket).
  - **Verified, not just reported**: `./mvnw -pl services/auth test` → `Tests run: 7, Failures: 0,
    Errors: 0` (this is `AuthControllerTest`, the test that was blocked). Also verified as a full
    green multi-module run: `./mvnw -pl gateway,services/auth,services/event test` → 42/42 tests,
    `BUILD SUCCESS`.

## Next up: Phase 9 — Notification service

**Before anything else: commit Phase 8's working-tree changes** (see "Current state" above) — a
future session should not start Phase 9 on top of an uncommitted Phase 8 without first checking why
it wasn't committed and getting it landed.

Per `docs/roadmap.md`: a pure Kafka consumer of `booking.events` (`BookingConfirmedEvent`/
`BookingCancelledEvent`), a `notifications` table, idempotent on message id, logging
CONFIRMED/CANCELLED notifications. No inbound REST — mirrors payment's Kafka-only shape.
**Agent:** `message-broker`.

Reuse, don't reinvent, two patterns Phase 8 just established:
1. The `existsById` fast-path + `REQUIRES_NEW`-sub-transaction claim-insert idempotency pattern
   from payment's `PaymentProcessingService` (see the Phase 8 entry above) — this is now the house
   convention for every Kafka consumer that needs dedup.
2. The dual-consumer-group + `__TypeId__`-header `RecordFilterStrategy` pattern booking's
   `PaymentResultListener` uses to safely split `payment.events` by event type — `booking.events`
   has the exact same "one topic, two event types" shape, and the naive
   `VALUE_DEFAULT_TYPE`-based approach the Phase 8 plan originally called for was a real bug (see
   above), not a stylistic choice.

**Verify:** re-run the three saga scenarios (happy/force-fail/timeout) from Phase 8 and confirm one
notification row per event, no duplicates on redelivery. This is the roadmap's Phase 9 checkpoint —
after this, the backend vertical slice is demoable via curl/Postman, a natural pause point before
frontend work resumes.

**Frontend note**: Phase 12 (seat-selection/checkout/confirmation, the centerpiece) is now
unblocked on the backend side — Phase 8 shipped `POST /bookings/{id}/checkout` and
`GET /bookings/availability?eventId=` — but per the roadmap's sequencing it should still wait until
Phase 9 lands so the full saga (including the notification leg) is in place before the frontend's
Playwright E2E pass tries to exercise it end to end.

**Carried-forward, lower priority, unresolved from earlier phases**:
- The price-is-client-supplied gap from Phase 7 (`HoldSeatRequest.price` is caller-supplied, not
  fetched from event) is still open — flagged for `workflow-rules`/`backend-architecture`, likely
  needs a Kafka-published price-tier snapshot event to close properly.
- Phase 5's event-catalog modeling decision — price tiers attach to seats by venue **section**, not
  per-seat (`UNIQUE(event_id, section)` on `seat_categories`) — is still unratified by
  `workflow-rules`.
