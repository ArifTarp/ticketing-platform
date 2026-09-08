-- Payment core schema: mock payment records and Kafka-consumer idempotency ledger.
--
-- payment is a pure Kafka consumer/producer (no inbound REST, per its own CLAUDE.md): it consumes
-- `PaymentRequestedCommand` from `payment.commands` and publishes `PaymentCompletedEvent`/
-- `PaymentFailedEvent` to `payment.events`. This migration only covers the persistence this
-- service owns itself; booking_id is a foreign id from booking's database, not a local FK
-- (root CLAUDE.md: no cross-service DB access).

-- One mock payment attempt for a booking. status lifecycle: PENDING -> COMPLETED | FAILED,
-- decided synchronously by the mock fail-threshold rule (see PaymentMockRule) — there is no
-- external PSP callback that would leave a row PENDING for long.
CREATE TABLE payments (
    id           BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT         NOT NULL,
    amount       NUMERIC(10, 2) NOT NULL,
    status       VARCHAR(16)    NOT NULL,
    provider_ref VARCHAR(64),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX ix_payments_booking_id ON payments (booking_id);

-- Idempotency/dedup ledger for the future `payment.commands` consumer: Kafka delivery is at-least
-- once, so a redelivered `PaymentRequestedCommand` (same messageId) must not create a second
-- payment. The consumer inserts a row here before processing; a PK violation on replay means
-- "already processed" (see PaymentProcessingService for the exact insert-then-check approach).
CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
