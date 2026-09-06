# ADR-0001: Seat availability state lives in the booking service, not event

## Status

Accepted

## Context

The platform is split into bounded-context services with one database per service and no
cross-service database access (per root `CLAUDE.md`'s communication rules). Two services touch
seats:

- **event** owns the *static* seat map — venue layout, sections, rows, seat numbers, price
  categories. This data changes rarely and is read-heavy (catalog browsing).
- **booking** owns the checkout flow — placing time-boxed holds on seats, running the payment
  saga, and confirming or releasing seats. This data changes constantly and under concurrency
  (many users racing for the same seat at the same time).

If seat *availability* (`AVAILABLE` / `HELD` / `SOLD`) were stored in event's database, booking
would need to write to event's database directly to place a hold or mark a seat sold — a direct
violation of the "no shared schema, no cross-service database writes" rule, and it would put
high-concurrency, transactional, lock-sensitive traffic (seat holds racing under load) inside the
same store as low-write, read-heavy catalog data. Alternatively, booking could call event
synchronously to mutate seat state, but that reintroduces a synchronous dependency on the hot path
of checkout and couples booking's availability transitions to event's deployment/availability.

## Decision

Seat *availability state* (`AVAILABLE`, `HELD`, `SOLD` per `eventId` + `seatId`) is owned and
persisted entirely by the **booking** service (`seat_availability` table, mirrored by short-lived
Redis holds with TTL for the distributed lock). The **event** service owns only the static seat
map and pricing; it never reads or writes availability, and it exposes seat layout without a
per-seat sold/held/available flag.

When the frontend or gateway needs a combined view (seat layout + current availability), that
composition happens at the client/gateway level by calling both services' read APIs — event never
receives a synchronous call from booking to check or mutate availability, and booking never reads
event's database directly.

## Consequences

- Seat mutation (hold, release, sell) stays inside a single service and a single database
  transaction/lock scope (Redis `eventId:seatId` lock + booking's own Postgres), avoiding
  distributed writes or two-phase commit across services.
- event remains a simple, cacheable, read-heavy catalog service with no concurrency concerns.
- Consumers of "seat state" (frontend seat map) must combine two API calls (event for layout,
  booking for availability) instead of one — an accepted latency/complexity tradeoff in exchange
  for clean service boundaries.
- If event's seat map changes (e.g. a seat is removed from a venue) after bookings already
  reference that seat, booking's `seat_availability`/`booking_items` rows become the historical
  record; event does not need to retroactively reconcile booking data, and booking does not need
  to validate against event's current layout beyond the initial hold request.
- Any future feature needing seat state and catalog data together (e.g. an admin report) must be
  built as a read composition (gateway aggregation or a dedicated read model), never a
  cross-service DB join.
