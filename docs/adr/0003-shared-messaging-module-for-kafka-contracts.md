# 0003. Shared `messaging` Maven module for Kafka contracts

## Status
Accepted

## Context

Phase 6 needs to define the JSON contracts exchanged on `payment.commands`, `payment.events`, and
`booking.events` (root `CLAUDE.md`'s topic table), plus the Spring Kafka producer/consumer/DLQ
plumbing that `booking`, `payment`, and `notification` will each use starting in Phase 7/8.

Root `CLAUDE.md`'s service-boundary rule ("no shared DB, no shared schema, services never call
each other's REST directly") governs *runtime* coupling between services. It does not forbid a
shared **compile-time** library. Two options were considered for the DTOs:

1. **Each service owns its own copy** of the DTOs it produces/consumes (e.g. `booking` defines its
   own `PaymentCompletedEvent` class matching the JSON shape it expects, independent of payment's
   class of the same name).
2. **A shared `messaging` Maven module** (`com.demo.ticketing.messaging`) containing the
   command/event records and reusable Kafka config, that `booking`, `payment`, and `notification`
   all depend on.

## Decision

Use a shared `messaging` module (added to the root `pom.xml`'s `<modules>`, depended on by
`booking`, `payment`, and `notification`).

Reasoning:

- Kafka message compatibility is a **producer/consumer JSON-shape agreement** regardless of which
  Java class deserializes it — duplicating the DTO in three places just means three chances to let
  the copies drift silently (a field rename in `payment`'s copy of `PaymentCompletedEvent` would
  compile fine and only fail at runtime as silently-dropped/ null fields in `booking`'s consumer).
  A shared module makes drift a compile error instead of a runtime surprise.
- This is a small demo monorepo with a single git root and a single Maven reactor already; the
  three consuming services already sit in the same build lifecycle, so adding one more shared
  module has no meaningful deployability cost (each service still packages/deploys independently;
  `messaging` is a compile-time library dependency, not a running service).
- The root boundary rule is about **runtime** coupling (no shared DB, no direct REST) so that
  services can evolve their persistence and APIs independently. A shared DTO/config library for an
  already-agreed wire contract doesn't reintroduce that coupling — it's the idiomatic Spring/Kafka
  pattern at this scale (cf. how many Spring Kafka reference architectures ship an
  `avro`/`schema`/`events` module).
- It also gives us one place to put the reusable DLQ/error-handling Kafka config (`ProducerFactory`,
  `ConsumerFactory`, `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`) instead of
  copy-pasting it three times.

## Consequences

- `messaging` must stay a pure library: no Spring Boot application class, no service-specific
  business logic (no `SagaState`, no idempotency/dedup store — those remain per-service, built in
  Phase 7/8).
- A breaking change to a shared DTO now requires coordinated redeployment awareness across the
  services that depend on it (same tradeoff as any shared library) — acceptable for a demo of this
  size; if this were a larger real system we'd revisit with a schema registry (Avro/Protobuf) and
  independent versioned schemas instead of a shared Java library.
- `booking`, `payment`, `notification` each add a single new dependency on
  `com.demo.ticketing:messaging` in their `pom.xml`. No other change to those modules in this
  phase — their business logic (producers actually calling `KafkaTemplate.send`, consumers acting
  on the payload) is Phase 7/8's job.
