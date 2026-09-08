-- Idempotency store for Kafka consumers (root CLAUDE.md non-negotiable: "every consumer must be
-- idempotent (dedupe on event id -- Kafka can redeliver)"). Keyed on the message's own messageId
-- (UUID), shared across every consumer in this service (currently only the payment.events
-- consumer, PaymentResultListener) since a UUID is globally unique regardless of source topic.
CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
