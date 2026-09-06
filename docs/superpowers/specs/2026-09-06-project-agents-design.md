# Proje Bazlı Claude Code Agent Kurulumu — Tasarım

> Spec durumu: onaylandı (kullanıcı, 2026-09-06). Sonraki adım: writing-plans skill ile
> implementasyon planı.

## Amaç

Ticketing platform monorepo'sunda tekrarlayan uzmanlık alanları (backend servis implementasyonu,
frontend implementasyonu, checkout saga'sı, Kafka messaging, Docker/infra, gateway/resilience) ve
mimari danışmanlık ihtiyaçları (backend/frontend architecture review) için proje bazlı Claude Code
agent'ları tanımlamak. Henüz kod yazılmadığı için bu agent'lar, vertical slice implementasyonuna
başlarken doğru sınırlar ve convention'larla çalışmayı garanti edecek.

## Kapsam

Sadece **agent** tanımları (`.claude/agents/*.md`). Custom skill'ler (tekrarlayan workflow'lar,
örn. "yeni servis iskeleti oluştur") bu turun kapsamı dışında — ihtiyaç doğdukça ayrı bir
brainstorm ile eklenecek.

## Yapı

Tüm agent dosyaları repo kökünde `.claude/agents/` altında **flat** (klasörsüz) duracak — Claude
Code agent'ları dizin bazlı scope'lanmıyor, sadece isimle çağrılıyor. Her dosya:

```markdown
---
name: <agent-adı>
description: <ne zaman kullanılacağı, tek cümle>
tools: <gerekli araç listesi>
model: <opsiyonel override>
---

<talimat gövdesi: uzmanlık alanı, kendi sınırları, CLAUDE.md/servis CLAUDE.md'lerine referans,
naming convention hatırlatması, ne zaman başka bir agent'a devretmesi gerektiği>
```

## Agent listesi

### İmplementasyon agent'ları

1. **backend-service** — auth/event/booking/payment/notification servislerinin ortak Spring Boot
   implementasyon agent'ı. Entity/repository/service/controller katmanları, Flyway migration,
   JUnit 5 + Testcontainers testleri. Hangi serviste çalıştığını dosya yolundan/context'ten anlar.
   Saga mantığı, Kafka producer/consumer detayı ve gateway'e dokunmaz — bunlar için ilgili
   cross-cutting agent'a yönlendirir.
2. **frontend** — Next.js App Router implementasyonu: sayfalar, component'ler, hook'lar, API
   client fonksiyonları, Vitest (unit) + Playwright (E2E) testleri. `frontend/CLAUDE.md`
   convention'larına (PascalCase component, camelCase hook/util, route yapısı) uyar.
3. **saga-orchestrator** — booking servisinin checkout saga'sına özel: `SagaState` yönetimi,
   idempotent consumer tasarımı, timeout sweep (hold TTL expiry), outbox pattern, dead-letter
   path. Sadece booking servisi kapsamında çalışır; genel Kafka altyapısı için message-broker
   agent'ına danışır.
4. **message-broker** — Kafka topic/şema tasarımı, producer/consumer boilerplate (Spring Kafka),
   event/command DTO'ları (`*Event`/`*Command` naming), DLQ konfigürasyonu, event versioning.
   Saga akışının iş mantığına girmez — sadece messaging altyapısını sağlar.
5. **docker-infra** — `docker-compose.yml`, servis Dockerfile'ları, local stack (Postgres, Kafka,
   Redis, tüm servisler, frontend), health check ve servisler arası network ayarları.
6. **gateway-resilience** — Spring Cloud Gateway route tanımları, JWT validation filtresi,
   Resilience4j (circuit breaker + timeout + retry) konfigürasyonu. İş mantığı yazmaz (CLAUDE.md
   kuralı: "keep business logic out of the gateway").

### Danışman agent'ları (kod yazmaz)

7. **backend-architecture** — servis sınırları, katmanlama (`domain/application/web/infra`),
   yeni servis eklenirken modül yapısı, ADR taslağı hazırlama. backend-service/saga-orchestrator/
   message-broker/gateway-resilience agent'ları büyük kararlarda buna danışabilir ama bu agent
   kendisi implementasyon yapmaz.
8. **frontend-architecture** — route/sayfa yapısı, state yönetimi yaklaşımı, component sınırları,
   API entegrasyon stratejisi kararlarında frontend agent'ına danışılan; kod yazmayan agent.

## Ortak ilkeler (her agent dosyasında tekrarlanacak)

- Kök `CLAUDE.md` ve varsa ilgili servisin `CLAUDE.md`'si otoriter kaynaktır; çelişki durumunda
  servis-özel dosya öncelikli.
- Servis sınırlarına uy: shared DB yok, cross-service DB read yok, service-to-service REST yok
  (gateway/Kafka üzerinden).
- Naming convention'lara (Entity/Repository/Service/Controller/DTO/Kafka topic/event/command
  suffix kuralları) harfiyen uy.
- Kendi uzmanlık alanının dışına taşan bir iş geldiğinde, hangi agent'a devretmesi gerektiğini
  açıkça belirt (örn. backend-service bir Kafka consumer'ı kendisi yazmaya kalkışmadan önce
  message-broker'a yönlendirir).

## Doğrulama

- 8 agent dosyası da geçerli frontmatter (`name`, `description`, `tools`) içermeli.
- Her dosyanın `description` alanı, ne zaman tetikleneceğini net anlatmalı (Claude Code bunu
  otomatik seçim için kullanır).
- Manuel test: `Agent` tool ile her agent adını çağırıp kısa bir görevle (örn. backend-service'e
  "auth servisi için User entity'sini tanımla" gibi) doğru sınırlar içinde davrandığını doğrulamak
  (bu spec kapsamında değil, implementasyon planında ele alınacak).
