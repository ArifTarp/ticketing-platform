# CLAUDE.md — notification-service

> Loaded when working inside `services/notification/`. See root `CLAUDE.md` for shared
> conventions.

Placeholder for business logic — populated in Phase 9 (consumes `booking.events`, idempotent on
event id, logs "sent" mail/SMS mock). Owns: `notifications` table. Pure consumer, no inbound
REST.

## Kafka contracts (Phase 6 — already available, wire up consumer in Phase 9)

notification depends on the shared `com.demo.ticketing:messaging` module
(`docs/adr/0003-shared-messaging-module-for-kafka-contracts.md`) for:

- `com.demo.ticketing.messaging.event.BookingConfirmedEvent` / `BookingCancelledEvent` —
  consumed from `booking.events` (key = `bookingId`). Both carry `userId` so this service can
  address the `Notification.recipient` without reading booking's or auth's database directly
  (root CLAUDE.md's "never touch another service's database" rule).
- `com.demo.ticketing.messaging.topic.KafkaTopics` — canonical topic name constants; do not
  hardcode topic strings.
- `com.demo.ticketing.messaging.config.KafkaConsumerConfigSupport` / `KafkaJsonSupport` — call
  these from this service's own `@Configuration` class to build the listener container factory.

### DLQ convention
The `@KafkaListener` here must use a container factory built via
`KafkaConsumerConfigSupport.listenerContainerFactory(...)`, which retries 2 extra times (fixed
1s backoff) then publishes to `booking.events.DLT`.

### Deferred to Phase 9
- The `Notification` entity, the actual `@KafkaListener`, and the idempotency/dedup store keyed
  on the consumed event's `eventId` (per business-rules.md's "Idempotent: dedupe on event id").
  None of that exists yet — this phase only proves the message shapes and Kafka plumbing work in
  isolation (see `messaging` module's round-trip test).
