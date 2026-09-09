# CLAUDE.md — frontend

> Loaded when working inside `frontend/`. See root `CLAUDE.md` for shared conventions.

Next.js (App Router, TypeScript), pnpm. Talks **only** to the gateway
(`http://localhost:8080`), never to a service directly.

Screens (per `docs/user-flow.md`): `/login`, `/register`, `/events`, `/events/[eventId]`,
`/events/[eventId]/seats`, `/checkout/[bookingId]`, `/checkout/[bookingId]/confirm`, `/tickets`.
Admin (`/admin/events`) is Phase 14, optional/last.

State management: plain React state + small custom hooks (`useCountdown`, `useSeatSelection`,
`useBookingPolling`) — no Redux/SWR/React Query, per the Phase 10 architecture review. JWT held in
`localStorage` via the root `SessionProvider` context. `apiClient.ts` wraps `fetch` with RFC 7807
error parsing.

Note: `fetchMyBookings()` decodes the JWT's `sub` claim client-side to call
`GET /api/v1/bookings?userId=`, since booking does not parse JWTs itself.
