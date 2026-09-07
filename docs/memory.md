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

## Current state (as of 2026-09-07)

**Phases 1–6 are complete and committed.** Phase 3 (auth) landed in `7dff70f`, Phase 5 (event
catalog) in `62091ee`, Phase 4 (gateway with JWT validation) in `8a47d93` — Phase 5 and Phase 4
were built in parallel this session and both are done, so despite the numbering, Phase 4 landed
chronologically after Phase 5 — and Phase 6 (Kafka topics & DTO scaffolding) in `d35012b`. The
Testcontainers/Docker blocker noted below on 2026-09-07 is now **resolved** (native WSL2 Docker
Engine, see "Environment" below) — `AuthControllerTest` and the full multi-module suite both run
and pass. **Next: Phase 7 — Booking service (CRUD + Redis seat holds, no saga yet — the seat-race
concurrency logic)** per `docs/roadmap.md`.

Local toolchain is installed and working on this machine (see "Environment" below) — a fresh
session does not need to reinstall anything, just re-verify with the commands in that section.

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

## Next up: Phase 7 — Booking service: CRUD + Redis seat holds (no saga yet)

Per `docs/roadmap.md`: build `services/booking/` — `bookings`/`booking_items`/`seat_availability`/
`saga_state` + Flyway (datasource must use the least-privilege `booking_app` Postgres role, per
the Phase 2 fix, not the superuser). Endpoints: `POST /bookings/hold`, `GET /bookings/{id}`,
`GET /bookings?userId=me&status=`. Business rules to enforce: max 6 seats per booking, price
snapshotted at hold time (not looked up live at confirm time).

**The seat-race concurrency logic is the centerpiece of this phase**: a Redis distributed lock
per `eventId:seatId`, 10-minute TTL, must resolve two concurrent hold requests on the same seat to
exactly one 200 and one immediate 409 — via the Redis lock, not a DB unique-constraint race. This
must be proven with an automated Testcontainers test racing two concurrent clients on the same
seat, not just described in prose or manually spot-checked; the roadmap explicitly calls out that
this test must pass reliably (not flaky).

The `messaging` module's DTOs (Phase 6, commit `d35012b`) are now available as a dependency, but
Phase 7 itself does not need Kafka — booking only starts publishing/consuming (`PaymentRequested`,
`PaymentCompleted`/`Failed`, `booking.events`) in Phase 8's saga wiring. Don't reach for Kafka
here.

**Carried-forward open item, worth resolving before or during this phase rather than letting it
linger further**: Phase 5's event-catalog modeling decision — price tiers attach to seats by venue
**section**, not per-seat (`UNIQUE(event_id, section)` on `seat_categories`) — is still unratified
by `workflow-rules`. Phase 7's booking logic will read seat/price data shaped by that decision
(price snapshotting at hold time reads from event's per-section pricing), so it's worth getting
`workflow-rules` sign-off now rather than building booking's price-snapshot logic on top of an
unratified model and having to revisit it later.

Agents per the roadmap: `saga-orchestrator` (owns booking's concurrency-sensitive lock code),
`backend-service` (plain CRUD/entities), `gateway-resilience` (route).
