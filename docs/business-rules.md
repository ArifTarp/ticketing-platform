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

## payment (mock)

**Entity:** `Payment`

- `Payment`: id, bookingId (unique — one payment attempt per booking), amount, status
  (`PENDING`, `COMPLETED`, `FAILED`), providerRef, createdAt.
- Mock rule (for demoing both paths): simulate success by default; simulate failure when the
  amount exceeds a configurable threshold, or via a "force fail" test flag on the request —
  this lets a demo trigger the failure/rollback path deliberately, not just by luck.
- On completion, publish `PaymentCompleted`; on failure, publish `PaymentFailed`. Never call
  booking synchronously — always via `payment.events`.

## notification (pure consumer)

**Entity:** `Notification`

- `Notification`: id, type (`BOOKING_CONFIRMED`, `BOOKING_CANCELLED`), recipient, channel
  (`EMAIL`, `SMS` — mock, just logged), status (`SENT`, `FAILED`), payload, createdAt.
- Consumes `booking.events` only. No inbound REST endpoints besides a health check.
- `BookingConfirmed` → log a "confirmation + tickets" notification.
- `BookingCancelled` → log a "cancellation" notification.
- Idempotent: dedupe on event id so a redelivered Kafka message doesn't double-notify.

## Cross-cutting rules

- All monetary amounts: store as integer minor units (cents) or `BigDecimal`, never `float`/`double`.
- All timestamps: UTC, ISO-8601.
- Every state transition above should be enforced in the domain layer, not just the DB (don't rely
  on constraints alone to block illegal transitions — return a clear 409/422 with a reason).
