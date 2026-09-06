# Implementation Roadmap — Zero to Vertical Slice

> Read alongside `CLAUDE.md`, `business-rules.md`, and `user-flow.md`. This file sequences the
> work needed to go from an empty repo to the working vertical slice described in the root
> `CLAUDE.md` ("Scope for the demo"): register/login → browse an event → hold seats → pay (mock)
> → confirmed booking + notification, with the full saga and its three edge paths (payment fail,
> hold expiry, seat race) working — not just the happy path. Admin screens/features are explicitly
> last/optional per `user-flow.md`.
>
> Each phase produces something runnable/testable — no big-bang integration at the end. Each
> phase names which `.claude/agents/*.md` project agent should do the work.

## Dependency order (why this sequence)

Workflow/screen design (no code dependency, needed before anything else is built with confidence)
→ `docker-infra` (Postgres/Kafka/Redis) → `auth` (gates everything downstream via JWT) →
`gateway` (frontend only ever talks to the gateway) → `event` (booking needs seat/price data) →
Kafka topic/DTO skeleton (per CLAUDE.md: "model a workflow as Kafka events first") → `booking`
CRUD + Redis hold (split from the saga because the seat-race concurrency logic lives here) →
saga wiring (`payment` + checkout) → `notification` → frontend (architecture check → auth/browse
screens → seat/checkout/confirmation [centerpiece] → my tickets) → admin (optional, last).

## Phases

### Phase 1 — Workflow & screen design
Before any code: formalize the checkout workflow as an explicit step sequence, and turn
`user-flow.md`'s screen list into real wireframe-level detail (layout, component hierarchy,
states, "user clicks X → Y happens" interaction flow) so backend and frontend implementation
agents have a concrete target instead of inferring one mid-implementation.
- **Agents:** `workflow-rules` (formalizes the saga workflow + ADR-001), `figma-screen-design`
  (wireframe detail + optional visual mockups for the 7 core screens; screen 8/admin stays
  low-detail since it's optional/last).
- **Verify:** `docs/adr/0001-seat-availability-in-booking.md` exists and is consistent with root
  `CLAUDE.md`; `docs/user-flow.md` has wireframe-level detail for screens 1-7; no contradictions
  between the two documents' state machines (e.g. seat states, booking status).

### Phase 2 — Repo & infra skeleton
- Maven multi-module parent `pom.xml`; empty `gateway/` and `services/*` module stubs (pom +
  `Application.java` + per-service `CLAUDE.md`).
- `docker-compose.yml` with Postgres, Kafka, Redis only (no app containers yet).
- **Agents:** `docker-infra`, `backend-architecture` (review module layout).
- **Verify:** `docker compose up -d postgres kafka redis` all healthy; `mvn -q validate` passes on
  the empty multi-module build.

### Phase 3 — Auth service
- `users/roles/user_roles` + Flyway; `POST /auth/register`, `POST /auth/login` issuing JWT.
- **Agents:** `backend-service`, `workflow-rules` (confirm no business-rules.md changes needed).
- **Verify:** Postgres + auth up; curl register/login returns a JWT; Testcontainers suite green.

### Phase 4 — Gateway with JWT validation
- Route to auth (open), JWT validation filter elsewhere, Resilience4j template on the auth route.
- **Agent:** `gateway-resilience`.
- **Verify:** postgres+auth+gateway up; login works through the gateway; protected stub route
  without a token → 401.

### Phase 5 — Event catalog service
- `venues/events/seat_categories/seats` + Flyway; `GET /events`, `GET /events/{id}`,
  `GET /events/{id}/seats` (availability hardcoded AVAILABLE for now). Seed demo data via Flyway.
- **Agents:** `backend-service`, `gateway-resilience` (route), `backend-architecture` (confirm the
  event/booking boundary before writing seat endpoints).
- **Verify:** event container added to compose; `GET /api/v1/events` via the gateway returns
  seeded data; Testcontainers test.

### Phase 6 — Kafka topics & DTO scaffolding
- Define `payment.commands`, `payment.events`, `booking.events`; command/event DTOs; DLQ
  convention.
- **Agents:** `message-broker`, `workflow-rules` (validate DTO fields against
  `business-rules.md`), `docker-infra` (topic bootstrap + Kafka health check).
- **Verify:** `docker compose up kafka`; topics listed; a throwaway producer/consumer round-trips
  a sample DTO.

### Phase 7 — Booking service: CRUD + Redis seat holds (no saga yet)
- `bookings/booking_items/seat_availability/saga_state` + Flyway; Redis distributed lock on
  `eventId:seatId`, 10-minute TTL.
- `POST /bookings/hold` — **the concurrent seat-race edge path is built and proven here**: two
  concurrent holds on one seat resolve to one 200 + one immediate 409 via the Redis lock, not a DB
  race. Write a Testcontainers test racing two clients on the same seat.
- `GET /bookings/{id}`, `GET /bookings?userId=me&status=`; max 6 seats/booking; price snapshotted
  at hold time.
- **Agents:** `saga-orchestrator` (owns booking's concurrency-sensitive logic, including this
  pre-saga lock code), `backend-service` (plain CRUD/entities), `gateway-resilience` (route).
- **Verify:** compose adds booking+redis; single hold succeeds; two concurrent requests for the
  same seat → one 409; race test passes reliably, not flaky.

### Phase 8 — Payment mock + checkout saga wiring
- payment: `payments` table, consumes `PaymentRequested`, publishes `PaymentCompleted`/
  `PaymentFailed` (default success; force-fail flag/threshold makes the failure path demoable on
  demand).
- booking: `POST /bookings/{id}/checkout` publishes `PaymentRequested`; idempotent consumer
  updates `SagaState`, marks seats SOLD/released, booking CONFIRMED/CANCELLED, publishes
  `booking.events`; outbox pattern; DLQ.
- **Payment-fails edge path** here: force-fail → `PaymentFailed` → booking CANCELLED, seats
  released.
- **Hold-expiry edge path** here: scheduled sweep on `expires_at`, independent of the payment
  path — test with a short TTL override, don't wait 10 real minutes.
- **Agents:** `saga-orchestrator` (booking-side: consumers, SagaState, outbox, sweep, DLQ),
  `backend-service` (payment CRUD/mock logic), `message-broker` (if a DTO/DLQ gap surfaces),
  `gateway-resilience` (route).
- **Verify:** full backend stack up; three automated Testcontainers scenarios: (1) happy path →
  CONFIRMED, (2) force-fail → CANCELLED + seat released, (3) short-TTL timeout →
  EXPIRED/CANCELLED + seat released — plus Phase 7's race test still green.

### Phase 9 — Notification service
- `notifications` table, consumes `booking.events`, idempotent on event id, logs
  CONFIRMED/CANCELLED notifications.
- **Agent:** `message-broker` (consumer boilerplate fits its scope).
- **Verify:** re-run the three Phase 8 scenarios; one notification row per event, no duplicates on
  redelivery.
- **Checkpoint:** the backend vertical slice is now demoable via curl/Postman — a good place to
  pause before frontend work.

### Phase 10 — Frontend architecture pass
- Short structural validation against `docs/user-flow.md`'s (now wireframe-detailed) screens: route
  structure, state management for the countdown/status-polling screens (4-6), API client shape.
- **Agent:** `frontend-architecture` (advisory only).
- **Verify:** N/A — sanity-check output against `docs/user-flow.md`.

### Phase 11 — Frontend: auth + event browsing (screens 1-3)
- `/login`, `/register`, `/events`, `/events/[eventId]`.
- **Agent:** `frontend`.
- **Verify:** compose + `pnpm dev`; manual walk register → login → list → detail; Vitest unit
  tests for the API client.

### Phase 12 — Frontend: seat selection, checkout, confirmation (screens 4-6, the centerpiece)
- `/events/[eventId]/seats` — seat map, hold click, countdown, disabled HELD/SOLD, 409 handling
  (demo the race live in two browser tabs).
- `/checkout/[bookingId]` — mock payment form, poll `GET /bookings/{id}` until terminal.
- `/checkout/[bookingId]/confirm` — CONFIRMED success view / CANCELLED-EXPIRED failure view with
  retry link.
- **Agents:** `frontend`; `figma-screen-design` if the seat-map/countdown UI needs more detail
  than Phase 1 already produced.
- **Verify:** Playwright E2E covering all four scenarios against the full compose stack — happy
  path, force-fail payment, short-TTL expiry, two-tab seat race. This suite is the definition of
  "done" for the vertical slice.

### Phase 13 — Frontend: my tickets (screen 7)
- `/tickets` — list confirmed bookings + client-side QR of bookingId. No new backend work (the
  list endpoint already exists from Phase 7).
- **Agent:** `frontend`.
- **Verify:** Playwright — after a happy-path checkout, `/tickets` shows the booking with QR.
- **At this point the full vertical slice from CLAUDE.md's "Scope for the demo" is complete,
  including all three edge paths, in the browser.**

### Phase 14 (optional, explicitly last) — Admin screens
- Per `user-flow.md`: "optional, build last if time allows." Only after Phase 13 is solid.
- Backend: `POST /venues`, `/events`, `/events/{id}/seat-categories`, ADMIN-gated.
- Frontend: `/admin/events`.
- **Agents:** `backend-service`, `gateway-resilience` (role-gated route), `frontend`,
  `figma-screen-design` (optional wireframe first).
- **Verify:** curl with ADMIN vs USER JWT (403 for the latter); browser walk creating an event and
  seeing it in `/events`.

## Cross-cutting notes

- Observability (OpenTelemetry → Elastic) is a stretch phase after Phase 13; structured JSON logs
  from day one in every `backend-service` phase cost little and help debug the saga.
- `docker-infra` gets re-invoked at the start of Phases 3, 5, 6, 7, 8, 9, 11 (and optionally 14)
  each time a new container needs adding — don't front-load all app containers in Phase 2.
- The concurrent-race, payment-fail, and timeout paths must exist as automated Testcontainers
  tests before Phase 12's Playwright pass, not discovered for the first time in the browser.
- Any deviation from this sequencing gets a short ADR via `workflow-rules` or
  `backend-architecture`, per CLAUDE.md's "every decision must be explainable" rule.
