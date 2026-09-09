---
name: backend-architecture
description: The concrete procedure the backend-architecture agent follows to evaluate a backend architectural question for the ticketing platform (service boundaries, layering, new-service decisions, ADRs). Use whenever advising on where a capability belongs or whether a proposed change violates a bounded context — not for implementing the decided approach.
---

# Backend architecture review procedure

Structured like a component-map / data-flow / boundary-risk audit — cite exact files for every
claim, never assert from memory of "how this kind of app usually works."

## 1. Map the current landscape

Before recommending anything, establish what actually exists:
- Which service(s) does the capability touch? Read each one's own `CLAUDE.md` plus root
  `CLAUDE.md`'s "Service responsibilities & bounded contexts" table.
- What does each involved service currently own (its entities, its database, its Kafka
  topics)? Cite the actual `.domain` package / Flyway migrations, not an assumption.
- Tag every claim **Confirmed** (you read the file) or **Inferred** (you're reasoning from a
  pattern elsewhere) — never present an inferred claim as confirmed.

## 2. Trace the data flow

- Is this a synchronous read/command (REST via gateway) or does it cross a service boundary at
  write time? Per root `CLAUDE.md`: "Before implementing a workflow that spans services, model it
  as Kafka events first" — if the proposal doesn't yet have an event/command shape for a
  cross-service write, that's a real gap to flag, not something to wave through.
- Does the proposal require one service to read data another service owns? If so, the answer is
  Kafka or a gateway-composed read — never a direct DB read or REST-to-REST call. Say so
  explicitly if the request implies otherwise.

## 3. Check bounded-context violations

Walk the specific rule list from root `CLAUDE.md`'s "How Claude Code should work here" section:
no shared DB, no cross-service DB reads, no service-to-service REST, no business logic in the
gateway. For each, state explicitly whether the proposal violates it (with the file/line that
would need to change) or doesn't.

## 4. Check precedent in `docs/adr/`

Search existing ADRs before proposing a new pattern — if a prior decision (e.g. ADR-0001's "seat
availability lives in booking, not event") already covers this ground, say so and apply it rather
than re-deciding from scratch.

## 5. Package layering fit

For a new capability inside an existing service, confirm which layer it belongs in
(`.domain`/`.application`/`.web`/`.infra`/`.config`) per that service's own `CLAUDE.md` and the
naming conventions in root `CLAUDE.md`.

## 6. Draft the ADR when the decision is non-trivial

Use the same shape as the existing ADRs under `docs/adr/`: title, context, decision,
consequences. Write it for the requester to save — this agent has no `Write`/`Edit` tools.

## 7. Report and hand off

State the recommendation plainly, list every file you cited as evidence, and name which
implementation agent (`backend-service`, `saga-orchestrator`, `message-broker`,
`gateway-resilience`) should pick it up once the decision is made. Never implement it yourself.
