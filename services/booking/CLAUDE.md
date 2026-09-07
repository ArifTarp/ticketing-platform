# CLAUDE.md — booking-service

> Loaded when working inside `services/booking/`. See root `CLAUDE.md` for shared conventions.

Placeholder for business logic — populated in Phase 7 (bookings CRUD + Redis seat holds,
concurrent seat-race handling) and Phase 8 (checkout saga wiring). Owns: seat availability state,
holds (Redis TTL), bookings, saga orchestration (booking is the orchestrator, per ADR-0001).

## Kafka contracts (Phase 6 — already available, wire up producer/consumer in Phase 8)

booking depends on the shared `com.demo.ticketing:messaging` module
(`docs/adr/0003-shared-messaging-module-for-kafka-contracts.md`) for:

- `com.demo.ticketing.messaging.command.PaymentRequestedCommand` — produced to
  `payment.commands` (key = `bookingId`), after the local `SagaState` commit (outbox pattern).
- `com.demo.ticketing.messaging.event.PaymentCompletedEvent` / `PaymentFailedEvent` — consumed
  from `payment.events` (key = `bookingId`).
- `com.demo.ticketing.messaging.event.BookingConfirmedEvent` / `BookingCancelledEvent` —
  produced to `booking.events` (key = `bookingId`). `BookingCancelledEvent.reason` distinguishes
  `"PAYMENT_FAILED"` from `"HOLD_EXPIRED"` (see business-rules.md's "Flagged gaps" — there is no
  distinct `BookingExpired` topic-level event).
- `com.demo.ticketing.messaging.topic.KafkaTopics` — canonical topic name constants; do not
  hardcode topic strings.
- `com.demo.ticketing.messaging.config.KafkaProducerFactorySupport` /
  `KafkaConsumerConfigSupport` / `KafkaJsonSupport` — call these from this service's own
  `@Configuration` class to build `KafkaTemplate`/listener container factory beans.

### DLQ convention
Every `@KafkaListener` here (the `payment.events` consumer) must use a container factory built
via `KafkaConsumerConfigSupport.listenerContainerFactory(...)`, which retries 2 extra times
(fixed 1s backoff) then publishes to `payment.events.DLT`.

### Deferred to Phase 7/8
- `SeatAvailability`, Redis holds, the hold/checkout REST endpoints (Phase 7).
- `SagaState`, the actual `@KafkaListener` for `payment.events`, the timeout sweep job, and the
  idempotency/dedup store keyed on the consumed event's `eventId` (Phase 8, `saga-orchestrator`).
  This phase only proves the message shapes and Kafka plumbing work in isolation (see
  `messaging` module's round-trip test).
