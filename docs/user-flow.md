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

## Wireframes & component breakdown (7 core screens)

> Wireframe-level detail for the 7 core screens listed above. Screen 8 (Admin) is skipped here —
> it's explicitly optional/last per this doc. Layouts are simple ASCII/box sketches, top to bottom.
> Component names follow the frontend naming convention in the root `CLAUDE.md` (PascalCase
> components, `use*` hooks, camelCase API client functions). Visual/data states mirror
> `business-rules.md` state machines exactly — no invented states. A rendered mockup of screen 4
> (the centerpiece) is linked at the end of this section.

### 1. Login / Register — `/login`, `/register`

**Layout**
```
┌───────────────────────────────┐
│           AppLogo              │
│  ┌───────────────────────────┐ │
│  │ Tab: Login | Register     │ │
│  ├───────────────────────────┤ │
│  │ Email        [__________] │ │
│  │ Password     [__________] │ │
│  │ (Register only)            │ │
│  │ Confirm pw   [__________] │ │
│  │                            │ │
│  │ [ FormError banner ]       │ │
│  │ [   Submit button   ]      │ │
│  └───────────────────────────┘ │
│  Switch-mode link (↔)          │
└───────────────────────────────┘
```

**Components**
- `AuthLayout` — centered card shell, shared by both routes.
- `LoginForm` / `RegisterForm` — separate components (different fields/validation), not one form
  branching on a flag.
- `FormError` — generic inline error banner (used for 401 on login, 409 on register).
- `PasswordInput` — shared input with show/hide toggle.

**States**
- Empty (initial): fields blank, submit disabled until required fields are filled.
- Loading: submit button shows spinner, inputs disabled, calls `POST /api/v1/auth/login` or
  `POST /api/v1/auth/register`.
- Error — invalid credentials: 401 → `FormError` "Invalid email or password."
- Error — email taken (register only): 409 → `FormError` "An account with this email already
  exists." (matches `auth` service rule: registration fails 409 on duplicate email).
- Success: JWT stored → redirect to `/events`.

**Interaction flow**
1. User lands on `/login` → `LoginForm` renders empty, submit disabled.
2. User fills email + password, clicks "Log in" → button enters loading state → `POST
   /api/v1/auth/login`.
3. 200 → JWT stored → redirect to `/events`.
4. 401 → `FormError` shown, inputs re-enabled, password field cleared.
5. User clicks "Register instead" → switches to `RegisterForm` (no navigation, same route group).
6. User submits register form with an email already in use → 409 → `FormError` "email already
   exists" (per `auth` business rule) → user corrects email and resubmits.

---

### 2. Event list — `/events`

**Layout**
```
┌────────────────────────────────────────┐
│ NavBar                                   │
├────────────────────────────────────────┤
│ EventFilterBar: [city] [date from-to] [search] │
├────────────────────────────────────────┤
│ EventCard  EventCard  EventCard          │
│ EventCard  EventCard  EventCard          │  (grid)
│ EventCard  EventCard  EventCard          │
├────────────────────────────────────────┤
│ Pagination / "load more"                 │
└────────────────────────────────────────┘
```

**Components**
- `EventFilterBar` — city select, date-range picker, free-text search input; debounced.
- `EventList` — grid/list container, owns fetch + empty/loading/error state.
- `EventCard` — title, venue, date, "from $X" (lowest seat category price), status badge.
- `EmptyState` — shared "no results" component (reused on other list screens like My tickets).

**States**
- Loading (initial + on filter change): skeleton `EventCard` placeholders.
- Empty: no events match filters → `EmptyState` "No events found — try different filters."
- Error: fetch failure → `EmptyState`-style banner with "retry" action.
- Loaded: grid of `EventCard`s. Only `ON_SALE` events shown by default (per `event` status
  lifecycle `DRAFT → ON_SALE → SOLD_OUT`/`CLOSED`) — filter bar has no toggle to show non-`ON_SALE`
  events in the demo scope.

**Interaction flow**
1. User lands on `/events` → `EventList` fetches `GET /api/v1/events` with default filters →
   loading skeletons shown.
2. Results return → grid of `EventCard`s renders (or `EmptyState` if none).
3. User types in search / picks a city / sets a date range → `EventFilterBar` debounces →
   re-fetches `GET /api/v1/events?city=&from=&to=&q=` → list re-renders (loading skeleton shown
   briefly during re-fetch).
4. User clicks an `EventCard` → navigates to `/events/[eventId]`.

---

### 3. Event detail — `/events/[eventId]`

**Layout**
```
┌────────────────────────────────────────┐
│ NavBar                                   │
├────────────────────────────────────────┤
│ EventHeader: title, venue, date/time, status badge │
├───────────────────────┬────────────────┤
│ Description            │ PriceTierList  │
│                         │  - VIP  $120   │
│                         │  - Std  $60    │
│                         │                │
│                         │ [Select seats] │
└───────────────────────┴────────────────┘
```

**Components**
- `EventHeader` — title, venue, date/time, `EventStatusBadge`.
- `PriceTierList` — one row per `SeatCategory` (name + price).
- `EventStatusBadge` — visual badge for `ON_SALE` / `SOLD_OUT` / `CLOSED` (mirrors `event` status
  lifecycle exactly; `DRAFT` is never shown to end users).
- CTA button (`SelectSeatsButton` or inline in `EventHeader`) — primary action to screen 4.

**States**
- Loading: skeleton for header + price list.
- Error / not found: 404 → `EmptyState` "Event not found" with link back to `/events`.
- `ON_SALE` and `startsAt` in the future → CTA enabled.
- `SOLD_OUT` or `CLOSED`, or `startsAt` in the past → CTA disabled, badge reflects reason (per
  booking rule: "only an event `ON_SALE` **and** with `startsAt` in the future can be booked").

**Interaction flow**
1. User navigates from `EventCard` click → `GET /api/v1/events/{eventId}` fires → loading skeleton.
2. Loaded → `EventHeader` + `PriceTierList` render.
3. If status is `ON_SALE` and event is upcoming → user clicks "Select seats" → navigates to
   `/events/[eventId]/seats`.
4. If status is `SOLD_OUT`/`CLOSED` → CTA is disabled (not hidden) with a tooltip/badge explaining
   why — no navigation possible.

---

### 4. Seat selection — `/events/[eventId]/seats` (centerpiece)

**Layout**
```
┌──────────────────────────────────────────────────────────┐
│ NavBar                                                     │
├──────────────────────────────────────────────────────────┤
│ EventSummaryBar (title, venue)          [ CountdownTimer ] │
├───────────────────────────────────┬────────────────────────┤
│              SeatMap                │   SelectionSummary     │
│   [STAGE]                           │   - Seat A3   $120     │
│   Section A: rows of Seat squares   │   - Seat A4   $120     │
│   Section B: rows of Seat squares   │   Total: $240          │
│                                      │   [Proceed to payment] │
│   SeatMapLegend (Available/         │   RaceConflictToast     │
│   Selected/Held/Sold)               │   (transient, on 409)   │
└───────────────────────────────────┴────────────────────────┘
```

**Components**
- `SeatMap` — renders sections/rows/`Seat`s from `GET /api/v1/events/{eventId}/seats`.
- `Seat` — single seat square; visual variant is a pure function of `SeatAvailability` status.
- `SeatMapLegend` — static legend for the four visual states below.
- `CountdownTimer` — hold TTL countdown, driven by `expiresAt` from the hold response.
- `SelectionSummary` — selected seats, running total (via `useSeatSelection`), CTA.
- `RaceConflictToast` — transient toast for a 409 seat-race response.
- `HoldExpiredModal` — blocking modal shown when the countdown hits zero.
- Hook: `useSeatSelection` — holds selected seat ids + total, calls `holdSeats()`.
- Hook: `useCountdown` — derives mm:ss from `expiresAt`, exposes a `isExpiring` (≤2 min) flag.
- API client: `fetchSeatMap()`, `holdSeats()`, `createCheckout()` in `bookingApi.ts`.

**Visual states (must match `SeatAvailability` exactly — no extra states)**
- `AVAILABLE` — outlined, clickable.
- Selected (client-only, this session's `HELD` seats) — filled accent, part of the current
  `PENDING` booking.
- `HELD` (by another session) — filled neutral/disabled, not clickable.
- `SOLD` — filled dark/disabled, not clickable, permanent.

**Screen-level states**
- Loading: skeleton seat grid while `GET /api/v1/events/{eventId}/seats` is in flight.
- Empty/error: fetch failure → retry banner (no seats to render).
- Normal: seats as above, countdown running (`< 2min` → `CountdownTimer` switches to a warning
  visual treatment).
- Hold expired: countdown hits `00:00` → `HoldExpiredModal` shown, seat map refetched, selection
  cleared, booking is `EXPIRED` (booking status lifecycle `PENDING → EXPIRED`).
- Seat race (409): user's `holdSeats()` call for a seat that was just taken by another session
  returns 409 → `RaceConflictToast` shown inline, seat map refetches so the seat now shows `HELD`,
  the rest of the user's held seats/countdown are unaffected.
- Max seats: 6th seat selected → 7th click is blocked client-side with an inline message ("max 6
  seats per booking" per booking rule) before ever calling the API.

**Interaction flow**
1. User arrives from screen 3 → `GET /api/v1/events/{eventId}/seats` → `SeatMap` renders seats by
   status; no countdown yet (no hold placed).
2. User clicks an `AVAILABLE` seat → `holdSeats([seatId])` → 200 → seat turns "selected", `Booking`
   becomes/stays `PENDING`, `CountdownTimer` starts/refreshes from `expiresAt`.
3. User clicks a `HELD` or `SOLD` seat → no-op (not clickable, cursor disabled).
4. User selects a seat that another session grabbed a moment earlier → `holdSeats()` returns 409 →
   `RaceConflictToast` appears, `SeatMap` refetches, that seat now renders `HELD`.
5. User selects a 7th seat → blocked client-side, inline "max 6 seats" message, no API call.
6. Countdown reaches ≤2 minutes → `CountdownTimer` switches to warning styling (still ticking).
7. Countdown reaches `00:00` before checkout → `HoldExpiredModal` shown ("your hold expired") →
   on dismiss, seat map refetches (expired seats now `AVAILABLE` again), selection/total cleared.
8. User clicks "Proceed to payment" (enabled once ≥1 seat held) → `POST
   /api/v1/bookings/{bookingId}/checkout` → navigate to `/checkout/[bookingId]`.

---

### 5. Checkout / Payment — `/checkout/[bookingId]`

**Layout**
```
┌────────────────────────────────────────┐
│ NavBar                                   │
├───────────────────────┬────────────────┤
│ PaymentForm             │ BookingSummary │
│  Card number  [______]  │  Seats list    │
│  Expiry [__] CVC [__]   │  Total: $240   │
│  [Pay now]               │  CountdownTimer│
│                          │ (still ticking)│
└───────────────────────┴────────────────┘
        ↓ (after submit)
┌────────────────────────────────────────┐
│ PaymentProcessingOverlay (spinner,       │
│ "confirming your payment…")              │
└────────────────────────────────────────┘
```

**Components**
- `BookingSummary` — seats + total fetched from `GET /api/v1/bookings/{bookingId}`; reuses
  `CountdownTimer` since the hold is still live until payment resolves.
- `PaymentForm` — mock card fields (no real validation needed per the doc's scope).
- `PaymentProcessingOverlay` — shown while polling for the async saga result.
- Hook: `useBookingPolling(bookingId)` — polls `GET /api/v1/bookings/{bookingId}` until status is
  `CONFIRMED`, `CANCELLED`, or a client-side poll-timeout.
- API client: `createPayment()`, `fetchBooking()` in `bookingApi.ts` / `paymentApi.ts`.

**States**
- Loading: `BookingSummary` skeleton while `GET /api/v1/bookings/{bookingId}` loads.
- Error — booking not `PENDING` (e.g. already expired before checkout even opened): redirect back
  to seat selection with a message, since only `PENDING` bookings can be paid.
- Submitting: `PaymentForm` disabled, `POST /api/v1/payments` in flight.
- Processing (post-submit, pre-saga-result): `PaymentProcessingOverlay` shown, `useBookingPolling`
  active, countdown still visible/ticking (hold hasn't been consumed yet).
- Resolved: polling detects `CONFIRMED` or `CANCELLED` → navigate to
  `/checkout/[bookingId]/confirm`.
- Hold expires mid-checkout (sweep fires before payment result arrives): polling sees status flip
  to `EXPIRED`/`CANCELLED` → navigate to confirmation screen's failure view.

**Interaction flow**
1. User arrives from screen 4 → `GET /api/v1/bookings/{bookingId}` → `BookingSummary` renders
   seats/total, countdown continues from where screen 4 left it.
2. User fills mock card fields, clicks "Pay now" → `PaymentForm` disabled → `POST /api/v1/payments`
   with `bookingId` → call returns immediately (202-style ack; real result comes via the saga).
3. `PaymentProcessingOverlay` shown, `useBookingPolling` starts polling `GET
   /api/v1/bookings/{bookingId}`.
4. Saga resolves `PaymentCompleted` → booking flips `PENDING → CONFIRMED` → poll detects it →
   navigate to `/checkout/[bookingId]/confirm` (success view).
5. Saga resolves `PaymentFailed` → booking flips `PENDING → CANCELLED`, seats released → poll
   detects it → navigate to confirmation (failure view).
6. Hold TTL sweep fires before payment resolves → booking flips `PENDING → EXPIRED` → poll detects
   it → navigate to confirmation (failure view, "hold expired" reason).

---

### 6. Confirmation — `/checkout/[bookingId]/confirm`

**Layout — success**
```
┌────────────────────────────────────────┐
│  ✓ BookingSuccessPanel                   │
│  Seats: A3, A4     Total: $240           │
│  [ View my tickets ]                     │
└────────────────────────────────────────┘
```

**Layout — failure**
```
┌────────────────────────────────────────┐
│  ✕ BookingFailurePanel                   │
│  Reason: Payment failed / Hold expired   │
│  [ Try again → back to seat selection ]  │
└────────────────────────────────────────┘
```

**Components**
- `BookingSuccessPanel` — seat list, total, "view my tickets" link. Shown only for `CONFIRMED`.
- `BookingFailurePanel` — reason text + retry CTA. Shown for `CANCELLED` or `EXPIRED`.
- Reused: none new beyond above; seat/total formatting reuses `SelectionSummary`'s row layout.

**States**
- `CONFIRMED` → `BookingSuccessPanel`.
- `CANCELLED` → `BookingFailurePanel`, reason "Payment failed" (maps to `PaymentFailed` saga
  outcome).
- `EXPIRED` → `BookingFailurePanel`, reason "Your hold expired before payment completed."
- No other booking status is rendered here — a `PENDING` booking should never reach this screen
  (only arrived at via the polling resolution in screen 5).

**Interaction flow**
1. Screen loads with the already-resolved `bookingId` (navigated here only after status left
   `PENDING`) → `GET /api/v1/bookings/{bookingId}` confirms final status.
2. `CONFIRMED` → `BookingSuccessPanel` renders → user clicks "View my tickets" → `/tickets`.
3. `CANCELLED`/`EXPIRED` → `BookingFailurePanel` renders the specific reason → user clicks "Try
   again" → navigates back to `/events/[eventId]/seats` to restart seat selection.

---

### 7. My tickets — `/tickets`

**Layout**
```
┌────────────────────────────────────────┐
│ NavBar                                   │
├────────────────────────────────────────┤
│ TicketCard: event, date, seats, QrCode   │
│ TicketCard: event, date, seats, QrCode   │
├────────────────────────────────────────┤
│ EmptyState (if none)                     │
└────────────────────────────────────────┘
```

**Components**
- `TicketList` — fetch + list container.
- `TicketCard` — event info, seat list, `QrCodeDisplay`.
- `QrCodeDisplay` — client-side-generated QR of the `bookingId` (no real ticketing/QR backend).
- `EmptyState` — reused from screen 2, "no confirmed bookings yet" + link to `/events`.

**States**
- Loading: skeleton `TicketCard`s.
- Empty: no `CONFIRMED` bookings for the user → `EmptyState` "No tickets yet — browse events."
- Error: fetch failure → retry banner.
- Loaded: one `TicketCard` per `CONFIRMED` booking. Only `CONFIRMED` bookings are shown —
  `PENDING`, `CANCELLED`, `EXPIRED` bookings never appear here.

**Interaction flow**
1. User navigates to `/tickets` (e.g. from nav or the confirmation screen) → `GET
   /api/v1/bookings?userId=me&status=CONFIRMED` → loading skeletons.
2. Results render as `TicketCard`s, each with a `QrCodeDisplay` generated client-side from the
   `bookingId`.
3. No confirmed bookings → `EmptyState` with a CTA back to `/events`.

---

### Seat selection mockup

A visual mockup of screen 4 (seat map, hold countdown, and the AVAILABLE/HELD/SOLD/selected visual
treatment) is published as a Claude Code Artifact:
https://claude.ai/code/artifact/38365fae-b8d2-4dfe-8f66-9febdd9eeca7

This is a static HTML wireframe mockup (built and published directly as an Artifact), **not** a
Claude Design canvas and **not** a native Figma file — no Figma MCP/API is connected in this
environment, and this environment's shell tooling wasn't available to run the Claude Design canvas
seeding script. Treat it as a visual reference for layout/states only, not an editable design
file.
