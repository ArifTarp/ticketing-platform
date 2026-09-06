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

## Current state (as of 2026-09-07)

**Phases 1–3 are complete and committed.** Phase 3 (auth service: register/login, JWT issuing)
landed in commit `7dff70f`. **Next: Phase 4 — Gateway with JWT validation** per `docs/roadmap.md`.

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
- **Known gap, not yet resolved:** `AuthControllerTest` (the Testcontainers-backed integration
  test) could not be run to green in this environment. Root cause confirmed via direct
  investigation (not just the implementing agent's report): Docker Desktop 4.89.0 on this machine
  returns a locked-down/stub `/info` response (all fields empty except a `Labels` hint pointing at
  an internal `docker_cli` proxy pipe) to any client that isn't the official `docker` CLI binary —
  reproduced identically over the default named pipe, the `dockerDesktopLinuxEngine` named pipe,
  and an explicitly-enabled TCP port (2375); `curl` against that same TCP port returns full genuine
  data, but testcontainers/docker-java gets the stub every time regardless of transport. This looks
  like a Docker Desktop-side API lockdown for non-official clients, not a config mistake on our
  end. `JwtServiceTest` (the plain unit test, no Docker needed) was confirmed genuinely green.
  A future session should **not** re-attempt the `DOCKER_HOST`/`exposeDockerAPIOnTCP2375` fixes
  expecting a different result (see Environment note below) — this needs either a different Docker
  Desktop version, a different container runtime, or running the test suite outside Docker
  Desktop entirely (e.g. a Linux CI runner, or a real Linux/WSL2-native Docker Engine without
  Docker Desktop's proxy layer).
- Commit: `7dff70f`.

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
  fix Testcontainers:** the setting was flipped to `true` in both `%APPDATA%\Docker\settings.json`
  **and** `%APPDATA%\Docker\settings-store.json`. The latter is the actual authoritative store in
  this Docker Desktop version — `settings.json` alone gets silently overridden back to `false` by
  `settings-store.json` on restart, which cost real time to discover. Even with the port genuinely
  open (verified with `curl`), Testcontainers/docker-java still only gets a locked-down stub
  `/info` response (see Phase 3 entry above for the full root-cause writeup) — so don't expect
  this same TCP-exposure fix to work again without also addressing the underlying Docker Desktop
  API lockdown.

## Next up: Phase 4 — Gateway with JWT validation

Per `docs/roadmap.md`: Spring Cloud Gateway routes to all 5 backend services, JWT validation at
the edge (reject unauthenticated/invalid tokens before they reach a service), Resilience4j
(circuit breaker + timeout + retry) on sync routes. Preconditions: auth's JWT signing
key/algorithm (from Phase 3, commit `7dff70f`) must be shared with or independently verifiable by
the gateway. Separately, Phase 3's `AuthControllerTest` Testcontainers gap (see above) is still
open — a future session should decide whether to fix the Docker/Testcontainers environment issue
or move the auth integration test to a different verification strategy before this is considered
fully closed, though it does not block starting Phase 4.
