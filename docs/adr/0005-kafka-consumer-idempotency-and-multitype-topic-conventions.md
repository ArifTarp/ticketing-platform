# 0005. Kafka consumer idempotency and multi-type-topic consumption conventions

## Status
Accepted (codifying existing Phase 8/9 practice)

## Context

Phase 8 (payment) and Phase 9 (notification) both needed to consume Kafka topics that redeliver
messages (at-least-once) or carry more than one payload type on a single topic (`payment.events`
carries both `PaymentCompletedEvent` and `PaymentFailedEvent`). Two patterns were worked out ad hoc
during those phases and were documented only as code comments and per-service `CLAUDE.md` prose,
with no cross-service canonical reference. `services/booking`'s `SagaCompletionService`
independently implemented a variant of the idempotency pattern that differs from payment's and is
only safe by accident of method structure.

## Decision

**Idempotency claim pattern (canonical):** every Kafka consumer that must be idempotent on a
message id keeps a `processed_messages(message_id PK, processed_at)` table and claims a message
via: (1) an `existsById` fast-path check, then (2) if absent, an insert run in its own
`PROPAGATION_REQUIRES_NEW` transaction via a dedicated `TransactionTemplate` (never
`@Transactional(propagation=REQUIRES_NEW)` on a sibling method of the same bean — self-invocation
bypasses the proxy). A `DataIntegrityViolationException` from the REQUIRES_NEW insert means another
call already claimed it; treat as "duplicate, skip." This isolates the unique-constraint failure to
its own connection/transaction so it can never abort the caller's outer transaction on Postgres.
The claim must only be committed once the rest of the unit of work is known to succeed — do not
commit the claim before doing the business-logic work in the same call, or a mid-work failure
leaves the message permanently marked "processed" with nothing actually done or published. See
`services/payment/.../PaymentProcessingService` for the reference implementation.

**Multi-type-topic consumption pattern:** when more than one payload type is published to the same
topic, each type gets its own `@KafkaListener` method, its own `ConsumerFactory`/container factory,
and its own distinct consumer group id (sharing one group id across listeners on a single-partition
topic means only one listener ever receives anything after rebalancing). Each listener's container
factory also installs a `RecordFilterStrategy` that reads the `__TypeId__` header (`JsonSerializer`'s
default type header) and discards any record whose header doesn't match that listener's expected
type — do not rely on `JsonDeserializer.VALUE_DEFAULT_TYPE` alone, which force-deserializes every
record into the configured type regardless of what was actually published (silent data corruption,
not a loud failure). See `services/booking/.../config/KafkaConfig.java` for the reference
implementation.

## Consequences

- Every new `@KafkaListener` added anywhere in the platform must follow both conventions above;
  reviewers should reject a change that hand-rolls a different idempotency check or shares a
  consumer group across payload types on one topic.
- `services/booking/.../SagaCompletionService`'s idempotency claim must be retrofitted to the
  REQUIRES_NEW pattern for consistency, and the claim-commit-ordering bug (committing the claim
  before the business logic succeeds) must be fixed in payment and notification.
- **Update (code review pass):** `SagaCompletionService` had the exact same claim-before-work
  ordering bug as payment/notification — it already used the REQUIRES_NEW `tryClaimMessage` pattern
  above, but `onCompleted`/`onFailed` called it *first*, before `transitionFromPending`/`markSold`/
  `releaseSeat`/`upsertSagaState` ran in the same outer transaction. A failure partway through that
  business logic rolled back the transition/seat/SagaState work but left the claim durably committed
  (it runs in its own transaction), permanently skipping the message on redelivery and leaving the
  booking stuck `PENDING` with seats stuck `HELD` forever. This has been fixed to the same
  claim-after-commit pattern as payment/notification (`claimMessageAfterCommit`, hooking
  `TransactionSynchronization.afterCommit`) — this ADR's list above of services needing the
  ordering fix is now fully closed out (payment, notification, and booking all follow it).
- This ADR is the canonical reference; per-service `CLAUDE.md` files should link here instead of
  re-explaining the rationale.
