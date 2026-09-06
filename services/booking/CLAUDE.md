# CLAUDE.md — booking-service

> Loaded when working inside `services/booking/`. See root `CLAUDE.md` for shared conventions.

Placeholder — populated in Phase 7 (bookings CRUD + Redis seat holds, concurrent seat-race
handling) and Phase 8 (checkout saga wiring). Owns: seat availability state, holds (Redis TTL),
bookings, saga orchestration (booking is the orchestrator, per ADR-0001).
