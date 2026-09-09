---
name: run-ticketing-platform
description: Run, start, or launch the ticketing-platform app locally (infra + all backend services + gateway + frontend) on this dev machine, or check current run status, or stop everything cleanly. Use whenever asked to run/start/launch/restart/stop the app, or to verify a change works end to end. This is the project-specific runbook the built-in `run` skill looks for first — read this instead of guessing docker-compose or generic Java/Node run patterns.
---

# Running the ticketing platform locally

This repo's `docker-compose.yml` only stands up infra (Postgres, Kafka, Redis) — it does **not**
run the actual services (no Dockerfiles exist yet for booking/payment/notification, and
gateway/auth/event/frontend have no compose block at all — this is a known, tracked gap owned by
the **docker-infra** agent, not something to "fix" mid-task here). The verified working method
is: infra via Docker Compose, every Java service via Maven, frontend via `pnpm dev`. Do not
attempt `docker compose up --build` expecting the full stack — it will fail on missing
Dockerfiles for booking/payment/notification and simply won't touch gateway/auth/event/frontend
at all.

## 0. Machine-specific toolchain setup (check before any `mvn`/`mvnw` command)

This machine does not have Java/Maven reliably on `PATH` for every fresh shell. Prefer the
repo's own wrapper — `./mvnw` (bash) / `.\mvnw.cmd` (PowerShell), confirmed present at the repo
root — over a bare `mvn` invocation; it's pinned to the right Maven version. The wrapper still
needs a JVM it can find, so export `JAVA_HOME` at the top of every command that shells out to it
if `java`/`mvn` aren't already resolving:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:MAVEN_HOME = "C:\Users\Arif\tools\apache-maven-3.9.9"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
```

If `mvn`/`java`/`docker compose`/`pnpm` are missing entirely (not just off `PATH` for this
shell), check the project's `ticketing_local_toolchain` memory first — that's a machine setup
problem with a known fix, not a real regression to re-diagnose from scratch.

## 1. Start infra

```powershell
docker compose up -d postgres kafka kafka-topics-init redis
```

Confirm before moving on — don't just assume it worked:

```powershell
docker compose ps
```

`postgres` and `kafka` should show healthy/running; `kafka-topics-init` should show
`Exited (0)` (it's a one-shot topic-creation job — nonzero means topics didn't get created and
every service will fail to consume/produce until it's re-run).

## 2. Start each Java service (one background task per service)

Each service's own `application.yml` already defaults to `localhost` for Postgres/Kafka/Redis —
no extra flags needed except the gateway's port override in step 3. Launch each as its own
background task (not chained in one shell) so it can be monitored/stopped independently:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:MAVEN_HOME = "C:\Users\Arif\tools\apache-maven-3.9.9"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
.\mvnw.cmd -pl services/auth spring-boot:run
```

Repeat for `services/event`, `services/booking`, `services/payment`, `services/notification`
(same three `$env:` lines, different `-pl` module).

## 3. Start the gateway — port note

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:MAVEN_HOME = "C:\Users\Arif\tools\apache-maven-3.9.9"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
.\mvnw.cmd -pl gateway "-Dspring-boot.run.arguments=--server.port=8086" spring-boot:run
```

**Why port 8086, not the documented 8080:** on this specific dev machine, port 8080 is
permanently occupied by a pre-existing, unrelated Windows `Tomcat10.exe` service (not started by
any session). `frontend/.env.local` already has `NEXT_PUBLIC_API_BASE_URL=http://localhost:8086`
to match — do not "fix" that back to 8080 on this machine.

**On a different machine without that conflict:** drop the `-Dspring-boot.run.arguments`
override, run the gateway on its default 8080, and either delete `frontend/.env.local` (falls
back to the 8080 default baked into `frontend/lib/constants.ts`) or set its
`NEXT_PUBLIC_API_BASE_URL` to `http://localhost:8080` explicitly.

## 4. Start the frontend

```powershell
pnpm dev
```
Run from `frontend/`, as its own background task. If `pnpm` isn't found, install it once
globally (`npm install -g pnpm`) — a one-time machine setup step, not a bug.

## 5. Health-check before declaring "ready"

Poll each in order — don't call the stack ready until every row responds:

| Component     | Port                      | Check |
|----------------|---------------------------|-------|
| Postgres       | 5432                      | `docker compose ps` shows healthy |
| Kafka          | 9092                      | `docker compose ps` shows healthy, `kafka-topics-init` exited 0 |
| auth           | 8081                      | `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health` → `200` |
| event          | 8082                      | same pattern |
| booking        | 8083                      | same pattern |
| payment        | 8084                      | same pattern |
| notification   | 8085                      | same pattern (no inbound gateway route, but the actuator port itself should still answer) |
| gateway        | 8086 (this machine) / 8080 (default) | same pattern |
| frontend       | 3000                      | request to `http://localhost:3000` returns 200 |

If a Java service fails to bind or crashes on startup, check its background task's captured
output for a Postgres/Kafka connection refusal before assuming a code bug — it usually means
step 1 wasn't actually healthy yet when the service started.

## 6. Checking status without restarting anything

- Infra: `docker compose ps`. Never re-run `docker compose up` for containers already showing
  `running`/`healthy`.
- Services/frontend: re-run the port health checks from step 5, or check the background task's
  status directly. Never blindly re-invoke `spring-boot:run`/`pnpm dev` for something already
  up — check first.
- Find which process owns a port (useful when a health check unexpectedly fails and something
  else may be squatting on it):
  ```powershell
  Get-NetTCPConnection -LocalPort 8086 -State Listen | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Get-Process -Id $_ }
  ```

## 7. Stopping everything cleanly

Stop in reverse order: frontend → gateway → each Java service → infra.

1. Stop each frontend/gateway/service background task.
2. **Known gotcha, verified in an earlier session:** stopping a `mvn`/`mvnw spring-boot:run`
   background task kills the Maven parent process, but the forked child JVM running the actual
   Spring Boot app can survive and keep holding its port. After stopping each Java service,
   verify the port is actually free and force-kill if not — do this for every service port
   (8081-8085) and whichever gateway port is in use (8086 on this machine, 8080 by default):
   ```powershell
   $port = 8083  # repeat per port
   $procId = (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).OwningProcess
   if ($procId) { Stop-Process -Id $procId -Force }
   ```
3. `docker compose stop postgres kafka kafka-topics-init redis` — prefer plain `stop` over
   `down` so Postgres data survives between sessions, unless a full reset (fresh volumes) was
   actually asked for.

## Scope note

This skill only orchestrates already-existing run commands. It never edits
`docker-compose.yml`, writes a Dockerfile, or changes any service's `application.yml` — that's
**docker-infra**'s job. If infra itself needs to change (a new Dockerfile, a new compose service
block, fixing the missing-Dockerfile gap for booking/payment/notification), hand off to that
agent instead of patching around it here, and it will keep this file in sync as part of that
change.
