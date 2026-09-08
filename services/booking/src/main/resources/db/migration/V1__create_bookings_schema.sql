-- Booking core schema: bookings, booking line items, seat availability state, and saga tracking.
--
-- ADR-0001: booking is the sole owner of seat *availability* (AVAILABLE / HELD / SOLD). event owns
-- the static seat map (venue/section/row/number/price) in its own database and is never read here;
-- event_id/seat_id below are foreign ids from that service, not local foreign keys.

-- One customer's attempt to buy a set of seats for one event. Status lifecycle (root CLAUDE.md /
-- business-rules.md): PENDING -> CONFIRMED | CANCELLED | EXPIRED.
CREATE TABLE bookings (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT         NOT NULL,
    event_id   BIGINT         NOT NULL,
    status     VARCHAR(16)    NOT NULL,
    total      NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_bookings_total CHECK (total >= 0)
);

CREATE INDEX ix_bookings_user_id ON bookings (user_id);
CREATE INDEX ix_bookings_event_id ON bookings (event_id);
-- The Phase 8 timeout sweep scans PENDING bookings whose expires_at has passed.
CREATE INDEX ix_bookings_status_expires_at ON bookings (status, expires_at);

-- One held/sold seat within a booking. price is a snapshot taken at hold time
-- (business-rules.md: "not recalculated from current category price") — booking never re-reads
-- event's pricing after the hold.
CREATE TABLE booking_items (
    id         BIGSERIAL PRIMARY KEY,
    booking_id BIGINT         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    seat_id    BIGINT         NOT NULL,
    price      NUMERIC(12, 2) NOT NULL,
    CONSTRAINT ck_booking_items_price CHECK (price >= 0),
    -- A seat can only appear once per booking. The "max 6 seats/booking" rule is a business rule
    -- enforced in the application layer (Phase 7 hold endpoint), not a DB constraint.
    CONSTRAINT uq_booking_items_booking_seat UNIQUE (booking_id, seat_id)
);

CREATE INDEX ix_booking_items_booking_id ON booking_items (booking_id);

-- Source of truth for whether a seat can be picked, per event. Mirrors the short-lived Redis
-- distributed lock (eventId:seatId, 10-minute TTL, Phase 7) for durability/audit; the Redis lock
-- itself remains what arbitrates concurrent hold requests (business-rules.md's "concurrent
-- seat-race" edge path) — this table is the persisted outcome, not the lock.
CREATE TABLE seat_availability (
    id         BIGSERIAL PRIMARY KEY,
    event_id   BIGINT      NOT NULL,
    seat_id    BIGINT      NOT NULL,
    status     VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_seat_availability_status CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    CONSTRAINT uq_seat_availability_event_seat UNIQUE (event_id, seat_id)
);

CREATE INDEX ix_seat_availability_event_id ON seat_availability (event_id);

-- Tracks a booking's progress through the checkout saga so a crash mid-saga can be recovered
-- instead of leaving a booking stuck (business-rules.md). step/status are deliberately free-form
-- strings, not CHECK-constrained enums: the saga-orchestrator agent (Phase 8) owns the actual
-- step/status vocabulary (e.g. step=PAYMENT_REQUESTED, status=IN_PROGRESS|DONE) and this schema
-- should not lock that in ahead of that design landing.
CREATE TABLE saga_state (
    id         BIGSERIAL PRIMARY KEY,
    booking_id BIGINT      NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    step       VARCHAR(32) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_saga_state_booking_id UNIQUE (booking_id)
);
