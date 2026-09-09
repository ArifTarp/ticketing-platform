---
name: docker-infra
description: The concrete procedure the docker-infra agent follows when adding or changing docker-compose.yml, a Dockerfile, or local infra stack setup for the ticketing platform. Use whenever touching containerized infra — not for application code in any language.
---

# Docker/local-infra change procedure

Infra changes are easy to get subtly wrong (a missing health check, a port collision, a service
that can silently reach a database it shouldn't) — verify with evidence, don't assume a compose
file is correct because it looks reasonable.

## 1. Confirm the exact requirements before writing YAML

For the service you're adding/changing, read its own `application.yml` for the real port,
datasource URL shape, and any Kafka/Redis config — don't guess these from the fixed ports table
in root `CLAUDE.md` alone; a service's actual config is the source of truth for what
docker-compose needs to override via env vars.

## 2. Enforce the database-isolation rule

Root `CLAUDE.md`: one Postgres database per service, no shared schema, no service's container
network-reachable to another service's database. When adding a service to compose, check its
`SPRING_DATASOURCE_URL` env var points only at its own database, and that no other service's
compose block grants it network access it shouldn't have.

## 3. Health checks and start order

Every infra dependency (Postgres, Kafka, Redis) needs a `healthcheck:` block, and every dependent
service needs `depends_on` with `condition: service_healthy` — not a bare `depends_on` (which only
waits for container start, not readiness) and not a fixed sleep/wait hack.

## 4. Verify by actually running it, not by reading the YAML

Bring the changed stack up (`docker compose up -d <changed services>`), then confirm with
`docker compose ps` that every container reaches `healthy`/`running` — and if a Dockerfile
changed, that `docker compose up --build` actually completes the build, not just that the
Dockerfile *looks* correct. A compose file that "should work" and one that's been proven to work
are different claims — only report the second.

## 5. Keep `run-ticketing-platform` in sync

This project's actual working local-dev method (documented in
`.claude/skills/run-ticketing-platform/SKILL.md`) exists specifically because compose doesn't yet
cover every service. Any change here that alters what compose can or can't do — a new Dockerfile
that makes a previously-Maven-only service now composable, a new port, a new env var — must be
reflected in that skill's runbook in the same change, not left to drift.

## 6. Report

State exactly what changed, what you verified (with the actual `docker compose ps`/build output,
not a description of what should happen), and hand off anything that's actually application code
(not a Dockerfile's build steps) to **backend-service** or **frontend**.
