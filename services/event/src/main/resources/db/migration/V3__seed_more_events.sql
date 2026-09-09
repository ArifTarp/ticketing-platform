-- Expands the demo catalog with developer/tech workshop events so the catalog screens have enough
-- volume (and enough seats) to look real. Two new, larger venues are added because the V2 venues
-- (66 + 12 seats) are far too small for the seat counts here; existing V2 venues/events/seats are
-- left untouched so EventControllerTest's per-event assertions (Neon Nights Live's 66 seats, Retro
-- Fest's 30-seat section B, ...) keep passing unmodified.
--
-- Sections stay the existing row/col + `section` model (no arc/coordinate columns) — the frontend
-- will render the arc visual via CSS transforms keyed off `section`, which is a separate,
-- frontend-only concern.

INSERT INTO venues (name, address, city) VALUES
    ('TechHub Convention Center', '45 Teknokent Bulvarı', 'Istanbul'),
    ('Innovation Campus Auditorium', '8 Bilkent Kampüsü Caddesi', 'Ankara');

-- TechHub Convention Center layout: VIP 2x8=16, A 5x18=90, B 5x18=90, C 3x15=45 => 241 seats.
INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'VIP', r::text, n
FROM venues v, generate_series(1, 2) AS r, generate_series(1, 8) AS n
WHERE v.name = 'TechHub Convention Center';

INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'A', r::text, n
FROM venues v, generate_series(1, 5) AS r, generate_series(1, 18) AS n
WHERE v.name = 'TechHub Convention Center';

INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'B', r::text, n
FROM venues v, generate_series(1, 5) AS r, generate_series(1, 18) AS n
WHERE v.name = 'TechHub Convention Center';

INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'C', r::text, n
FROM venues v, generate_series(1, 3) AS r, generate_series(1, 15) AS n
WHERE v.name = 'TechHub Convention Center';

-- Innovation Campus Auditorium layout: VIP 2x6=12, A 4x15=60, B 4x15=60, C 3x12=36 => 168 seats.
INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'VIP', r::text, n
FROM venues v, generate_series(1, 2) AS r, generate_series(1, 6) AS n
WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'A', r::text, n
FROM venues v, generate_series(1, 4) AS r, generate_series(1, 15) AS n
WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'B', r::text, n
FROM venues v, generate_series(1, 4) AS r, generate_series(1, 15) AS n
WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO seats (venue_id, section, row_label, seat_number)
SELECT v.id, 'C', r::text, n
FROM venues v, generate_series(1, 3) AS r, generate_series(1, 12) AS n
WHERE v.name = 'Innovation Campus Auditorium';

-- 14 developer/tech workshop events, all ON_SALE, spread across Oct 2026 - Feb 2027. starts_at is
-- authored as real Istanbul-local wall-clock time with an explicit +03 offset so it renders
-- correctly for any reader regardless of session timezone.

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'React ile Modern Frontend Geliştirme Atölyesi',
       'Hooks, state yönetimi ve performans optimizasyonuna odaklanan tam günlük, uygulamalı bir React atölyesi.',
       '2026-10-12 10:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'TechHub Convention Center';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Kubernetes: Production''a Hazır mısınız?',
       'Cluster mimarisi, otomatik ölçekleme ve production ortamı için sağlamlaştırma pratiklerini kapsayan ileri seviye bir Kubernetes günü.',
       '2026-10-20 09:30:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'TechHub Convention Center';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Rust ile Sistem Programlama',
       'Sahiplik modeli, bellek güvenliği ve düşük seviye performans üzerine örneklerle ilerleyen bir Rust eğitimi.',
       '2026-10-28 10:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'LLM Tabanlı Uygulama Geliştirme',
       'Büyük dil modelleriyle üretim kalitesinde uygulama geliştirmeyi; prompt tasarımı, RAG ve değerlendirme metriklerini ele alan bir atölye.',
       '2026-11-05 13:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'TechHub Convention Center';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'DevOps ve CI/CD Pratikleri',
       'Pipeline tasarımından otomatik dağıtıma kadar modern DevOps araç zincirini uçtan uca gösteren bir gün.',
       '2026-11-12 09:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Sistem Tasarımı Derinlemesine',
       'Ölçeklenebilir dağıtık sistemlerin tasarımını; önbellekleme, sharding ve tutarlılık modelleri üzerinden vaka analizleriyle işleyen bir seminer.',
       '2026-11-19 10:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'TechHub Convention Center';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Go ile Mikroservis Mimarisi',
       'Go dilinde performanslı ve test edilebilir mikroservisler yazmayı; gRPC ve mesajlaşma desenleriyle birlikte anlatan bir atölye.',
       '2026-11-26 10:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Veri Mühendisliğine Giriş: Apache Kafka ve Spark',
       'Akış verisi işleme temellerini Apache Kafka ve Spark üzerinden uygulamalı örneklerle anlatan bir veri mühendisliği günü.',
       '2026-12-03 09:30:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'TechHub Convention Center';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Bulut Native Güvenlik Uygulamaları',
       'Konteyner ve bulut native ortamlarda güvenlik açıklarını tespit etmeyi ve önlemeyi konu alan uygulamalı bir atölye.',
       '2026-12-10 10:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'GraphQL ile API Tasarımı',
       'Şema tasarımı, veri yükleme stratejileri ve performans konularını kapsayan pratik bir GraphQL atölyesi.',
       '2026-12-17 13:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'TechHub Convention Center';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Test Otomasyonu ve Kalite Mühendisliği',
       'Uçtan uca test stratejilerini, otomasyon çerçevelerini ve sürekli entegrasyona entegrasyonu ele alan bir kalite mühendisliği günü.',
       '2027-01-14 10:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Terraform ile Altyapıyı Kod Olarak Yönetmek',
       'Bulut altyapısını versiyonlanabilir, tekrarlanabilir kod olarak yönetmeyi Terraform üzerinden uygulamalı anlatan bir atölye.',
       '2027-01-21 09:30:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'TechHub Convention Center';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Mobil Geliştirmede Flutter Derinlemesine',
       'Tek kod tabanından çoklu platform mobil uygulama geliştirmeyi; state yönetimi ve performans ayarlarıyla derinlemesine ele alan bir atölye.',
       '2027-01-28 10:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'Innovation Campus Auditorium';

INSERT INTO events (venue_id, title, description, starts_at, status)
SELECT v.id,
       'Yapay Zeka Destekli Yazılım Geliştirme Araçları',
       'Kod tamamlama, otomatik test üretimi ve code review''de yapay zeka destekli araçların pratikte kullanımını gösteren bir seminer.',
       '2027-02-04 10:00:00+03',
       'ON_SALE'
FROM venues v WHERE v.name = 'TechHub Convention Center';

-- Price tiers per event (TL). VIP highest, Bakış (restricted-view/back rows) lowest.

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 2200.00, 'VIP'),
                      ('Standart', 950.00, 'A'),
                      ('Ekonomi', 650.00, 'B'),
                      ('Bakış', 400.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'React ile Modern Frontend Geliştirme Atölyesi';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 2500.00, 'VIP'),
                      ('Standart', 1100.00, 'A'),
                      ('Ekonomi', 750.00, 'B'),
                      ('Bakış', 450.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Kubernetes: Production''a Hazır mısınız?';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 1800.00, 'VIP'),
                      ('Standart', 800.00, 'A'),
                      ('Ekonomi', 550.00, 'B'),
                      ('Bakış', 320.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Rust ile Sistem Programlama';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 2400.00, 'VIP'),
                      ('Standart', 1050.00, 'A'),
                      ('Ekonomi', 700.00, 'B'),
                      ('Bakış', 420.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'LLM Tabanlı Uygulama Geliştirme';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 1900.00, 'VIP'),
                      ('Standart', 850.00, 'A'),
                      ('Ekonomi', 580.00, 'B'),
                      ('Bakış', 340.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'DevOps ve CI/CD Pratikleri';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 2300.00, 'VIP'),
                      ('Standart', 1000.00, 'A'),
                      ('Ekonomi', 680.00, 'B'),
                      ('Bakış', 410.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Sistem Tasarımı Derinlemesine';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 1750.00, 'VIP'),
                      ('Standart', 780.00, 'A'),
                      ('Ekonomi', 530.00, 'B'),
                      ('Bakış', 310.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Go ile Mikroservis Mimarisi';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 2150.00, 'VIP'),
                      ('Standart', 920.00, 'A'),
                      ('Ekonomi', 630.00, 'B'),
                      ('Bakış', 380.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Veri Mühendisliğine Giriş: Apache Kafka ve Spark';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 2000.00, 'VIP'),
                      ('Standart', 900.00, 'A'),
                      ('Ekonomi', 620.00, 'B'),
                      ('Bakış', 360.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Bulut Native Güvenlik Uygulamaları';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 1950.00, 'VIP'),
                      ('Standart', 850.00, 'A'),
                      ('Ekonomi', 580.00, 'B'),
                      ('Bakış', 350.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'GraphQL ile API Tasarımı';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 1650.00, 'VIP'),
                      ('Standart', 750.00, 'A'),
                      ('Ekonomi', 510.00, 'B'),
                      ('Bakış', 300.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Test Otomasyonu ve Kalite Mühendisliği';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 2050.00, 'VIP'),
                      ('Standart', 900.00, 'A'),
                      ('Ekonomi', 600.00, 'B'),
                      ('Bakış', 360.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Terraform ile Altyapıyı Kod Olarak Yönetmek';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 1850.00, 'VIP'),
                      ('Standart', 820.00, 'A'),
                      ('Ekonomi', 560.00, 'B'),
                      ('Bakış', 330.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Mobil Geliştirmede Flutter Derinlemesine';

INSERT INTO seat_categories (event_id, name, price, section)
SELECT e.id, c.name, c.price, c.section
FROM events e
         JOIN (VALUES ('VIP', 2500.00, 'VIP'),
                      ('Standart', 1150.00, 'A'),
                      ('Ekonomi', 780.00, 'B'),
                      ('Bakış', 470.00, 'C')) AS c(name, price, section) ON TRUE
WHERE e.title = 'Yapay Zeka Destekli Yazılım Geliştirme Araçları';
