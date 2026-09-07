-- Demo seed data, kept in its own migration so V1 stays a pure schema migration.
--
-- Deliberately small: 78 seats total, enough to render a realistic seat map and to demo the
-- Phase 7 seat race, without thousands of rows slowing every Testcontainers run.
-- Rows are inserted without explicit ids (so the BIGSERIAL sequences stay correct) and referenced
-- by natural key.

INSERT INTO venues (name, address, city) VALUES
    ('Demo Arena', '1 Sahil Yolu', 'Istanbul'),
    ('Riverside Hall', '12 Riverside Road', 'Ankara');

-- Demo Arena layout: section A (2 rows x 8), B (3 rows x 10), C (2 rows x 10) = 66 seats.
INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'A', r::text, n
FROM venues v, generate_series(1, 2) AS r, generate_series(1, 8) AS n
WHERE v.name = 'Demo Arena';

INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'B', r::text, n
FROM venues v, generate_series(1, 3) AS r, generate_series(1, 10) AS n
WHERE v.name = 'Demo Arena';

INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'C', r::text, n
FROM venues v, generate_series(1, 2) AS r, generate_series(1, 10) AS n
WHERE v.name = 'Demo Arena';

-- Riverside Hall layout: a single section S (2 rows x 6) = 12 seats.
INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'S', r::text, n
FROM venues v, generate_series(1, 2) AS r, generate_series(1, 6) AS n
WHERE v.name = 'Riverside Hall';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Neon Nights Live',
       'A synthwave night with three acts, doors at 19:00.',
       now() + INTERVAL '30 days',
       'ON_SALE'
FROM venues v
WHERE v.name = 'Demo Arena';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Acoustic Evening',
       'An intimate unplugged set in a 12-seat listening room.',
       now() + INTERVAL '45 days',
       'ON_SALE'
FROM venues v
WHERE v.name = 'Riverside Hall';

-- DRAFT: exists in the catalog but must never appear in the public list.
INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Midnight Rehearsal',
       'Not announced yet.',
       now() + INTERVAL '60 days',
       'DRAFT'
FROM venues v
WHERE v.name = 'Demo Arena';

-- CLOSED and in the past: also excluded from the public list. Prices only section B, which
-- demonstrates that the seat map returns only the sections an event actually sells.
INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Retro Fest',
       'Last year''s edition — archived.',
       now() - INTERVAL '90 days',
       'CLOSED'
FROM venues v
WHERE v.name = 'Demo Arena';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 250.00, 'A'),
                      ('Standard', 120.00, 'B'),
                      ('Balcony', 60.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Neon Nights Live';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, 'General', 80.00, 'S'
FROM events e
WHERE e.title = 'Acoustic Evening';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, 'Standard', 100.00, 'B'
FROM events e
WHERE e.title = 'Midnight Rehearsal';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, 'Standard', 90.00, 'B'
FROM events e
WHERE e.title = 'Retro Fest';
