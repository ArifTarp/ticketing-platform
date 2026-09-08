# 0004. `POST /bookings/hold` appends to an existing PENDING booking instead of always creating a new one

## Status
Accepted

## Context

`docs/user-flow.md` (screen 4) describes seat selection as one `POST /api/v1/bookings/hold` call
per seat click, with each call expected to reuse the same in-progress `Booking` for that
`(userId, eventId)` pair. The Phase 7 implementation, `BookingHoldService.holdSeats`, always
creates a brand-new `Booking` on every call with no lookup for an existing `PENDING` booking to
append to. This forced Phase 12's frontend to batch all seat selections into a single hold call at
"proceed to payment" time instead of holding incrementally, which breaks the documented per-click
countdown/race-toast UX and delays real seat reservation (Redis lock + `HELD` status) until the
whole selection is finalized — undermining the seat-race demo path user-flow.md calls out as a
flagship behavior to rehearse.

## Decision

`BookingHoldService.holdSeats` looks up an existing `PENDING` `Booking` for the request's
`(userId, eventId)` before creating one. If found, it appends the newly Redis-locked seats as new
`BookingItem`s to that booking (recomputing `total`) instead of creating a second `Booking` row.
The max-6-seats rule is enforced cumulatively (existing items + this request's seats), not per
call.

## Consequences

- `docs/user-flow.md` stays accurate as originally written — no doc change needed.
- `BookingHoldService`'s persistence step needs a find-or-create branch; the max-6 check moves
  from request-level to booking-level validation.
- Existing `SeatHoldConcurrencyTest`/`BookingHoldControllerTest` fixtures that assume "one call =
  one new booking" need updating for the append case.
- No cross-service or bounded-context impact — the whole change is inside booking's own write path.
