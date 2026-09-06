# Screens & User Flow

> Read alongside `CLAUDE.md` and `business-rules.md`. Describes the client-facing screens, what each
> does, and which API calls it makes. Use this as the basis for frontend routes/components and for
> Figma if we design screens later. Keep it simple — this is a demo, not a production product.

## Screen list (Next.js routes)

| # | Screen                | Route                          | Purpose |
|---|------------------------|---------------------------------|---------|
| 1 | Login / Register      | `/login`, `/register`           | Auth in/out |
| 2 | Event list             | `/events`                       | Browse + filter events |
| 3 | Event detail           | `/events/[eventId]`             | Event info, seat categories, "select seats" CTA |
| 4 | Seat selection          | `/events/[eventId]/seats`       | Interactive seat map + hold countdown |
| 5 | Checkout / Payment     | `/checkout/[bookingId]`         | Mock payment form |
| 6 | Confirmation           | `/checkout/[bookingId]/confirm` | Success summary or failure/expired message |
| 7 | My tickets             | `/tickets`                      | List of confirmed bookings + QR |
| 8 | Admin: events (optional)| `/admin/events`                 | Create/edit venues & events |

## Screen details

**1. Login / Register**
- Fields: email, password (register also: confirm password).
- Calls: `POST /api/v1/auth/register`, `POST /api/v1/auth/login` → stores JWT (httpOnly cookie
  or in-memory + refresh strategy — keep it simple, no need for full refresh-token flow in the demo).
- On success → redirect to `/events`.

**2. Event list**
- Filters: city, date range, free-text search.
- Calls: `GET /api/v1/events?city=&from=&to=&q=`.
- Shows: title, venue, date, "from $X" (lowest seat category price), status badge.
- Only `ON_SALE` events are shown by default.

**3. Event detail**
- Calls: `GET /api/v1/events/{eventId}` (includes seat categories + pricing).
- Shows: description, venue, date/time, price tiers. CTA "Select seats" → screen 4.
- Disabled CTA if event is `SOLD_OUT` or `CLOSED`.

**4. Seat selection** — the centerpiece screen
- Calls: `GET /api/v1/events/{eventId}/seats` (seat map + availability status per seat).
- User clicks seats (up to 6) → on each click: `POST /api/v1/bookings/hold` with the seat id(s),
  which creates/updates a `PENDING` booking and starts the 10-minute countdown (shown on screen,
  driven by `expiresAt` from the response).
- Seats already `HELD`/`SOLD` are visually disabled.
- If the countdown hits zero client-side → show "hold expired", refetch seat map, clear selection.
- CTA "Proceed to payment" → `POST /api/v1/bookings/{bookingId}/checkout` → navigate to screen 5.

**5. Checkout / Payment**
- Shows booking summary (seats, total) fetched via `GET /api/v1/bookings/{bookingId}`.
- Mock card form (no real validation needed) → `POST /api/v1/payments` with `bookingId`.
- This call returns immediately (payment is processed async via Kafka); the frontend then polls
  `GET /api/v1/bookings/{bookingId}` (or subscribes via SSE/WebSocket if we add one later — polling
  is fine for the demo) until status is `CONFIRMED`, `CANCELLED`, or timeout.

**6. Confirmation**
- `CONFIRMED` → success view with seat list, total, "view my tickets" link.
- `CANCELLED`/`EXPIRED` → failure view with reason, "try again" → back to screen 4.

**7. My tickets**
- Calls: `GET /api/v1/bookings?userId=me&status=CONFIRMED`.
- Each booking shows seats, event info, and a generated QR (can be a client-side QR of the
  bookingId — no need for a real ticketing/QR backend service in the demo).

**8. Admin (optional, build last if time allows)**
- Create venue, create event, define seat categories + generate seats for a venue.
- Calls: `POST /api/v1/venues`, `POST /api/v1/events`, `POST /api/v1/events/{id}/seat-categories`.
- Gated by `ADMIN` role from the JWT.

## End-to-end flow (happy path)

```
Register/Login
   → Browse events (list → detail)
   → Select seats (hold starts, 10 min countdown)
   → Checkout (submit mock payment)
   → [async] booking saga: PaymentRequested → PaymentCompleted → BookingConfirmed
   → Confirmation screen shows CONFIRMED
   → My tickets shows the booking with QR
```

## Failure / edge paths to demo deliberately

- **Payment fails:** checkout → payment mock returns `FAILED` → booking `CANCELLED`, seats released
  → confirmation screen shows failure → user can retry seat selection.
- **Hold expires:** user selects seats, waits past 10 minutes without paying → scheduled sweep
  cancels the booking → confirmation/seat screen reflects `EXPIRED` → seats become available again.
- **Race for the same seat:** two sessions try to hold the same seat — second one gets 409
  immediately (not after payment) — good to show the Redis lock working live in a demo.

These three paths (fail, expire, race) are the ones worth rehearsing for the interview — they
prove the saga and concurrency handling actually work, not just the happy path.
