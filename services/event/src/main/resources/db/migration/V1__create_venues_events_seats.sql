-- Event catalog schema: venues, events, per-event price tiers, and the venue's static seat map.
--
-- ADR-0001: seat *availability* (AVAILABLE / HELD / SOLD) is owned by the booking service and is
-- deliberately absent from this schema. No table here may ever grow a sold/held/available column —
-- if you feel the need for one, you are about to break the service boundary.

CREATE TABLE venues (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(160) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    city       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_venues_name UNIQUE (name)
);

CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    venue_id    BIGINT        NOT NULL REFERENCES venues (id),
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    starts_at   TIMESTAMPTZ   NOT NULL,
    status      VARCHAR(16)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Status lifecycle (business-rules.md): DRAFT -> ON_SALE -> SOLD_OUT | CLOSED.
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'CLOSED'))
);

CREATE INDEX ix_events_venue_id ON events (venue_id);
-- The public list endpoint always filters ON_SALE and orders by starts_at.
CREATE INDEX ix_events_status_starts_at ON events (status, starts_at);

-- Static layout. A seat belongs to the VENUE, not the event (business-rules.md) — the same physical
-- seat is reused by every event held at that venue.
CREATE TABLE seats (
    id          BIGSERIAL PRIMARY KEY,
    venue_id    BIGINT      NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    section     VARCHAR(32) NOT NULL,
    -- "row" is a reserved word in SQL/HQL, hence row_label; "number" is likewise avoided.
    row_label   VARCHAR(16) NOT NULL,
    seat_number INTEGER     NOT NULL,
    CONSTRAINT uq_seats_position UNIQUE (venue_id, section, row_label, seat_number)
);

CREATE INDEX ix_seats_venue_id ON seats (venue_id);

-- A price tier for one event. `section` is how a tier is attached to physical seats: every seat in
-- that venue section is sold at this tier for this event. business-rules.md defines SeatCategory as
-- (id, eventId, name, price) but does not say how a seat gets its tier — one tier per section is
-- the smallest model that satisfies the documented "seat map returns each seat's seatCategory"
-- contract. Both UNIQUE constraints below encode that rule: a tier name is unique per event, and a
-- section can be priced by at most one tier per event. If a tier ever needs to span several
-- sections, promote `section` to a child table (seat_category_sections) rather than duplicating
-- tier rows.
CREATE TABLE seat_categories (
    id       BIGSERIAL PRIMARY KEY,
    event_id BIGINT         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    name     VARCHAR(64)    NOT NULL,
    price    NUMERIC(12, 2) NOT NULL,
    section  VARCHAR(32)    NOT NULL,
    CONSTRAINT ck_seat_categories_price CHECK (price >= 0),
    CONSTRAINT uq_seat_categories_event_name UNIQUE (event_id, name),
    CONSTRAINT uq_seat_categories_event_section UNIQUE (event_id, section)
);

CREATE INDEX ix_seat_categories_event_id ON seat_categories (event_id);
