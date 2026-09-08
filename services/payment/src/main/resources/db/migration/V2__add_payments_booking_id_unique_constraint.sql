-- docs/business-rules.md: "bookingId (unique — one payment attempt per booking)". V1 only added a
-- non-unique index (ix_payments_booking_id), which never enforced this at the database level.
-- Flyway migrations are immutable once applied, so this is a new versioned migration rather than
-- an edit to V1.

ALTER TABLE payments
    ADD CONSTRAINT uq_payments_booking_id UNIQUE (booking_id);
