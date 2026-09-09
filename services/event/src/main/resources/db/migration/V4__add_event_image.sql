-- Adds a cover image to the event catalog. Nullable: older/admin-created events may not have one
-- yet. Uses picsum.photos deterministic seed URLs (`/seed/<slug>/WxH`) rather than curated Unsplash
-- photo ids — same photo every time for a given seed, and the URL always resolves (no risk of a
-- since-deleted Unsplash asset breaking the demo).

ALTER TABLE events ADD COLUMN image_url VARCHAR(500);

-- Backfill the 4 V2 demo events.
UPDATE events SET image_url = 'https://picsum.photos/seed/neon-nights-live/800/450' WHERE title = 'Neon Nights Live';
UPDATE events SET image_url = 'https://picsum.photos/seed/acoustic-evening/800/450' WHERE title = 'Acoustic Evening';
UPDATE events SET image_url = 'https://picsum.photos/seed/midnight-rehearsal/800/450' WHERE title = 'Midnight Rehearsal';
UPDATE events SET image_url = 'https://picsum.photos/seed/retro-fest/800/450' WHERE title = 'Retro Fest';

-- Backfill the 14 V3 developer/tech workshop events.
UPDATE events SET image_url = 'https://picsum.photos/seed/react-frontend-atolyesi/800/450' WHERE title = 'React ile Modern Frontend Geliştirme Atölyesi';
UPDATE events SET image_url = 'https://picsum.photos/seed/kubernetes-production/800/450' WHERE title = 'Kubernetes: Production''a Hazır mısınız?';
UPDATE events SET image_url = 'https://picsum.photos/seed/rust-sistem-programlama/800/450' WHERE title = 'Rust ile Sistem Programlama';
UPDATE events SET image_url = 'https://picsum.photos/seed/llm-uygulama-gelistirme/800/450' WHERE title = 'LLM Tabanlı Uygulama Geliştirme';
UPDATE events SET image_url = 'https://picsum.photos/seed/devops-ci-cd/800/450' WHERE title = 'DevOps ve CI/CD Pratikleri';
UPDATE events SET image_url = 'https://picsum.photos/seed/sistem-tasarimi/800/450' WHERE title = 'Sistem Tasarımı Derinlemesine';
UPDATE events SET image_url = 'https://picsum.photos/seed/go-mikroservis/800/450' WHERE title = 'Go ile Mikroservis Mimarisi';
UPDATE events SET image_url = 'https://picsum.photos/seed/veri-muhendisligi-kafka-spark/800/450' WHERE title = 'Veri Mühendisliğine Giriş: Apache Kafka ve Spark';
UPDATE events SET image_url = 'https://picsum.photos/seed/bulut-native-guvenlik/800/450' WHERE title = 'Bulut Native Güvenlik Uygulamaları';
UPDATE events SET image_url = 'https://picsum.photos/seed/graphql-api-tasarimi/800/450' WHERE title = 'GraphQL ile API Tasarımı';
UPDATE events SET image_url = 'https://picsum.photos/seed/test-otomasyonu/800/450' WHERE title = 'Test Otomasyonu ve Kalite Mühendisliği';
UPDATE events SET image_url = 'https://picsum.photos/seed/terraform-altyapi/800/450' WHERE title = 'Terraform ile Altyapıyı Kod Olarak Yönetmek';
UPDATE events SET image_url = 'https://picsum.photos/seed/flutter-mobil-gelistirme/800/450' WHERE title = 'Mobil Geliştirmede Flutter Derinlemesine';
UPDATE events SET image_url = 'https://picsum.photos/seed/yapay-zeka-gelistirme-araclari/800/450' WHERE title = 'Yapay Zeka Destekli Yazılım Geliştirme Araçları';
