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
| 8 | Admin: events          | `/admin/events`                 | Create/edit venues & events (full wireframe detail below; implementation stays last/optional per roadmap build order) |

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
- Calls two separate reads, merged client-side by `seatId` (per ADR-0001 — event never carries
  availability): `GET /api/v1/events/{eventId}/seats` (event service — static layout + price tier
  per seat, no availability) and `GET /api/v1/bookings/availability?eventId={eventId}` (booking
  service — `{seatId, status}` for `HELD`/`SOLD` seats; any `seatId` absent from the response is
  `AVAILABLE`).
- User clicks seats (up to 6) → on each click: `POST /api/v1/bookings/hold` with the seat id(s),
  which creates/updates a `PENDING` booking and starts the 10-minute countdown (shown on screen,
  driven by `expiresAt` from the response).
- Seats already `HELD`/`SOLD` are visually disabled.
- If the countdown hits zero client-side → show "hold expired", refetch seat map, clear selection.
- CTA "Proceed to payment" → `POST /api/v1/bookings/{bookingId}/checkout` → navigate to screen 5.

**5. Checkout / Payment**
- Shows booking summary (seats, total) fetched via `GET /api/v1/bookings/{bookingId}`.
- Mock card form is cosmetic UX only — client-side validation, no backend payment call (there is
  no `POST /api/v1/payments` endpoint; payment has no inbound REST). The one checkout trigger,
  `POST /api/v1/bookings/{bookingId}/checkout`, already fired from screen 4's "Proceed to payment".
- On "Pay now", the screen transitions straight into the processing overlay and polls
  `GET /api/v1/bookings/{bookingId}` (or subscribes via SSE/WebSocket if we add one later — polling
  is fine for the demo) until status is `CONFIRMED`, `CANCELLED`, or timeout.

**6. Confirmation**
- `CONFIRMED` → success view with seat list, total, "view my tickets" link.
- `CANCELLED`/`EXPIRED` → failure view with reason, "try again" → back to screen 4.

**7. My tickets**
- Calls: `GET /api/v1/bookings?userId=me&status=CONFIRMED`.
- Each booking shows seats, event info, and a generated QR (can be a client-side QR of the
  bookingId — no need for a real ticketing/QR backend service in the demo).

**8. Admin: events**
- Create venue, create event, define seat categories + generate seats for a venue.
- Calls: `POST /api/v1/venues`, `POST /api/v1/events`, `POST /api/v1/events/{id}/seat-categories`.
- Gated by `ADMIN` role from the JWT.
- Full wireframe-level detail below, same as the other 7 screens — the design is not deprioritized.
  Only the *implementation build order* stays last/optional per `roadmap.md` Phase 14 (build the
  core vertical slice first); that build-order decision is unchanged.

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

## Wireframes & component breakdown (all 8 screens)

> Wireframe-level detail for all 8 screens listed above, including Admin — Admin's *design* is a
> full citizen here, same depth as the other 7; only its *implementation* build order stays
> last/optional per `roadmap.md` Phase 14 (unchanged). Layouts are simple ASCII/box sketches, top
> to bottom. Component names follow the frontend naming convention in the root `CLAUDE.md`
> (PascalCase components, `use*` hooks, camelCase API client functions). Visual/data states mirror
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
- `SeatMap` — renders sections/rows/`Seat`s by merging two reads client-side: layout from
  `GET /api/v1/events/{eventId}/seats` (event service) and live status from
  `GET /api/v1/bookings/availability?eventId={eventId}` (booking service) — event's endpoint never
  carries an availability field, per ADR-0001.
- `Seat` — single seat square; visual variant is a pure function of `SeatAvailability` status.
- `SeatMapLegend` — static legend for the four visual states below.
- `CountdownTimer` — hold TTL countdown, driven by `expiresAt` from the hold response.
- `SelectionSummary` — selected seats, running total (via `useSeatSelection`), CTA.
- `RaceConflictToast` — transient toast for a 409 seat-race response.
- `HoldExpiredModal` — blocking modal shown when the countdown hits zero.
- Hook: `useSeatSelection` — holds selected seat ids + total, calls `holdSeats()`.
- Hook: `useCountdown` — derives mm:ss from `expiresAt`, exposes a `isExpiring` (≤2 min) flag.
- API client: `fetchEventSeats()` in `eventApi.ts` (layout only, no availability);
  `fetchSeatAvailability()`, `holdSeats()`, `createCheckout()` in `bookingApi.ts` —
  `useSeatSelection`/`SeatMap` call both and merge by `seatId`.

**Visual states (must match `SeatAvailability` exactly — no extra states)**
- `AVAILABLE` — outlined, clickable.
- Selected (client-only, this session's `HELD` seats) — filled accent, part of the current
  `PENDING` booking.
- `HELD` (by another session) — filled neutral/disabled, not clickable.
- `SOLD` — filled dark/disabled, not clickable, permanent.

**Screen-level states**
- Loading: skeleton seat grid while both `GET /api/v1/events/{eventId}/seats` and
  `GET /api/v1/bookings/availability?eventId={eventId}` are in flight.
- Empty/error: fetch failure → retry banner (no seats to render).
- Normal: seats as above, countdown running (`< 2min` → `CountdownTimer` switches to a warning
  visual treatment).
- Hold expired: countdown hits `00:00` → `HoldExpiredModal` shown, availability refetched (layout
  is static, no need to refetch it), selection cleared, booking is `EXPIRED` (booking status
  lifecycle `PENDING → EXPIRED`).
- Seat race (409): user's `holdSeats()` call for a seat that was just taken by another session
  returns 409 → `RaceConflictToast` shown inline, availability refetches so the seat now shows
  `HELD`, the rest of the user's held seats/countdown are unaffected.
- Max seats: 6th seat selected → 7th click is blocked client-side with an inline message ("max 6
  seats per booking" per booking rule) before ever calling the API.

**Interaction flow**
1. User arrives from screen 3 → `GET /api/v1/events/{eventId}/seats` (layout) and
   `GET /api/v1/bookings/availability?eventId={eventId}` (status) fire together → `SeatMap` merges
   both by `seatId` and renders seats by status; no countdown yet (no hold placed).
2. User clicks an `AVAILABLE` seat → `holdSeats([seatId])` → 200 → seat turns "selected", `Booking`
   becomes/stays `PENDING`, `CountdownTimer` starts/refreshes from `expiresAt`.
3. User clicks a `HELD` or `SOLD` seat → no-op (not clickable, cursor disabled).
4. User selects a seat that another session grabbed a moment earlier → `holdSeats()` returns 409 →
   `RaceConflictToast` appears, availability refetches, that seat now renders `HELD`.
5. User selects a 7th seat → blocked client-side, inline "max 6 seats" message, no API call.
6. Countdown reaches ≤2 minutes → `CountdownTimer` switches to warning styling (still ticking).
7. Countdown reaches `00:00` before checkout → `HoldExpiredModal` shown ("your hold expired") →
   on dismiss, availability refetches (expired seats now `AVAILABLE` again), selection/total
   cleared.
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
- `PaymentForm` — mock card fields, client-side validation only (cosmetic — there is no real PSP
  integration and no backend payment endpoint to submit to; per `business-rules.md` the checkout
  saga has exactly one trigger, `POST /api/v1/bookings/{bookingId}/checkout`, already fired from
  screen 4's "Proceed to payment").
- `PaymentProcessingOverlay` — shown while polling for the async saga result.
- Hook: `useBookingPolling(bookingId)` — polls `GET /api/v1/bookings/{bookingId}` until status is
  `CONFIRMED`, `CANCELLED`, `EXPIRED`, or a client-side poll-timeout.
- API client: `fetchBooking()` in `bookingApi.ts` (no payment-specific client — there is no
  `paymentApi.ts` and no `createPayment()`; payment has no inbound REST endpoint).

**States**
- Loading: `BookingSummary` skeleton while `GET /api/v1/bookings/{bookingId}` loads.
- Error — booking not `PENDING` (e.g. already expired before checkout even opened): redirect back
  to seat selection with a message, since only `PENDING` bookings can be paid.
- Submitting: `PaymentForm` runs client-side field validation only (cosmetic — no backend call);
  on success it disables the form and transitions straight to Processing.
- Processing (post-submit, pre-saga-result): `PaymentProcessingOverlay` shown, `useBookingPolling`
  active, countdown still visible/ticking (hold hasn't been consumed yet).
- Resolved: polling detects `CONFIRMED`, `CANCELLED`, or `EXPIRED` → navigate to
  `/checkout/[bookingId]/confirm`. `EXPIRED` specifically means the hold TTL sweep fired before the
  payment result arrived (mid-checkout hold expiry) — the poll still detects it and routes to the
  confirmation screen's failure view like `CANCELLED` does.

**Interaction flow**
1. User arrives from screen 4 → `GET /api/v1/bookings/{bookingId}` → `BookingSummary` renders
   seats/total, countdown continues from where screen 4 left it. The checkout saga trigger
   (`POST /api/v1/bookings/{bookingId}/checkout`) has already fired from screen 4's "Proceed to
   payment" CTA, committing `SagaState` and publishing `PaymentRequested`.
2. User fills mock card fields, clicks "Pay now" → `PaymentForm` runs client-side validation only
   (cosmetic; no backend payment endpoint exists to call) → form disables → screen transitions
   directly into `PaymentProcessingOverlay`.
3. `useBookingPolling` starts polling `GET /api/v1/bookings/{bookingId}` for the async saga
   outcome.
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

### 8. Admin: events — `/admin/events`

**Layout**
```
┌──────────────────────────────────────────────────────────┐
│ NavBar (Admin link visible only for ADMIN role)             │
├──────────────────────────────────────────────────────────┤
│ AdminTabs: Events | Venues                                  │
├──────────────────────────────────────────────────────────┤
│ [ + Create event ]                          [ + Create venue]│
├──────────────────────────────────────────────────────────┤
│ EventTable                                                   │
│  Title      Venue        Starts at        Status   [Edit]   │
│  Rock Night  Arena Hall   2026-10-01 20:00  ON_SALE  [Edit]  │
│  Jazz Fest   City Hall    2026-11-05 19:00  DRAFT    [Edit]  │
├──────────────────────────────────────────────────────────┤
│ Pagination                                                    │
└──────────────────────────────────────────────────────────┘

  (Create/Edit event — modal or slide-over panel)
┌──────────────────────────────────────────────────────────┐
│ EventForm                                                    │
│  Title         [_____________________]                      │
│  Venue         [ VenueSelect ▾ ]  or  [+ new venue]          │
│  Description   [_____________________]                      │
│  Starts at     [ date/time picker ]                          │
│  Status        [ DRAFT | ON_SALE | SOLD_OUT | CLOSED ▾ ]     │
│  SeatCategoryList                                             │
│   - Name [VIP]      Price [$120]   [Remove]                 │
│   - Name [Standard] Price [$60]    [Remove]                 │
│   [+ Add seat category]                                      │
│  [ FormError banner ]                                        │
│  [ Cancel ]                    [ Save event ]                │
└──────────────────────────────────────────────────────────┘
```

**Components**
- `AdminGuard` — route-level wrapper; redirects/403s non-`ADMIN` JWTs before rendering anything
  else on this route (mirrors the `auth` rule "only `ADMIN` may create/update venues and events").
- `AdminTabs` — switches between the Events list and a Venues list (venues are created inline from
  the event form via `VenueSelect`'s "+ new venue" option, so a full separate venue CRUD screen is
  out of scope for the demo — matches CLAUDE.md's admin scope: "create/edit venues & events").
- `EventTable` — list container; one row per `Event` (title, venue name, `startsAt`, status badge,
  edit link). Owns fetch + empty/loading/error state, same pattern as `EventList` (screen 2).
- `EventStatusBadge` — reused from screen 3, now shows all four lifecycle values including `DRAFT`
  (which end-user screens never show).
- `EventForm` — create/edit form (modal or slide-over): title, venue picker, description, starts-at,
  status, and an embedded `SeatCategoryList`.
- `VenueSelect` — dropdown of existing venues plus a "+ new venue" option that expands an inline
  mini-form (name, address, city) rather than navigating away.
- `SeatCategoryList` — repeatable rows of `SeatCategory` (name, price); add/remove rows client-side
  before submit. No per-seat editing UI here — per CLAUDE.md, `event` owns the static seat map, but
  seat-level admin editing is out of scope for this screen (only seat *categories*, matching the
  Phase 14 endpoint `/events/{id}/seat-categories`).
- `FormError` — reused from screen 1, generic inline error banner (403, validation errors).
- API client: `fetchEvents()` (reused, admin view requests all statuses, not just `ON_SALE`),
  `createVenue()`, `createEvent()`, `createSeatCategories()` in a new `adminApi.ts` (camelCase,
  verb-first, per frontend naming convention) — or co-located in `eventApi.ts` if that file already
  exists; naming, not scope, decision left to `frontend`.

**States**
- Guard/forbidden: non-`ADMIN` JWT (or none) hits `/admin/events` → `AdminGuard` redirects to
  `/events` or renders a 403 `EmptyState` — never renders `EventTable`/`EventForm`. Mirrors the
  gateway/backend returning 403 for a non-admin JWT on `POST /venues` / `/events`.
- Loading: skeleton rows in `EventTable` while `GET /api/v1/events` (admin view, unfiltered by
  status) is in flight.
- Empty: no events exist yet → `EmptyState` "No events yet — create one" (reuses screen 2/7's
  `EmptyState`).
- Error (list): fetch failure → retry banner.
- Form — empty (create mode): all fields blank, `SeatCategoryList` starts with one empty row,
  "Save event" disabled until title, venue, starts-at, and ≥1 valid seat category are filled.
- Form — pre-filled (edit mode): fields populated from the selected `Event`; existing seat
  categories load into `SeatCategoryList`.
- Form — submitting: "Save event" shows spinner, fields disabled, calls `POST /api/v1/events` (or
  update) then `POST /api/v1/events/{id}/seat-categories`.
- Form — error: 403 (role check failed server-side despite client guard) → `FormError` "You don't
  have permission to do this."; 400/422 validation (e.g. missing required field, non-positive
  price) → `FormError` with the specific message; venue-creation inline mini-form has its own
  equivalent validation state.
- Form — success: modal/panel closes, `EventTable` refetches and shows the new/updated row.

**Interaction flow**
1. `ADMIN`-role user navigates to `/admin/events` → `AdminGuard` checks the JWT's `roles` claim →
   passes → `EventTable` fetches `GET /api/v1/events` (all statuses) → loading skeleton, then rows.
2. A non-`ADMIN` user (or logged-out) hits the same route → `AdminGuard` blocks rendering, shows
   403/`EmptyState` or redirects — no table or form ever mounts, no admin API call is attempted.
3. User clicks "+ Create event" → `EventForm` opens (modal/slide-over) in create mode, empty.
4. User picks an existing venue from `VenueSelect`, or clicks "+ new venue" → inline mini-form
   (name, address, city) → on submit, `createVenue()` → `POST /api/v1/venues` → new venue appears
   selected in `VenueSelect` without leaving the event form.
5. User fills title, description, starts-at, leaves status at `DRAFT` (or picks `ON_SALE`), adds one
   or more `SeatCategoryList` rows (name + price each).
6. User clicks "Save event" → `createEvent()` → `POST /api/v1/events`, then
   `createSeatCategories()` → `POST /api/v1/events/{id}/seat-categories` for the category rows →
   on success, form closes, `EventTable` refetches and shows the new row with its status badge.
7. User clicks "Edit" on an existing row → `EventForm` opens pre-filled with that `Event`'s fields
   and existing seat categories → user changes status from `DRAFT` to `ON_SALE` (the transition
   that makes it visible on the public `/events` list per the `event` status lifecycle) → saves →
   table updates in place.
8. Any admin API call returning 403 mid-session (e.g. JWT role changed/expired) → `FormError`
   shown, form stays open with the user's unsaved input intact so they don't lose it.

---

### Visual mockups (all 8 screens)

Every screen listed above now has a visual wireframe mockup, published as a Claude Code Artifact,
in addition to its written wireframe detail. All eight reuse the same style system (color tokens,
`Sora`/`Public Sans` fonts, panel/badge/toast/button shapes, light+dark theme handling) established
by the screen 4 mockup, so the set reads as one consistent design language rather than eight ad hoc
pages.

These are **static HTML wireframe mockups** (built and published directly as Claude Artifacts),
**not** Claude Design canvases and **not** native Figma files — no Figma MCP/API is connected in
this environment, and this environment's shell tooling wasn't available to run the Claude Design
canvas seeding script. Treat each as a visual reference for layout/states only, not an editable
design file.

| # | Screen | Artifact |
|---|--------|----------|
| 1 | Login / Register | https://claude.ai/code/artifact/1a413ea4-1393-4294-a407-1184b9fd524c |
| 2 | Event list | https://claude.ai/code/artifact/961ef8f1-02d2-457d-a586-c2ecab804648 |
| 3 | Event detail | https://claude.ai/code/artifact/acda673e-5835-4537-8670-7febd38ed1a2 |
| 4 | Seat selection | https://claude.ai/code/artifact/38365fae-b8d2-4dfe-8f66-9febdd9eeca7 |
| 5 | Checkout / Payment | https://claude.ai/code/artifact/fb6fe7fb-be83-4977-bfa6-40ccfe64b465 |
| 6 | Confirmation | https://claude.ai/code/artifact/50f92954-3ec2-4ef0-8f3a-6ed1cc3955fc |
| 7 | My tickets | https://claude.ai/code/artifact/5409746a-0251-4eab-8835-705408192687 |
| 8 | Admin: events | https://claude.ai/code/artifact/ad358ad2-3485-4a75-8d47-9691373db497 |
