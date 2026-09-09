---
name: frontend-architecture
description: The concrete procedure the frontend-architecture agent follows to evaluate a frontend architectural question for the ticketing platform (route structure, state management, component boundaries, data-fetching strategy). Use whenever advising on how a screen/feature should be structured — not for implementing it.
---

# Frontend architecture review procedure

Structured like a route/component/state-management audit — cite exact files for every claim.

## 1. Route inventory

Compare the request against `docs/user-flow.md`'s full screen/route list. Does it fit an existing
route, or does it need a new one? If new, does the proposed App Router path follow this repo's
existing file-convention pattern (`app/events/[eventId]/page.tsx`-style)? Flag any deviation from
`docs/user-flow.md` instead of silently improvising a new screen — that doc is the authoritative
screen list, not a suggestion.

## 2. State-management fit

This repo made a deliberate Phase 10 decision: plain React state + small custom hooks
(`useSeatSelection`, `useCountdown`, `useBookingPolling`), no Redux/SWR/React Query. Don't
relitigate that decision — evaluate the request against it: does the new state genuinely need to
be shared across components, or is local state/a small hook enough? Cite the existing hooks as
precedent for the pattern to follow.

## 3. Component boundary

Decide what should be its own component vs. inlined, using the existing `components/<domain>/`
folder structure as precedent (e.g. `components/seats/`, `components/checkout/`). A boundary is
right when the component can be described, used, and tested without reading its internals from
the outside — if the proposed split doesn't achieve that, say so.

## 4. Data-fetching strategy

Check whether the screen needs a one-shot fetch, a client-side poll (this repo already has one
documented, deliberate pattern for this: `useBookingPolling` for payment-status polling — cite
it as precedent rather than inventing a different polling shape), or a merge of two independent
calls (the seat-map screen's client-side merge of `event`'s seat layout with `booking`'s
availability is the existing precedent for that shape — see ADR-0001 and
`docs/business-rules.md`'s "Seat map contract").

## 5. Service-boundary check

The frontend talks only to the gateway, never to a service directly, and never proxies one
service's data through another's route. Confirm the proposal doesn't imply otherwise.

## 6. Report and hand off

State the recommendation with the files you cited as precedent. If the question is actually about
screen-level layout/wireframe/visual-state detail rather than route/state/data-fetching shape,
say so and redirect to **figma-screen-design** instead of answering it yourself. Hand off
implementation to **frontend**. Never implement it yourself — this agent has no `Write`/`Edit`
tools.
