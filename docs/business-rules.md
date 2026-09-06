# Business Rules & Domain Model

> Read alongside the root `CLAUDE.md`. This file details entities and business rules per service.
> Claude Code should treat these rules as authoritative — implement validation/state transitions
> exactly as described, and flag in an ADR if a rule needs to change.

## auth

**Entities:** `User`, `Role`

- `User`: id, email (unique), passwordHash (BCrypt), createdAt.
- `Role`: `USER`, `ADMIN` (many-to-many via `user_roles`).
- Email must be unique; registration fails with 409 if it's taken.
- Password: min 8 chars, hashed with BCrypt, never returned in any response.
- JWT issued on login, expires in 1 hour. Claims: `sub` (userId), `email`, `roles`.
- Only `ADMIN` may create/update venues and events (enforced at the gateway or via `@PreAuthorize`).

## event (catalog)

**Entities:** `Venue`, `Event`, `SeatCategory`, `Seat`

- `Venue`: id, name, address, city.
- `Event`: id, venueId, title, description, startsAt, status.
  - Status lifecycle: `DRAFT → ON_SALE → SOLD_OUT` or `DRAFT → ON_SALE → CLOSED`.
  - Only an event in `ON_SALE` **and** with `startsAt` in the future can be booked.
- `SeatCategory`: id, eventId, name (e.g. "VIP", "Standard"), price.
- `Seat`: id, venueId, section, row, number — static layout, belongs to the venue, not the event.
  - A seat's *sellability for a given event* (available/held/sold) is **not** stored here — see booking.
- event never mutates seat sale state. It is read-heavy: browsing, filtering, seat map layout.

**Endpoint — seat map layout (no availability):**
- `GET /api/v1/events/{eventId}/seats` — returns the static seat map for the event: each seat's
  `seatId`, `section`, `row`, `number`, and its `seatCategory` (name + price). This response
  **never** includes an availability/sold/held flag — per ADR-0001, event has no knowledge of
  `AVAILABLE`/`HELD`/`SOLD` state. See "Seat map contract (frontend composition)" under the
  `booking` section below for how this is combined with live availability.

## booking (core)

**Entities:** `Booking`, `BookingItem`, `SeatAvailability`, `SagaState`

- `Booking`: id, userId, eventId, status, total, createdAt, expiresAt.
  - Status lifecycle: `PENDING → CONFIRMED`, `PENDING → CANCELLED`, `PENDING → EXPIRED`.
- `BookingItem`: id, bookingId, seatId, price (snapshot at time of hold, not looked up later).
- `SeatAvailability`: eventId, seatId, status (`AVAILABLE`, `HELD`, `SOLD`) — the source of truth
  for whether a seat can be picked. Mirrors Redis holds but persisted for consistency/audit.
- `SagaState`: bookingId, step, status — tracks where a booking is in the checkout saga, so a crash
  mid-saga can be recovered/replayed instead of leaving a booking stuck.

**Rules:**
- Max 6 seats per booking.
- A seat can be held by only one booking at a time (Redis distributed lock on `eventId:seatId`).
- Hold TTL: 10 minutes. A scheduled sweep expires bookings whose `expiresAt` has passed and releases
  their seats (sets `SeatAvailability` back to `AVAILABLE`, publishes `BookingCancelled`).
- A seat already `SOLD` or currently `HELD` by another booking cannot be selected — reject with 409.
- `total` = sum of `BookingItem.price` at hold time; not recalculated from current category price.
- Booking cannot move to `CONFIRMED` without a `PaymentCompleted` event for that bookingId.

**Endpoint — live availability (no layout):**
- `GET /api/v1/bookings/availability?eventId={eventId}` — returns, for every seat currently known
  to booking for that event, `{ seatId, status }` where `status` is `AVAILABLE`, `HELD`, or `SOLD`
  (from `SeatAvailability`). This response carries **no** section/row/number/price — booking has
  no opinion on layout or pricing, only sale state. Seats with no `SeatAvailability` row yet (never
  held or sold) are implicitly `AVAILABLE` and may be omitted from the response; the frontend
  should treat any `seatId` from the event layout call that is absent from this response as
  `AVAILABLE`.

**Seat map contract (frontend composition — resolves the event/booking split from ADR-0001):**
- The seat-selection screen requires **two separate client-side calls**, not one combined
  endpoint:
  1. `GET /api/v1/events/{eventId}/seats` (event service, via gateway) → layout + price tiers.
  2. `GET /api/v1/bookings/availability?eventId={eventId}` (booking service, via gateway) → status
     per `seatId`.
- The frontend merges the two responses client-side by `seatId` to render the seat map (layout +
  color-coded availability). This is a deliberate latency/complexity tradeoff for clean service
  boundaries (ADR-0001, Consequences) — do **not** introduce a gateway-composed/aggregated
  endpoint that merges these server-side; the gateway only routes, validates JWTs, and applies
  Resilience4j per root `CLAUDE.md`, it does not own composition/business logic. Both calls poll
  independently if the frontend needs to refresh availability (e.g. after a 409) without
  re-fetching the static layout.

## payment (mock)

**Entity:** `Payment`

- `Payment`: id, bookingId (unique — one payment attempt per booking), amount, status
  (`PENDING`, `COMPLETED`, `FAILED`), providerRef, createdAt.
- Mock rule (for demoing both paths): simulate success by default; simulate failure when the
  amount exceeds a configurable threshold, or via a "force fail" test flag on the request —
  this lets a demo trigger the failure/rollback path deliberately, not just by luck.
- On completion, publish `PaymentCompleted`; on failure, publish `PaymentFailed`. Never call
  booking synchronously — always via `payment.events`.
- **No inbound REST endpoint on payment, by design.** There is exactly **one** trigger for the
  entire checkout saga: `POST /api/v1/bookings/{id}/checkout` on the **booking** service (see
  "Checkout workflow" step 2 below). That single call commits `SagaState` and publishes
  `PaymentRequested`; payment only ever reacts to `payment.commands`. There is no
  `POST /api/v1/payments` endpoint, and the frontend must never call payment directly — it isn't
  reachable via the gateway's service routing for client calls, and adding one would contradict
  root `CLAUDE.md`'s service responsibility ("payment ... Consumes a payment command ... No inbound
  REST") and the "services never call each other's REST directly" communication rule.
- **The mock payment form on the checkout screen is cosmetic UX only.** Because this is a mock
  provider with no real PSP integration, there is nothing for a card-entry step to submit to on the
  backend. The card form's "Pay now" button does not call a payment API — it simply confirms intent
  and (if the checkout call hasn't already fired from the seat-selection screen) triggers
  `POST /api/v1/bookings/{id}/checkout`, then the screen switches to polling
  `GET /api/v1/bookings/{id}` for the saga's async result. Do not add a second backend call here;
  do not add a `createPayment()`/`paymentApi.ts` client function that calls payment — the frontend's
  "checkout" API surface is entirely `bookingApi.ts` (`holdSeats()`, `createCheckout()`,
  `fetchBooking()`).

## notification (pure consumer)

**Entity:** `Notification`

- `Notification`: id, type (`BOOKING_CONFIRMED`, `BOOKING_CANCELLED`), recipient, channel
  (`EMAIL`, `SMS` — mock, just logged), status (`SENT`, `FAILED`), payload, createdAt.
- Consumes `booking.events` only. No inbound REST endpoints besides a health check.
- `BookingConfirmed` → log a "confirmation + tickets" notification.
- `BookingCancelled` → log a "cancellation" notification.
- Idempotent: dedupe on event id so a redelivered Kafka message doesn't double-notify.

## Checkout workflow (step-by-step)

> Formalizes the saga described in root `CLAUDE.md` ("Kafka topics & the checkout saga") into an
> explicit, unambiguous step sequence. Booking is the saga orchestrator throughout — it is the only
> service that transitions `Booking.status` and `SeatAvailability.status`. Every step below that
> reads "booking publishes X" implies: commit the local DB change first, then publish (outbox
> pattern), per CLAUDE.md's non-negotiables. Every consumer step implies: dedupe on event id before
> acting (idempotent), and route to a dead-letter topic on repeated processing failure.

### Happy path

| # | Actor | Trigger | Action | Kafka topic (produce/consume) | Resulting state change |
|---|-------|---------|--------|-------------------------------|-------------------------|
| 1 | booking | User submits seat selection (`POST /bookings/hold`) | For each requested seat: acquire Redis distributed lock `eventId:seatId`; if lock acquired and `SeatAvailability` is `AVAILABLE`, create/attach `BookingItem` with price snapshot, set `SeatAvailability` → `HELD`, set Redis key TTL = 10 min; release lock. If any seat fails this check, reject the whole hold with 409 (see "Concurrent seat-race" below for the two-holder case). | — (no Kafka yet) | New `Booking` row, status `PENDING`, `expiresAt` = now + 10 min. Up to 6 `SeatAvailability` rows → `HELD`. |
| 2 | booking | User clicks "proceed to checkout" (`POST /bookings/{id}/checkout`) — the **single, sole trigger** for the saga; there is no separate payment-form submission that calls a backend endpoint (see "payment (mock)" section's note on the cosmetic checkout form) — while `Booking.status = PENDING` and `expiresAt` has not passed | Commit `SagaState(bookingId, step=PAYMENT_REQUESTED, status=IN_PROGRESS)`; publish `PaymentRequested` | produce → `payment.commands` | `SagaState` recorded; `Booking.status` stays `PENDING`. |
| 3 | payment | Consumes `PaymentRequested` | Create `Payment` row (`status=PENDING`), run mock rule (default success; force-fail flag or amount-threshold triggers failure), update `Payment.status` to `COMPLETED` or `FAILED`, commit, then publish result | consume ← `payment.commands`; produce → `payment.events` (`PaymentCompleted` or `PaymentFailed`) | `Payment.status` → `COMPLETED` or `FAILED`. |
| 4a | booking | Consumes `PaymentCompleted` for a `bookingId` still `PENDING` | Set each held seat's `SeatAvailability` → `SOLD`; set `Booking.status` → `CONFIRMED`; update `SagaState` step=`CONFIRMED`, status=`DONE`; commit, then publish | consume ← `payment.events`; produce → `booking.events` (`BookingConfirmed`) | `SeatAvailability` → `SOLD`; `Booking.status` → `CONFIRMED`. |
| 5 | notification | Consumes `BookingConfirmed` | Write `Notification` row (`type=BOOKING_CONFIRMED`, `status=SENT`) | consume ← `booking.events` | New `Notification` row. |

### Edge path (a): payment fails

| # | Actor | Trigger | Action | Kafka topic | Resulting state change |
|---|-------|---------|--------|-------------|--------------------------|
| 1-3 | (same as happy path steps 1-3, but the mock rule triggers `FAILED`) | | | | `Payment.status` → `FAILED`. |
| 4b | booking | Consumes `PaymentFailed` for a `bookingId` still `PENDING` | Release Redis locks/keys for this booking's seats; set each seat's `SeatAvailability` → `AVAILABLE`; set `Booking.status` → `CANCELLED`; update `SagaState` step=`CANCELLED`, status=`DONE`; commit, then publish | consume ← `payment.events`; produce → `booking.events` (`BookingCancelled`) | `SeatAvailability` → `AVAILABLE`; `Booking.status` → `CANCELLED`. |
| 5 | notification | Consumes `BookingCancelled` | Write `Notification` row (`type=BOOKING_CANCELLED`, `status=SENT`) | consume ← `booking.events` | New `Notification` row. |

### Edge path (b): hold expires (timeout sweep)

| # | Actor | Trigger | Action | Kafka topic | Resulting state change |
|---|-------|---------|--------|-------------|--------------------------|
| 1 | booking | Scheduled sweep job runs (fixed interval, e.g. every 30s) and finds a `Booking` with `status = PENDING` and `expiresAt < now`, **with no terminal `SagaState`** (i.e. checkout was never started, or `PaymentRequested` was sent but no `PaymentCompleted`/`PaymentFailed` ever arrived) | Release Redis locks/keys for this booking's seats; set each seat's `SeatAvailability` → `AVAILABLE`; set `Booking.status` → `EXPIRED`; update `SagaState` step=`EXPIRED`, status=`DONE`; commit, then publish | produce → `booking.events` (`BookingCancelled`) | `SeatAvailability` → `AVAILABLE`; `Booking.status` → `EXPIRED`. |
| 2 | notification | Consumes `BookingCancelled` | Write `Notification` row (`type=BOOKING_CANCELLED`, `status=SENT`) | consume ← `booking.events` | New `Notification` row. |

Notes:
- The sweep and the payment-result consumer both race to terminate the same `PENDING` booking.
  Booking must guard the transition with an atomic conditional update (e.g.
  `UPDATE bookings SET status=... WHERE id=? AND status='PENDING'`, or a check against
  `SagaState.status != DONE`) so only one of the two paths wins; the loser is a no-op, not an
  error, since the outcome (seats released, booking terminal) is already correct.
- The sweep publishes `BookingCancelled`, not a separate `BookingExpired` event — `booking.events`
  only defines `BookingConfirmed`/`BookingCancelled` per CLAUDE.md's topic table; `EXPIRED` is a
  `Booking.status` value, not a distinct Kafka event type. (See "Flagged gaps" below.)

### Edge path (c): concurrent seat-race (two holds on the same seat)

| # | Actor | Trigger | Action | Kafka topic | Resulting state change |
|---|-------|---------|--------|-------------|--------------------------|
| 1 | booking | Two `POST /bookings/hold` requests for the same `eventId:seatId` arrive concurrently (User A, User B) | Both requests attempt to acquire the Redis distributed lock `eventId:seatId`. Only one (say, User A) acquires it. | — | — |
| 2 | booking | User A holds the lock | Check `SeatAvailability` for the seat: if `AVAILABLE`, set to `HELD`, attach to User A's `Booking`, release lock | — | `SeatAvailability` → `HELD` (User A's booking). |
| 3 | booking | User B's request, waiting on the lock (or failing to acquire it immediately, per the Redis client's lock-acquisition semantics) | Once the lock is available, User B's request checks `SeatAvailability` for the seat and finds it `HELD` (not `AVAILABLE`) | — | No change. Reject User B's hold request for this seat with `409 Conflict`. |
| 4 | booking | User B's overall hold request | If User B requested multiple seats and only some are unavailable, the entire hold request fails atomically (no partial holds) — release any Redis locks/`SeatAvailability` flips already made for User B's other seats in this same request, respond `409` naming the conflicting seat(s) | — | Any tentative `HELD` states from User B's same request are rolled back to `AVAILABLE`. |

Notes:
- This path never touches Kafka — it is resolved entirely within booking's `POST /bookings/hold`
  request/response cycle via the Redis lock, before any saga step begins. This matches
  `docs/roadmap.md` Phase 7, which builds and proves this path before the saga (Phase 8) exists.
- The Redis lock is the single source of truth for "who gets to check-and-set first"; the
  `SeatAvailability` Postgres row is the durable record of the outcome. Do not skip the lock and
  rely on a DB unique constraint alone — the lock exists precisely so the losing request can be
  told "unavailable" fast, without a DB round-trip race.

## Flagged gaps / open questions (for the requester, not silently resolved)

- **`EXPIRED` booking status has no distinct Kafka event.** `business-rules.md`'s own status
  lifecycle for `Booking` lists `PENDING → EXPIRED` as a valid transition, and CLAUDE.md's saga
  step 5 says the sweep "publishes `BookingCancelled`" — but CLAUDE.md's topic table only defines
  `BookingConfirmed`/`BookingCancelled` as the two `booking.events` payloads, with no
  `BookingExpired`. This document assumes the sweep reuses `BookingCancelled` (same notification
  behavior for both `CANCELLED` and `EXPIRED`) and that `EXPIRED` vs `CANCELLED` is only visible
  via `Booking.status`, not via a distinct event type. If `EXPIRED` should be a first-class event
  for the frontend/notification to distinguish "you didn't pay in time" from "your payment failed,"
  that needs a decision (either a new `BookingExpired` event or an event payload field
  distinguishing the reason) before `saga-orchestrator` builds the sweep.
- **DLQ topic naming/convention is undefined.** CLAUDE.md and this document both require "every
  consumer needs a dead-letter path," but neither names a DLQ topic convention (e.g.
  `<topic>.dlq`) or retry-count-before-DLQ policy. Left to `message-broker` to define, flagged here
  so it isn't silently invented as a business rule.
- **Lock-acquisition wait/timeout semantics are unspecified.** Step 3 of the seat-race path above
  says User B's request "waits on the lock (or fails to acquire it immediately, per the Redis
  client's lock-acquisition semantics)" — this document does not mandate blocking-wait-with-timeout
  vs. fail-fast, since that's an implementation choice for `saga-orchestrator`/`backend-service`,
  not a business rule. Whichever is chosen, the observable business outcome (one 200, one 409) must
  hold.

## Cross-cutting rules

- All monetary amounts: store as integer minor units (cents) or `BigDecimal`, never `float`/`double`.
- All timestamps: UTC, ISO-8601.
- Every state transition above should be enforced in the domain layer, not just the DB (don't rely
  on constraints alone to block illegal transitions — return a clear 409/422 with a reason).
