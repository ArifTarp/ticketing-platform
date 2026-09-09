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

## Current state (as of 2026-09-09)

**All 14 phases in `docs/roadmap.md` (1 through 14) are complete and committed. The app has also
had a full visual redesign, its first real Playwright E2E suite, and — most recently — its first
full-app parallel-agent review + live-browser-test pass since the redesign.** Current HEAD is
`3ee4ca3` "fix: address findings from full-app review + live UI test pass" (application code:
booking backend fix + frontend fixes + one doc-drift fix; see the "Full-app review + live UI test
pass" entry under "What's done" for full detail). Immediately prior, `8957a08` was the docs commit
logging the Claude Code tooling audit/fix pass whose actual code landed in `691b115` (agents/
skills/hooks only, not application code; see the "Claude Code tooling audit/fix pass" entry under
"What's done"). The full vertical slice (register/login → browse → hold seats → pay
(mock) → confirmed booking + notification, with the saga and timeout path) plus the admin screens
(venue/event CRUD, ADMIN-gated) all ship under a new "control panel" dark/monospace-data-face
design system (commit `b0dea0d`), and the long-standing "Playwright: scaffold or defer?" open item
is now **resolved** — Playwright is set up with 8 real specs covering the golden path and the
admin flow (commit `44a88e8`). Running that suite for real also caught and fixed **two genuine,
pre-existing functional bugs** unrelated to the redesign: seat holds were being silently cleared
immediately after every successful hold (`useCountdown` stale-state bug), and the admin "create
venue inline while creating an event" path was completely broken (nested `<form>` HTML). See the
"UI redesign + first real Playwright E2E suite" entry below for full detail.

Remaining open items, none blocking: two small, documented Phase 14 scope limits (no venue-listing
GET endpoint, no event-edit PUT/PATCH endpoint), the gateway `BookingRouteTest` flakiness, the
outbox-durability ADR candidate, the E2E suite's missing `docker-compose` path (still requires
manually starting every backend service locally — see "Next up"), and — newly called out as its
own backlog line by the 2026-09-09 full-app review pass (not a new fact, just newly tracked
explicitly) — `services/event` and `services/booking` do zero local JWT/role validation,
relying entirely on the gateway's `hasRole("ADMIN")`; already documented as deliberate demo-scope
debt in each service's own `CLAUDE.md`, worth a real auth-hardening pass if/when prioritized.

Immediately prior to Phase 14: a post-Phase-8/9/12 review-and-fix pass, a gateway CORS fix, and a
saga/gateway review-and-fix pass (`2455d31`) followed by a targeted bug fix from that pass's own
review agents (`7d67ccc`), then logged in commit `85454f8`. See the "Saga idempotency,
pending-booking race, and CORS header-dup fix + review pass" entry below for details; the repo-root
`NOTLAR.txt` scratch file that tracked this in-progress work across sessions has been superseded by
this log entry and should be treated as stale/deletable going forward. Phase 3 (auth) landed in `7dff70f`,
Phase 5 (event catalog) in `62091ee`, Phase 4 (gateway with JWT validation) in `8a47d93`, Phase 6
(Kafka topics & DTO scaffolding) in `d35012b`, Phase 7 (booking CRUD + Redis seat holds) in
`abb2de4`, a gateway fix wiring up booking and event routes in `f7d9030`, Phase 8 (payment mock +
checkout saga wiring) in `d22d465`, Phase 9 (notification service) in `a0b1460`, Phase 10 (frontend
architecture review, advisory-only) fed directly into Phase 11 (frontend auth + event browsing,
screens 1-3) in `d78656c`, Phase 12 (frontend seat selection/checkout/confirmation, screens 4-6) in
`3e85aab`, a 4-agent review-and-fix pass across Phases 8/9/12 in `555c75f` (real saga-idempotency
and seat-hold bugs found and fixed — see entries below), **Phase 13 (frontend `/tickets` — my
bookings list + client-side QR) in `66a5ff3`**, and a **gateway CORS fix for the frontend origin in
`529d287`/`e244d72`** (see the Phase 13 and CORS entries below). Note: commit `55a8c00` ("docs: log Phase 13 my tickets completion") is misleadingly
named — it only logged the prior Phase 8/9/12 review-and-fix pass (commit `555c75f`), not Phase 13
itself (`66a5ff3`), which had no dedicated "What's done" entry until this log update. Phase 14
(admin screens) landed in `7f3a3e7`, logged in `ec6c3d1`. Most recently, a full UI redesign landed
in `b0dea0d` and the first real Playwright E2E setup (plus two bug fixes it uncovered) landed in
`44a88e8` — see the "UI redesign + first real Playwright E2E suite" entry below. Working tree is
clean as of this log entry (verified via `git status`) except for this file itself.

**RESOLVED this session (2026-09-09, commit `44a88e8`): Playwright is now set up and has real,
working specs** — this had been flagged as the single biggest remaining gap since Phase 12,
carried unresolved through Phase 13 and Phase 14. It is no longer an open item; see the "UI
redesign + first real Playwright E2E suite" entry below. Do not keep carrying this forward as open
in future "Current state"/"Next up" sections.

Local toolchain is installed and working on this machine (see "Environment" below) — a fresh
session does not need to reinstall anything, just re-verify with the commands in that section. One
environment fact from Phase 11 still applies: **port 8080 on this machine is occupied by a
pre-existing, unrelated Windows `Tomcat10.exe` service**, so the gateway cannot bind its documented
default port here — see the Phase 11 entry below before assuming `docker-compose`'s default gateway
config will just work on this machine.

**RESOLVED this session**: the suspicious `frontend/AGENTS.md` file (a planted prompt-injection
payload, first flagged during the Phase 8/9/12 review pass and left open since) has been deleted
with the user's explicit approval, and `frontend/CLAUDE.md` (which had auto-imported it via
`@AGENTS.md`) was rewritten with real frontend conventions content. This item is closed — see the
"Gateway CORS + AGENTS.md removal" entry below.

**Also this session (2026-09-09), later in the day, commit `691b115`: a Claude Code tooling
audit/fix pass — not application code.** All 14 roadmap phases remain the current application
status (unchanged by this entry); this pass only touched `.claude/agents/`, `.claude/skills/`,
`.claude/settings.json`, and `.githooks/`. Highlights: a previously-committed agent file,
`.claude/agents/saga-orchestrator.md`, was found silently missing from disk (tracked by git but
absent from the working tree with no deletion commit) and restored; a new `run-ticketing-platform`
skill now documents the actual working procedure for running the full stack locally, since
`docker-compose` still can't do it alone (see "Next up"); several agents were wired to existing
Superpowers skills they should have been using already; two new agents
(`brainstorm-analyst`, `performance-engineer`) were added; and commit-time lint/typecheck/compile
hooks were added both for Claude Code (`.claude/settings.json`) and for plain `git commit`
(`.githooks/pre-commit`). Full detail in the "Claude Code tooling audit/fix pass" entry below.

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

Committed as `d22d465`. Verified green as a combined build (`./mvnw -pl
messaging,services/payment,services/booking -am test`, ~4m52s, `BUILD SUCCESS`).

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
- Commit: `d22d465`.

### Phase 9 — Notification service (2026-09-08)

Committed as `a0b1460` (`services/notification` was an empty skeleton going into this phase;
`docker-compose.yml` also modified). Built by `message-broker`.

- Pure `booking.events` consumer, no inbound REST and no gateway route — same Kafka-only shape as
  `payment`, by design (mirrors the existing precedent, not a new decision).
- **Deliberately reused, not reinvented, two conventions Phase 8 established** (per the standing
  instruction left in the previous "Next up" section): the `existsById` fast-path +
  `REQUIRES_NEW`-sub-transaction claim-insert idempotency pattern, copied from payment's
  `PaymentProcessingService` (new `processed_messages` table here too); and the dual-consumer-group
  + `__TypeId__`-header `RecordFilterStrategy` pattern for splitting `BookingConfirmedEvent`/
  `BookingCancelledEvent` off one topic, copied from booking's `KafkaConfig`/
  `PaymentResultListener` — avoids the same same-topic-multiple-types bug Phase 8 caught and fixed
  (see that entry above) from being reintroduced here.
- New `V1__create_notifications_schema.sql` (`notifications` + `processed_messages` tables),
  `NotificationService`, `BookingEventListener`, `KafkaConfig`. Each event type writes one
  `notifications` row (mock — no real mail/SMS send, per root `CLAUDE.md`'s notification scope);
  cancelled events record the cancellation reason on the row.
- `docker-compose.yml` gained a `notification` app service block (port 8085), mirroring `payment`'s
  block shape (own Postgres role via the existing `init-multi-db.sh` loop, Kafka `depends_on` on
  the internal listener).
- Tests: `BookingEventListenerTest`, 3/3 green — confirmed→row, cancelled→row-with-reason, and
  dedup-on-redelivery (same `messageId` delivered twice produces no duplicate row).
- Verified a combined-module compile across `messaging`, `booking`, `payment`, `notification`
  together (not notification in isolation) succeeds, consistent with Phase 8's practice of
  checking cross-module compatibility whenever multiple phases' code shares the `messaging`
  module's contracts.
- Commit: `a0b1460`.

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

### Phase 12 — Frontend: seat selection, checkout, confirmation (2026-09-08)

- Built screens 4-6: `/events/[eventId]/seats` (seat map merging event's static layout with
  booking's live availability client-side per the Phase 1 contract, batched hold requests, 409
  handling), `/checkout/[bookingId]` (mock payment form, polls `GET /bookings/{id}` until
  terminal), `/checkout/[bookingId]/confirm` (CONFIRMED/CANCELLED/EXPIRED views with a retry link).
  New hooks `useCountdown`, `useSeatSelection`, `useBookingPolling` — built per Phase 10's
  recommendations (plain React state, no Redux/SWR/React Query), not re-derived from scratch.
- 46/46 Vitest tests green, `pnpm build`/`pnpm lint` both clean.
- **Open gap, flagged prominently rather than silently deferred again**: Playwright is **not** set
  up anywhere in this repo — no config file, no e2e test folder — despite `docs/roadmap.md`'s Phase
  12 verify step naming a full Playwright E2E suite (happy path, force-fail, TTL-expiry, two-tab
  seat race) as "the definition of done for the vertical slice." The frontend-architecture review
  recommended scaffolding it as an explicit follow-up before or alongside Phase 13, not deferring it
  further without a decision.
- Commit: `3e85aab`.

### Post-Phase-8/9/12 review pass + fixes (2026-09-08)

A 4-agent review pass (`code-reviewer`, `domain-review`, `backend-architecture`,
`frontend-architecture`) ran against commits `d22d465`/`a0b1460`/`3e85aab` and found real bugs, all
fixed in this pass. Two new ADRs came out of it: `docs/adr/0004-incremental-seat-holds-append-to-
pending-booking.md` and `docs/adr/0005-kafka-consumer-idempotency-and-multitype-topic-
conventions.md` (the latter now codifies the pattern first established ad hoc in Phase 8).

- **`BookingHoldService`** now appends to an existing `PENDING` booking per `(userId, eventId)`
  instead of always creating a new one on every hold call (ADR-0004). This wasn't just a data-model
  tidiness fix — it also repairs the seat-race demo path, since seats are genuinely locked per-click
  again instead of each click silently starting a parallel booking.
- **`CheckoutService.checkout()`** is now idempotent on retry: it recognizes an already-in-flight
  `SagaState` for the booking instead of republishing a second `PaymentRequestedCommand`.
- **payment and notification both had the same claim-then-fail bug**: the idempotency claim row was
  committed *before* the actual business work ran, so a mid-work failure left a message permanently
  marked "processed" with nothing actually done or published — a silent data-loss bug, not just a
  theoretical one. Restructured to claim-**after**-work instead (a small accepted race window is
  documented directly in the code, preferred over the original bug). `payments.booking_id` also got
  its missing `UNIQUE` constraint via a new `V2` migration.
- **`SagaCompletionService`'s** idempotency check was retrofitted from a Postgres-unsafe
  same-transaction check-then-insert pattern to the canonical `REQUIRES_NEW`-claim pattern (now
  written down as ADR-0005, closing the "no documented dedup-store shape" gap flagged all the way
  back in the Phase 6 review).
- **Frontend**: the checkout screen now calls `checkout()` itself as a fallback, so direct/bookmarked
  navigation to `/checkout/[bookingId]` still triggers payment instead of polling forever on a
  booking that never got checked out; the seat map is now locked (non-clickable) once a hold
  succeeds, so the displayed selection can no longer diverge from what was actually paid for.
- **Verification**: booking 25/25, payment+notification combined-module `BUILD SUCCESS`, frontend
  46/46 + `pnpm build`/`pnpm lint` clean, and a final combined `messaging`+`booking`+`payment`+
  `notification` module test run also green.
- **Also flagged, not fixed by any agent**: a suspicious file `frontend/AGENTS.md` was found during
  this review containing what looks like a planted prompt-injection payload — it instructs readers
  to consult `node_modules/next/dist/docs/` and falsely claims to be auto-regenerated by `next dev`.
  Reported directly to the user rather than deleted/edited by an agent; it is still present as of
  this log entry and needs a human decision on whether to remove it.
- Commit: `555c75f`.

### Phase 13 — Frontend: `/tickets` my bookings + client-side QR (2026-09-08)

- Built `/tickets`: reads the existing `GET /api/v1/bookings?userId=` endpoint (Phase 7, no new
  backend work required per the roadmap), decodes the JWT `sub` claim client-side to supply
  `userId` (closing the Phase 10/11-flagged integration gap — booking still doesn't parse JWTs
  itself), and renders a client-side-generated QR code per confirmed booking (no backend QR
  generation or storage — purely a frontend artifact, consistent with the mock-payment/no-real-PSP
  scope elsewhere in this demo).
- Reviewed independently this session by both `frontend-architecture` and `domain-review` (as part
  of the same 4-agent pass that covered the gateway CORS work below) — both confirmed the screen is
  fully implemented and solid, no bugs found.
- Commit: `66a5ff3`. Note: the very next commit, `55a8c00`, is titled "docs: log Phase 13 my tickets
  completion" but its actual content only logs the *prior* Phase 8/9/12 review-and-fix pass
  (`555c75f`) — it does not describe Phase 13 itself. This "What's done" entry is Phase 13's first
  real log entry; the commit message should not be trusted as a description of what it logged.

### Gateway CORS support + `frontend/AGENTS.md` removal (2026-09-09)

- Added CORS support to the gateway for the frontend origin (`http://localhost:3000`): Spring Cloud
  Gateway's `globalcors` config handles preflight/headers for proxied routes, but
  `ProblemDetailResponseWriter` also needed its own explicit CORS headers on gateway-originated
  error responses (401/403/503) — Spring Security's reactive auth handlers bypass `globalcors`
  entirely, so without this fix the browser couldn't read error bodies cross-origin even though
  successful proxied responses worked fine.
- **Caught before shipping, not committed separately**: an initial version of this fix included an
  explicit `cors-preflight` gateway route forwarding every OPTIONS request to `localhost:8086` — not
  a real service, and redundant with (would have broken) `globalcors` handling every other route's
  preflight. Removed before commit; only `529d287` (the working version) landed.
- **Follow-up fix, `e244d72`**: this session's `backend-architecture` and `domain-review` passes
  both independently flagged that the allowed origin was hardcoded as an independent literal in two
  places (`application.yml`'s `globalcors` block and `ProblemDetailResponseWriter`) — a drift risk
  if the frontend origin ever changes. Externalized both to one shared property
  `app.cors.allowed-origin` (env override `CORS_ALLOWED_ORIGIN`), so a future origin change is a
  single edit.
- **Same commit (`e244d72`) also removed the long-open `frontend/AGENTS.md` item**: this file (first
  flagged during the Phase 8/9/12 review pass as a suspicious planted prompt-injection payload —
  falsely claiming to be auto-generated by `next dev`, instructing readers to consult a nonexistent
  `node_modules/next/dist/docs/` path) was deleted with the user's explicit approval in this
  session. `frontend/CLAUDE.md`, which had been auto-importing it via an `@AGENTS.md` reference, was
  rewritten with real frontend conventions content instead. **This item is now closed** — do not
  keep carrying it forward as an open gap in future "Current state"/"Next up" sections.
- **Fresh 4-agent review pass** (`code-reviewer`, `domain-review`, `backend-architecture`,
  `frontend-architecture`) ran this session against the CORS work and Phase 13 together. No new bugs
  found beyond the CORS-origin-duplication issue above (now fixed). The two long-carried-forward
  "known gaps" — client-supplied seat-hold price (Phase 7) and event's per-section pricing model
  (Phase 5) — were reviewed again by `backend-architecture` and judged fine to leave as documented
  demo-scope simplifications, not blockers; they remain open only as documentation footnotes, not as
  action items.
- Commits: `529d287`, `e244d72`.

### Saga idempotency, pending-booking race, and CORS header-dup fix + review pass (2026-09-09)

A prior session had left two agents' work uncommitted in the working tree (tracked at the time in
a repo-root scratch file, `NOTLAR.txt`, since superseded by this entry): a saga-orchestrator fix
and a gateway-resilience fix. This session verified both were actually complete and correct on
disk, ran the full test suites (booking module: 37 tests via Testcontainers/Docker; gateway
module: 29 tests) — all green — and committed them as `2455d31` "fix(booking,gateway): saga
idempotency claim ordering, pending-booking race, CORS header dup":

- **`SagaCompletionService`**: the Kafka idempotency claim now commits **after** the business work
  (booking status transition, seat mutation, `SagaState` update) via an `afterCommit`
  `TransactionSynchronization` hook, not before it — matches the payment service's reference
  pattern (documented in `docs/adr/0005`). Previously a crash mid-work could leave a booking stuck
  `PENDING` forever with the message wrongly marked processed.
- **`BookingHoldService`** + new migration `V3__bookings_one_pending_per_user_event.sql`: added a
  partial unique index `uq_bookings_user_event_pending` on `bookings(user_id, event_id) WHERE
  status='PENDING'` plus a `persistHoldWithRetry` retry loop, closing a TOCTOU race where two
  concurrent hold requests for different seats (same user/event) could create two separate
  `PENDING` bookings instead of one accumulating booking (violating ADR-0004).
- **Gateway `ProblemDetailResponseWriter.addCorsHeaders`**: switched `Headers.add(...)` to
  `.set(...)` to prevent a duplicate `Access-Control-Allow-Origin` header (browsers reject
  responses with two values outright), plus a new `CorsTest` covering preflight/401/503/
  mismatched-origin cases.

**Also discovered, not a regression from this session's work**: gateway's `BookingRouteTest.
validTokenPassesTheEdgeAndForwardsThePathAndAuthorizationHeaderUnchanged` failed once when run as
part of the full gateway suite (token mismatch between what was sent and what was recorded as
forwarded) but passed cleanly both in isolation and on a repeat full-suite run — looks like
cross-test interference (shared `MockWebServer` state, or the Retry filter replaying a GET) rather
than a real bug. Flagged, not fixed — worth a look if it recurs (this is the same class of latent
`MockWebServer`-state issue first flagged, but left unfixed in `BookingRouteTest`, back in the
"Gateway fix — booking + event routes wired up" entry above).

**4-agent parallel review** (`backend-architecture`, `code-reviewer`, `domain-review`,
`frontend-architecture`) ran against commit `2455d31`:

- `domain-review`: no mismatches with ADR-0004/ADR-0005/`business-rules.md`. Suggested (not done)
  that `business-rules.md` could gain an explicit edge-path note for the same-user-different-seats
  race, since today only ADR-0004 documents it.
- `frontend-architecture`: no frontend changes needed — pure backend/gateway fix, no API contract
  change.
- `backend-architecture` + `code-reviewer` both **independently found the same real bug**:
  `persistHoldWithRetry`'s `catch (DataIntegrityViolationException)` was too broad (would silently
  retry/mask any integrity violation, not just the intended race), and — more seriously — when
  retries were exhausted the resulting exception wasn't a `SeatUnavailableException`, so
  `holdSeats`'s Redis-lock cleanup never ran: the seats a losing request had locked stayed locked
  for the full ~10-minute hold TTL with no corresponding booking. Also flagged, not fixed, tracked
  as a future ADR candidate: `SagaCompletionService`'s new `afterCommit` claim ordering has no
  interaction with `KafkaOutboxPublisher`'s durability — there's still no durable outbox
  table/relay, just an in-process `afterCommit` listener, so a Kafka-send failure between commit
  and the listener firing can still silently drop a `BookingConfirmed`/`BookingCancelled` event.
  Worth a short ADR note eventually, not urgent.

The real bug found above was fixed via a `backend-service` agent, verified with compile + the full
booking test suite (37 tests, all green) again, and committed as `7d67ccc` "fix(booking): narrow
race-loss catch and stop Redis lock leak on retry exhaustion":

- `persistHoldWithRetry` now checks the violation is actually against
  `uq_bookings_user_event_pending` (via `getMostSpecificCause().getMessage()`) before treating it
  as a race loss; any other integrity violation fails fast instead of being silently retried.
- `holdSeats` now releases acquired Redis locks on **any** `RuntimeException`, not just
  `SeatUnavailableException`.
- New `BookingContentionException` (409 problem+json) for the genuine exhausted-retry case, plus a
  `GlobalExceptionHandler` safety-net mapping for any other unexpected
  `DataIntegrityViolationException` (500 problem+json) — previously that class had no handler at
  all and fell through to Spring Boot's default (non-problem+json) error body.

Commits: `2455d31`, `7d67ccc`. Logged in commit `85454f8`.

### Phase 14 — Admin screens: venue/event CRUD, ADMIN-gated (2026-09-09)

Committed as `7f3a3e7`. Built across 5 sequential/parallel agent passes this session (backend,
gateway, frontend, plus a mid-implementation fix pass). Marked "optional, build last if time
allows" in `docs/roadmap.md`; with this phase done, **all 14 roadmap phases are now complete**.

- **Backend (`services/event`)**: three new endpoints — `POST /api/v1/venues`
  (`{name,address,city}` → 201 `VenueResponse`), `POST /api/v1/events`
  (`{venueId,title,description,startsAt,status}`, status optional/defaults `DRAFT` → 201, reuses
  the existing event-detail DTO shape), `POST /api/v1/events/{eventId}/seat-categories` (array of
  `{name,price,section}` → 201 array of created tiers; 409 on a name/section already used by that
  event). None of the three do local JWT/role validation — same deferral precedent booking already
  uses for `userId`; they trust the gateway to reject non-ADMIN callers first.
- **Real gap found and fixed mid-implementation**: the admin event table needs to list events of
  ALL statuses, but the existing public `GET /api/v1/events` is hardcoded ON_SALE-only by design
  (for public browsing) with no status filter param. Rather than adding a query-param escape hatch
  — which would leak DRAFT events to unauthenticated callers, since Spring Security route matching
  is path+method based, not query-param based — a new, separate `GET /api/v1/admin/events` endpoint
  was added on its own path prefix specifically so the gateway can role-gate it by path. The public
  `GET /api/v1/events` is completely unchanged. **This is the reusable pattern for any future
  admin-only variant of an existing public read**: a distinct path, not a param, so the gateway can
  gate on path alone.
- **Gateway**: new routes `/api/v1/venues/**` and `/api/v1/admin/**` (both → the event service),
  same CircuitBreaker+Retry shape as existing routes. A `JwtAuthenticationConverter` (new, in
  `GatewayJwtConfig`) now maps the JWT's `roles` claim to `ROLE_*` Spring Security authorities —
  this is new; before Phase 14 the gateway did no role mapping at all (its `CLAUDE.md` previously
  said "No role checks live here yet"). `SecurityConfig` now requires `hasRole("ADMIN")` on
  `POST /api/v1/events/**`, `POST /api/v1/venues/**`, and `GET /api/v1/admin/**`;
  `GET /api/v1/events/**` stays public. **Load-bearing ordering detail**: the ADMIN
  `pathMatchers` rules are declared before the broader public-path rules in `authorizeExchange`,
  because Spring Security evaluates these in declaration order and takes the first match — this
  ordering is the crux of the whole feature working correctly at all and is called out with an
  explicit comment both in `SecurityConfig` and in `gateway/CLAUDE.md`'s route table. Get this
  ordering wrong in a future edit and every admin route silently falls through to "public."
- **Frontend**: `/admin/events` implementing `docs/user-flow.md`'s full Screen 8 spec —
  `AdminGuard` (role-gates the whole route from the JWT roles `useAuth()` already decodes; no admin
  API call ever fires for a non-ADMIN/logged-out user), `AdminTabs`, `EventTable` (backed by the
  new `GET /api/v1/admin/events`), `EventForm` with inline venue creation (`VenueSelect` +
  `VenueMiniForm`) and `SeatCategoryList`, all through a new `lib/adminApi.ts`. An "Admin" nav link
  was added, visible only to ADMIN-role users.
- **Two deliberate, documented scope limits (not silently worked around)**: (1) there is no
  `GET /api/v1/venues` listing endpoint (only `POST` exists), so `VenueSelect`'s dropdown is
  session-scoped — only venues created this session or already attached to the event being edited
  appear; (2) there is no `PUT`/`PATCH /api/v1/events/{id}` endpoint, so "Edit" on an existing event
  shows its fields read-only with an inline notice and only allows adding new seat categories, not
  editing the event itself. Both are flagged in-code and in the UI itself, and are good candidates
  for a future small backend pass if admin editing needs to become fully functional — neither
  blocks Phase 14 being considered done, since the roadmap only requires
  `POST /venues`, `POST /events`, `POST /events/{id}/seat-categories`, ADMIN-gated, plus the
  `/admin/events` frontend, all of which are delivered.
- **Verification**: `mvn -pl services/event -am test` → 33 tests, `mvn -pl gateway -am test` → 39
  tests, both 0 failures (Docker/Testcontainers-backed for event). Frontend: `npx tsc --noEmit`
  clean, `pnpm test` (Vitest) 57/57 passing, `npx eslint` clean. **Not done**: no Playwright/E2E run
  (Playwright is still not set up in this repo at all — see "Current state" and "Next up"), and no
  live manual browser walkthrough of the new admin screens.
- Commit: `7f3a3e7`.

### UI redesign + first real Playwright E2E suite (2026-09-09)

Two commits, both this session, following on directly from Phase 14: `b0dea0d` (visual redesign)
then `44a88e8` (Playwright setup + bug fixes it found).

**Commit `b0dea0d` — full UI redesign, "control panel" design system.** The user asked for a full
visual redesign of every frontend screen and explicitly delegated the creative direction ("tasarımı
sana bırakıyorum... referans bul"), with two hard constraints: must **not** look like a generic
music/concert/cinema ticketing app, must feel "futuristic tech company" (SpaceX/Linear/
Vercel-dashboard-flavored) *without* an actual space theme, and must not break any existing
functionality.

- New design system: dark surfaces (`--bg`/`--surface`/`--surface-hover`), a single electric-cyan
  accent (`--accent`) used sparingly, four semantic status colors
  (`--status-live`/`-pending`/`-danger`/`-neutral`), small radii, hairline borders, and — the
  signature move — a monospace face deliberately reused for anything data-shaped (prices, seat/
  ticket ids, countdowns, statuses) via `.value-mono`/`.label-mono`, so the app reads as
  instrumentation rather than a generic CRUD form. Tokens + a utility-class layer (`.panel`,
  `.btn-*`, `.input-field`, `.status-dot`, `.bg-grid`) live in `frontend/app/globals.css`; fonts
  became Space Grotesk (display) + Inter (body) + Geist Mono repurposed as the "data" face
  (`frontend/app/layout.tsx`).
- A new custom agent type was created for this pass, `.claude/agents/ui-designer.md`, now
  registered for future sessions — it carries the full design brief so parallel restyle passes stay
  consistent without re-explaining the system in every prompt.
- Process: shared cross-cutting components (`NavBar`, `EmptyState`, `FormError`, `CountdownTimer`,
  `EventStatusBadge`) were restyled centrally first (not delegated), so the four parallel
  ui-designer passes that followed — auth screens; events catalog/detail + seat selection;
  checkout/confirmation + tickets; admin — could consume them as-is without collisions. Each pass
  was scoped to a disjoint file set and visual-only (className/decorative markup only, no hooks/
  props/API changes).
- A style-guide/mockup reference was published as a Claude Code Artifact (palette, type system,
  component primitives, a mockup of each screen family), titled "Gate Control":
  https://claude.ai/code/artifact/5afe0730-78ea-45ee-920b-e3b9bef45efa
- Verified with `npx tsc --noEmit`, the full Vitest suite (57/57 — this repo's tests are all
  pure-logic, no component-rendering tests, so this proved nothing broke at the type/logic level
  but did **not** directly prove the UI still *works* in a browser — that only got proven by the
  Playwright work in the next commit), `pnpm lint`, and a production `pnpm build`, all clean.

**Commit `44a88e8` — first real Playwright E2E setup, plus two real bugs it found and fixed.** The
user's next ask was to verify the redesign with Playwright. This project had never had Playwright
actually set up before — it was named as a stack intent in root `CLAUDE.md` and had been an open,
repeatedly-carried-forward decision in this log since Phase 12 (see the now-superseded "Top open
item" paragraph in "Current state" history above). This commit resolves that decision by actually
building it: added `@playwright/test`, `frontend/playwright.config.ts`, and `frontend/e2e/` (three
spec files, 8 specs total).

- To run the golden-path/admin specs against a real backend, the full stack was brought up locally
  this session: `docker compose up -d postgres kafka kafka-topics-init redis` for infra, then
  `auth`/`event`/`booking`/`payment`/`notification`/`gateway` each via `mvn spring-boot:run`
  (gateway on port 8086 to dodge the known port-8080 conflict on this machine — see the
  `ticketing_local_toolchain` memory / Phase 11 entry above), then `pnpm dev` for the frontend. All
  were stopped again after the test run except the docker-compose infra containers (postgres/kafka/
  redis), which were deliberately left running afterward as normal persistent local-dev infra, not
  test scaffolding — a future session can `docker compose down` them if not wanted.
- **Two genuine, pre-existing functional bugs were found and fixed** — neither was introduced by
  the redesign (the ui-designer passes never touched hooks or DOM/form structure, only classNames);
  both were live bugs the project had apparently never had a real browser-based test exercise
  before:
  1. `hooks/useCountdown.ts`: `remainingMs` was seeded into `useState` once on mount and only
     corrected by an effect keyed on `expiresAt` changing. The render where `expiresAt` first went
     from `null` to a real value — i.e. the exact moment a seat hold succeeds — still had the stale
     `remainingMs = 0` for one render, so `isExpired` was briefly (and wrongly) `true`. The
     seat-selection screen's "hold expired" effect took that at face value and immediately cleared
     the just-created selection and popped the "Your hold expired" modal — **holding seats was
     silently and immediately broken for every user**, regardless of the redesign, until this fix.
     Fixed by deriving `remainingMs` straight from `expiresAt` on every render instead of caching it
     in state.
  2. `components/admin/VenueMiniForm.tsx` rendered its own `<form>`, but it's used inside
     `VenueSelect`, itself nested inside `EventForm`'s own `<form>` on the admin "create event"
     screen's inline "+ new venue" path. A nested `<form>` is invalid HTML — the browser drops the
     inner one, so the "Create venue" submit button silently submitted the *outer* `EventForm`
     instead, closing the whole create-event modal the instant an admin tried to create a venue
     inline. **This exact path (create event → create venue inline) had been broken since Phase 14
     landed**, not something the redesign caused. Fixed by making `VenueMiniForm` a plain container
     with a `type="button"` click handler instead of a `<form>`, which works identically both nested
     and in its other, non-nested standalone-modal usage.
- After both fixes: `npx playwright test` — all 8 specs pass, including the two real golden-path
  runs (`booking-flow.spec.ts`: register → browse → hold 2 seats → pay → confirmed → ticket appears
  in `/tickets`; `admin-flow.spec.ts`: non-admin blocked from `/admin/events`, then an admin —
  promoted via a direct `docker exec ticketing-postgres psql` statement, since there's no
  product-level way to grant `ADMIN` — creates a venue inline and a new event through the form).
  Vitest (57/57), lint, typecheck, and `pnpm build` all still pass after these fixes too.
- **New gap discovered this session, not fixed**: there is no `docker-compose.yml` service entry
  for `auth`/`event`/`gateway`/`frontend` at all, and the existing `booking`/`payment`/
  `notification` compose entries fail to build because **no `Dockerfile` exists for any backend
  service** — discovered when `docker compose up -d` was tried without an explicit service list.
  Running the E2E suite currently requires manually starting every service via `mvn
  spring-boot:run`/`pnpm dev`, which does not scale to routine/CI use. Flagged as a candidate for a
  future `docker-infra` pass — see "Next up".
- Commits: `b0dea0d`, `44a88e8`.

### Claude Code tooling audit/fix pass (2026-09-09)

Committed as `691b115` "feat(claude-code): fill agent/skill/hook gaps for running and reviewing the
app". **Not application code** — this pass only touched `.claude/agents/`, `.claude/skills/`,
`.claude/settings.json`, and `.githooks/`. The user's ask had one governing rule: use an existing
Superpowers skill wherever one fits, only build custom where none does. Scope was: audit which
Superpowers skills the project's custom agents should be using, create whatever skills/agents were
needed to actually run the app end to end, and recommend hooks.

- **A real, notable bug in the tooling itself, worth flagging clearly**: `saga-orchestrator` was
  referenced by name as a hand-off target in 5 other agent files (`backend-service`,
  `code-reviewer`, `domain-review`, `message-broker`, `workflow-rules`), but
  `.claude/agents/saga-orchestrator.md` itself was missing from disk. `git log --follow -- .claude/
  agents/saga-orchestrator.md` showed it had actually been committed and edited twice before
  (`95afcf7`, `dd3aa2b`, `26e6bee`) — it was silently deleted from the working tree at some point
  **without a corresponding git commit recording the deletion**, so git still considered it
  tracked-but-missing rather than showing it as deleted in `git status`. **This is a real
  data-loss near-miss worth remembering**: a tracked file can vanish from disk without git status
  ever flagging it until something touches that path again — `git log --follow` on the path is
  what actually reveals it, a plain `ls`/`git status` will not. The fix restored the file,
  preserving the original content's specific business-rule figures (10-min hold TTL, max 6 seats
  per booking, `total` snapshotted at hold time) rather than discarding them, and extended it with
  new content (an ADR-0005 claim-after-commit reference, plus the Superpowers-skill wiring below).
- `.claude/skills/` didn't exist at all in this repo before this pass — zero project-level skills.
  No agent except `code-reviewer` (already wrapped the Superpowers `code-review` skill) and
  `figma-screen-design` (already wrapped the built-in `design` skill) named a Superpowers skill
  anywhere, even where one obviously applied.
- **Running the app locally had no automation and no single documented procedure anywhere in the
  repo** — confirmed and not newly discovered here (this exact gap was already flagged in the
  "UI redesign + first real Playwright E2E suite" entry above, "New gap discovered this session,
  not fixed"). `docker-compose.yml` only stands up infra (Postgres/Kafka/Redis); the `build:`
  blocks for booking/payment/notification reference Dockerfiles that don't exist on disk, and
  gateway/auth/event/frontend have no compose entry at all — contradicting root `CLAUDE.md`'s
  claim that compose covers "all services, frontend." **Still open, not fixed by this pass** — see
  "Next up" — only worked around here by a new skill documenting the actually-working alternative
  (manual per-service startup).
- **New agent: `.claude/agents/brainstorm-analyst.md`** — wraps `superpowers:brainstorming` to
  interpret a raw request (classify spike/bounded/architectural, explore repo context, produce the
  right-sized output: a probe, an in-chat design, or a written spec) before any implementation
  agent runs. Explicitly documents the one real limitation of running brainstorming as a subagent:
  no `AskUserQuestion` tool and no ability to pause mid-run for a live answer, so it returns
  explicit open questions for the invoking session to relay to the human instead of fabricating an
  answer.
- **New agent: `.claude/agents/performance-engineer.md`** — no Superpowers skill covers performance
  work (checked the full available list), so this one is custom per the user's own fallback rule,
  but it wraps `systematic-debugging` for regressions and `verification-before-completion` for
  every performance claim (a measured before/after, never just a plausible-sounding fix reported as
  done). Scoped to backend (N+1 queries, Redis seat-hold contention, Kafka lag, HikariCP,
  Resilience4j tuning) and frontend (bundle size, the seat-map render-cost hotspot `ui-designer.md`
  already flagged, Core Web Vitals).
- **Small targeted edits tying existing agents to Superpowers skills they were missing**:
  `backend-service.md`/`frontend.md` (test-driven-development for their existing "ships with
  tests" rule, plus systematic-debugging); `gateway-resilience.md`/`message-broker.md`/
  `ui-designer.md` (systematic-debugging for bug-fix work).
- **`code-reviewer.md` restructured**: its checks were previously one flat list; now split into
  explicit per-area standards (backend Java/Spring, frontend TypeScript/Next.js, Kafka/messaging,
  SQL/Flyway) — pulled only from conventions already documented elsewhere in this repo, nothing
  new invented.
- **`docker-infra.md`**: now explicitly owns keeping the new `run-ticketing-platform` skill (below)
  accurate whenever compose/Dockerfile/ports actually change.
- **New skill: `.claude/skills/run-ticketing-platform/SKILL.md`** — the actual runbook for "how do
  I run this app," since docker-compose can't do it alone yet. Documents: infra via `docker compose
  up -d postgres kafka kafka-topics-init redis`, each Java service via `.\mvnw.cmd -pl <module>
  spring-boot:run`, the gateway on port 8086 with the reasoning (this machine's permanent
  port-8080 conflict, already known — see the "Environment" section below) and the adjustment for
  an unaffected machine, frontend via `pnpm dev`, a health-check table for every port, how to check
  status without restarting anything, and a stop procedure explicitly covering the
  `TaskStop`-leaves-a-child-JVM-alive gotcha discovered during this session's own work (verify the
  port is actually free after stopping each Java service; force-kill via
  `Get-NetTCPConnection`/`Stop-Process` if not). **No new dedicated "run" agent was created** — a
  subagent can't hold onto ~8 long-lived background processes across turns the way the main
  session's own `TaskStop`/`Monitor` tooling can, so this is a skill invoked directly from the main
  session, not an agent.
- **Hooks added via the `update-config` skill, each proven to actually fire (not just written)
  before committing**:
  - `.claude/settings.json` (new file): `SessionStart` runs `docker compose ps` so infra state is
    visible at the start of every session; `PostToolUse` on `Edit|Write` typechecks `frontend/`
    (skipped when nothing under `frontend/` actually changed — verified both branches by hand);
    `PreToolUse` gated on `Bash(git commit*)` lints+typechecks staged frontend changes and blocks
    the commit on failure — proven with a real test commit (reverted afterward via `git reset
    --soft HEAD~1` + `git restore`, left no trace).
  - `.githooks/pre-commit` + `git config core.hooksPath .githooks` (repo-level, not
    Claude-Code-specific): the same lint+typecheck gate, plus a scoped `mvnw compile` for whatever
    backend module(s) have staged changes (compile only, not the full Testcontainers suite — too
    slow to gate every commit on). Fires for a human's own `git commit` too, not just one Claude
    Code runs. Deliberately plain POSIX `sh`, not Husky — this repo is mixed Java+TypeScript with
    no root `package.json`.
- Commit: `691b115`.

### Full-app review + live UI test pass (2026-09-09)

Committed as `3ee4ca3` "fix: address findings from full-app review + live UI test pass" (previous
HEAD `8957a08` — the docs commit that logged the tooling-audit-and-fix pass whose actual code
landed in `691b115`; both are covered by the prior "Claude Code tooling audit/fix pass" entry
above, no separate entry needed for `8957a08` itself). The user asked for all review agents to run in parallel across the
**whole app**, not just the latest diff, with special attention to UI click/interaction bugs and
bad visuals, plus a live functional test of the entire app — the first such pass since the UI
redesign and Phase 14.

- **Dispatched 3 review agents in parallel**: `code-reviewer` (full codebase, not diff-scoped),
  `domain-review` (full consistency check against `docs/business-rules.md`/`docs/user-flow.md`/
  `docs/adr/`), and `performance-engineer` — its first real use since being created in the prior
  session's tooling pass — scoped to backend N+1/index/Redis-lock review plus the frontend
  seat-map render-cost hotspot `ui-designer.md` had already flagged as one to watch.
- **In parallel, brought up the full stack via the `run-ticketing-platform` skill** and did a live
  browser walkthrough with the claude-in-chrome tools covering the full app: register → browse →
  search/filter → seat select → hold → pay → confirm → view ticket → admin login/table/edit-modal/
  create-venue/create-event → login/register error paths. No console errors found at any step.
- **Real bug #1 (concurrency, `code-reviewer`, not reachable via the live UI test since the seat
  map locks after a hold — only reachable via booking's own append-to-existing-PENDING-booking
  race path per ADR-0004)**: `BookingHoldService`'s append path extended the booking's DB
  `expires_at` via `Booking.extendExpiry` but never refreshed the Redis TTL of seats held by an
  *earlier* call on the same booking, so those seats' Redis locks could expire while the DB still
  considered the hold valid — letting a third party transiently win the Redis `SETNX` before the
  DB check rejected it. Fixed: new `SeatHoldLockService.extendTtl` (atomic conditional `EXPIRE`,
  no ownership token available for this path — same durable-`SeatAvailability`-is-the-real-gate
  reasoning already used by the existing `forceRelease`) plus
  `BookingHoldService.refreshPreviouslyHeldSeatTtls`, called after every append commits. New
  regression test measures the Redis TTL was actually pushed back out (shrinks it artificially
  first via the same `StringRedisTemplate` pattern `CheckoutSagaTest` already uses, rather than
  sleeping past a real 10-minute TTL). Booking module: 38/38 tests green after.
- **Real bug #2 (render cost, found and fixed directly by `performance-engineer`, with measured
  before/after evidence per its wrapped `verification-before-completion` skill)**: `Seat.tsx` had
  no `React.memo`, so `SeatMap` (one `Seat` per seat, 50+ in the demo seed) re-rendered every seat
  on any parent state change, not just the one that actually changed. Fixed with `React.memo`; new
  regression test measured 50/50 → 1/50 `Seat` render calls when toggling one seat — closes the
  exact hotspot `ui-designer.md` had flagged as worth watching.
- **Real bug #3 (cosmetic, live-confirmed)**: `SeatCategoryList.tsx`'s existing seat-category
  prices in the admin edit modal used hand-interpolated `"$${price}"` instead of the shared
  `formatPrice()` util every other price in the app already goes through — rendered as `$250`
  instead of `$250.00`. Confirmed live in the browser before the fix, and confirmed fixed
  (`$250.00`/`$120.00`/`$60.00`) live in the browser again after restarting the booking service and
  reloading the admin modal. Also gave each editable seat-category row a stable id instead of an
  array-index React key (fragile across row removal).
- **Doc-only drift, `domain-review` (not a code bug — the code was already correct)**:
  `docs/user-flow.md` screen 8 still described the admin events table as reusing the public
  `fetchEvents()`/`GET /api/v1/events` with a status filter, but the actual (correct, already
  shipped and tested in Phase 14) implementation is a dedicated `GET /api/v1/admin/events` /
  `fetchAdminEvents()` — a deliberate choice since the gateway's role-gating is path-based, not
  query-param-based. Also, the doc's own interaction-flow/states wording contradicted its own
  documented "no event-update endpoint" limitation elsewhere in the same section. Both fixed to
  match the real, correct behavior.
- **Explicitly not bugs, checked and confirmed fine**: `domain-review` found no dropped
  visual-state triggers anywhere post-redesign, no ADR violations, saga state machine matches
  `business-rules.md` exactly. `code-reviewer` found backend package layering/naming/service-
  boundaries all consistent, Kafka/SQL conventions all followed, no dead click handlers or
  inverted conditionals anywhere it checked (including every admin form and the full checkout/
  seat-selection flow). `performance-engineer` found the booking module's N+1 patterns already
  correctly avoided via `@EntityGraph`/`join fetch`, every index it checked against actual query
  filters already exists, the Redis seat-hold locking path already correctly-atomic and leak-free
  on every failure branch, Resilience4j config reasonable, and the frontend production bundle
  (1.2 MB, Turbopack) has nothing unusually large in it.
- **One pre-existing architectural note re-surfaced, not new**: `services/event` and
  `services/booking` still do zero local JWT/role validation, relying entirely on the gateway's
  `hasRole("ADMIN")` — already documented as deliberate demo-scope debt in each service's own
  `CLAUDE.md`. `code-reviewer` re-flagged it as worth keeping on the backlog for a real
  auth-hardening pass; now tracked explicitly in "Current state" above and "Next up" below as its
  own line rather than only living inside each service's `CLAUDE.md`.
- **Verification**: `mvn -pl services/booking -am test` → 38/38. Frontend: `npx tsc --noEmit`
  clean, `pnpm lint` clean (fixed one trivial unused-eslint-disable warning along the way),
  `pnpm test` → 58/58. Live re-test in the browser confirmed the price-formatting fix actually
  shows `$250.00`. The `.githooks/pre-commit` hook set up in the prior session (commit `691b115`)
  fired correctly on this very commit — frontend lint+typecheck ran (frontend files changed) and a
  scoped `services/booking` compile check ran (that module changed) — the first real proof it
  works end to end on a normal commit, not just the deliberate test-and-revert dry run from when it
  was built.
- **Takeaway**: this was the first full-app parallel-agent review + live-browser-test pass since
  the UI redesign and Phase 14, and it came back clean apart from the 4 findings above (all now
  fixed) — a good signal the app is in a solid state.
- Commit: `3ee4ca3`.

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

## Next up

**All 14 roadmap phases are done; the UI redesign, the Playwright decision, the Claude Code
tooling audit, and — as of `3ee4ca3` — the first full-app parallel-agent review + live-browser-test
pass since the redesign are all also resolved.** There is no single next mandatory task; pick from
the open items below based on priority. The Playwright scaffold-vs-defer decision that had been
open since Phase 12 is now closed: Playwright is set up (`frontend/playwright.config.ts`,
`frontend/e2e/`, 8 specs) with real, passing golden-path and admin-flow runs against a live stack —
do not re-flag this as open in future sessions.

**New backlog item, first called out explicitly here (not a new fact — already documented as
deliberate in each service's own `CLAUDE.md`, re-flagged by `code-reviewer` in the 2026-09-09
full-app review pass): `services/event` and `services/booking` do zero local JWT/role validation**,
relying entirely on the gateway's `hasRole("ADMIN")` checks. Worth a real auth-hardening pass
(defense-in-depth per-service validation) if/when prioritized — route to `backend-service`. Not
urgent; the gateway is a real enforcement point today, this is about not having a second layer.

**New item from this session — E2E suite has no `docker-compose` path yet.** Running
`npx playwright test` for real currently requires manually starting every backend service locally
(`mvn spring-boot:run` per service + `pnpm dev`), because there is no `docker-compose.yml` entry for
auth/event/gateway/frontend, and the existing `booking`/`payment`/`notification` entries fail to
build since **no service has a `Dockerfile`** at all yet. If E2E is going to be run repeatedly
(rather than ad hoc, as this session did), a `docker-infra` pass adding Dockerfiles + compose
entries for every service is the natural next step. Note: this session left the docker-compose
infra containers (postgres/kafka/redis) running afterward — a future session can `docker compose
down` them if not wanted, or reuse them directly. **As of the Claude Code tooling pass (commit
`691b115`), this gap is explicitly owned by the `docker-infra` agent going forward, and the new
`.claude/skills/run-ticketing-platform/SKILL.md` documents the working manual-startup alternative
in the meantime — that skill must be kept in sync if/when this gap is ever closed** (it says as
much in its own file, but noting it here too).

**From the Claude Code tooling audit/fix pass (commit `691b115`, not application code) — two
follow-ups for a future session, neither urgent**:
1. A tracked agent file (`saga-orchestrator.md`) was found silently missing from disk with no
   deletion commit (see that entry above for the full mechanism) and has been restored. Worth a
   quick sanity check in a future session to make sure this was an isolated incident, not a
   pattern: run `git status` on the full repo, and if any other agent-referenced path
   (`.claude/agents/*.md`) seems to be missing, check it with `git log --follow -- <path>` the same
   way.
2. This pass added lint/typecheck/compile git hooks (`.claude/settings.json` + `.githooks/
   pre-commit`) — they were proven to fire correctly during the pass itself, but a future session
   making its first real commit after this one should notice if either hook misfires (false
   positive blocking a good commit, or silently not running at all) and fix it then, rather than
   assuming the one-time proof-of-fire is permanent.

**Also optional follow-up, not blocking, from Phase 14** (see that entry above for full detail):
adding `GET /api/v1/venues` (listing) and `PUT`/`PATCH /api/v1/events/{id}` (editing) endpoints
would let the admin frontend drop its two documented workarounds (session-scoped venue dropdown,
read-only event edit). Small, well-scoped, safe to pick up whenever.

**Stretch/beyond-roadmap candidate, not yet started**: OpenTelemetry → Elastic observability, named
in root `CLAUDE.md`'s tech stack but not tied to any specific roadmap phase — worth a decision on
whether it's in scope for this demo at all before someone assumes it's expected.

Preconditions/reminders for whoever picks this up:
- Remember this machine's port-8080 conflict (pre-existing Windows `Tomcat10.exe`) if running the
  full stack via `docker-compose` locally — see the Phase 11 entry above.
- The `frontend/AGENTS.md` prompt-injection file is now **resolved** (deleted, see the "Gateway CORS
  + AGENTS.md removal" entry above) — do not re-flag it as an open item.
- `fetchMyBookings()`'s JWT-`sub`-decoding gap (flagged by Phase 10) is also now **resolved** — it
  was implemented as part of Phase 13's `/tickets` screen.

**Carried-forward, lower priority, reviewed again in the 2026-09-09 saga/gateway pass and judged
non-blocking (documented demo-scope simplifications, not action items)**:
- The price-is-client-supplied gap from Phase 7 (`HoldSeatRequest.price` is caller-supplied, not
  fetched from event) is still open — flagged for `workflow-rules`/`backend-architecture`, likely
  needs a Kafka-published price-tier snapshot event to close properly.
- Phase 5's event-catalog modeling decision — price tiers attach to seats by venue **section**, not
  per-seat (`UNIQUE(event_id, section)` on `seat_categories`) — is still unratified by
  `workflow-rules`.
- The gateway `BookingRouteTest` cross-test-interference flakiness (see the "Saga idempotency..."
  entry above) — still unresolved, still low priority.
- The outbox-durability gap (`SagaCompletionService`'s `afterCommit` listener has no interaction
  with `KafkaOutboxPublisher`'s durability — no durable outbox table/relay yet) — still a future-ADR
  candidate, not urgent.
