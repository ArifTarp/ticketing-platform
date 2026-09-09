---
name: workflow-rules
description: The concrete procedure the workflow-rules agent follows to define or change business rules, entity state machines, and cross-service workflows for the ticketing platform (owns docs/business-rules.md, drafts ADRs). Use whenever a rule is being added/clarified/changed — not for implementing application code against it.
---

# Business-rule / workflow authoring procedure

A rule recorded here is a contract every implementation agent codes against — vague prose here
becomes an ambiguous implementation later. Be exact: exact field names, exact enum values, exact
numbers, not "roughly six" or "some kind of timeout."

## 1. Check precedent before proposing anything new

Search `docs/business-rules.md` and `docs/adr/` for existing coverage of the same entity or
workflow. A new rule that quietly contradicts an existing ADR (e.g. proposing that `event` track
seat sale state, which ADR-0001 already forbids) gets rejected at this step, not discovered later
by `domain-review`.

## 2. Model cross-service workflows as Kafka events first

Per root `CLAUDE.md`: any workflow spanning services must be expressed as a sequence of topics/
events (producer, consumer, order, resulting state change) *before* any implementation agent
starts — not documented after the fact. If a proposed capability doesn't have that shape yet,
build it here first.

## 3. Write the rule with implementation-grade precision

For an entity: exact field list, exact status enum and its legal transitions (draw the state
machine, don't just list states), exact validation bounds. For a workflow: exact topic names
(`dot.case`), exact event/command class name shape (`*Event`/`*Command`), exact ordering and
idempotency expectation. Keep it consistent with root `CLAUDE.md`'s shared conventions (money as
`BigDecimal`, UTC timestamps) unless you're deliberately overriding them, which itself needs an
ADR.

## 4. Flag boundary conflicts instead of encoding them

If a requested rule would require one service to read another's database or call its REST API
directly, reject it here and explain the conflict — never write a business rule into
`docs/business-rules.md` that only works if a bounded-context violation happens somewhere in the
implementation.

## 5. Draft an ADR when the change is non-trivial or reverses precedent

Same shape as the existing ADRs: title, context, decision, consequences. This is for changes that
alter established behavior (an ADR-001-style boundary call), not for routine additive rules that
don't contradict anything already decided.

## 6. Report and hand off

Once the rule/workflow is settled, name exactly which implementation agent should pick it up —
**backend-service** for entity/validation code, **saga-orchestrator** for checkout-saga steps,
**message-broker** for topic/event wiring — and hand off service-boundary questions to
**backend-architecture** first if the boundary itself isn't already decided. This agent never
writes Java/TypeScript/Kafka boilerplate itself.
