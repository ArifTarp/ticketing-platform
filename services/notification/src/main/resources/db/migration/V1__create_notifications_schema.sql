-- Notification core schema: mock "sent" notification log and Kafka-consumer idempotency ledger.
--
-- notification is a pure Kafka consumer (no inbound REST, per its own CLAUDE.md): it consumes
-- BookingConfirmedEvent/BookingCancelledEvent from booking.events and logs a mock "sent"
-- notification row. This migration only covers the persistence this service owns itself;
-- booking_id/user_id are foreign ids from booking's/auth's databases, not local FKs
-- (root CLAUDE.md: no cross-service DB access).

-- One mock "sent" notification. status is always SENT for this demo mock (no real mail/SMS
-- integration) -- the column still exists so the schema mirrors root CLAUDE.md's documented
-- notifications(type, recipient, channel, status, payload, created_at) shape.
CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    type         VARCHAR(32)  NOT NULL,
    recipient    VARCHAR(255) NOT NULL,
    channel      VARCHAR(16)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    payload      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notifications_type CHECK (type IN ('BOOKING_CONFIRMED', 'BOOKING_CANCELLED')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('SENT'))
);

-- Idempotency/dedup ledger for the booking.events consumer: Kafka delivery is at-least-once, so a
-- redelivered BookingConfirmedEvent/BookingCancelledEvent (same messageId) must not create a
-- second notification row. See NotificationService for the claim-then-insert approach (same house
-- convention as services/payment's processed_messages table).
CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
