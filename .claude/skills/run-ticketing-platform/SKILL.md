---
name: run-ticketing-platform
description: Run, start, or launch the ticketing-platform app locally (infra + all backend services + gateway + frontend) on this dev machine, or check current run status, or stop everything cleanly. Use whenever asked to run/start/launch/restart/stop the app, or to verify a change works end to end. This is the project-specific runbook the built-in `run` skill looks for first — read this instead of guessing docker-compose or generic Java/Node run patterns.
---

# Running the ticketing platform locally

`docker-compose.yml` now stands up infra (Postgres, Kafka, Redis, and the observability stack —
Elasticsearch, Kibana, Elastic APM Server) **and** booking/payment/notification, each with its own
Dockerfile (multi-stage Maven build + slim JRE, OpenTelemetry Java agent baked in). It still does
**not** cover gateway/auth/event/frontend — no compose block exists for them at all; this remains
a known, tracked gap owned by the **docker-infra** agent, not something to "fix" mid-task here.
The verified working method for the whole stack is: infra + observability + booking/payment/
notification via Docker Compose, gateway/auth/event via Maven, frontend via `pnpm dev`.
`docker compose up --build` will build and start postgres/kafka/redis/elasticsearch/kibana/
apm-server/booking/payment/notification correctly, but will still leave gateway/auth/event/
frontend untouched — start those the Maven/pnpm way below.

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

The OpenTelemetry Java agent jar used below (`tools/opentelemetry-javaagent.jar`, currently
v2.31.1) is gitignored (matches the repo's blanket `*.jar` ignore rule) and downloaded once per
clone/machine, not committed. If it's missing:

```powershell
mkdir tools -ErrorAction SilentlyContinue
Invoke-WebRequest -Uri "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar" -OutFile "tools\opentelemetry-javaagent.jar"
```

## 1. Start infra + observability

```powershell
docker compose up -d postgres kafka kafka-topics-init redis elasticsearch kibana apm-server
```

Confirm before moving on — don't just assume it worked:

```powershell
docker compose ps
```

`postgres`, `kafka`, `redis`, `elasticsearch`, `kibana`, and `apm-server` should all show
healthy/running; `kafka-topics-init` should show `Exited (0)` (it's a one-shot topic-creation job
— nonzero means topics didn't get created and every service will fail to consume/produce until
it's re-run). Elasticsearch/Kibana/APM Server take longer to report healthy than Postgres/Kafka/
Redis (Elasticsearch alone can take 30-60s) — don't declare infra broken just because they're
still `starting` a few seconds after `up -d` returns.

Kibana's APM UI (traces from every OTel-instrumented service) is at `http://localhost:5601/app/apm`.
Raw trace documents are queryable directly: `curl http://localhost:9200/traces-apm*/_search`.

## 2a. Start booking/payment/notification via Docker Compose (preferred — matches production shape)

```powershell
docker compose up -d --build booking payment notification
```

Each already has a `depends_on: condition: service_healthy` on its Postgres/Kafka/Redis/apm-server
dependencies, so this is safe to run right after step 1 without manually polling readiness first.
Each image bundles the OpenTelemetry Java agent and is pre-wired via compose env vars
(`OTEL_EXPORTER_OTLP_ENDPOINT=http://apm-server:8200`, etc. — see `docker-compose.yml`) to export
traces to the apm-server started in step 1.

**Port-conflict note:** if a port (8083/8084/8085) is already bound by a Maven-launched instance
of the same service from a previous session (step 2b below), `docker compose up` for that service
will fail with `ports are not available`. Stop the Maven-launched instance first (see "Stopping
everything cleanly") rather than running both at once — never silently rebind to a different host
port, since that would drift from the fixed ports table in root `CLAUDE.md`.

## 2b. Start gateway/auth/event via Maven (no compose block exists for these yet)

Each service's own `application.yml` already defaults to `localhost` for Postgres/Kafka/Redis —
no extra flags needed except the gateway's port override in step 3 and the OTel env vars below
(these run outside Docker's network, so `OTEL_EXPORTER_OTLP_ENDPOINT` points at `localhost:8200`,
not `apm-server:8200`). Launch each as its own background task (not chained in one shell) so it
can be monitored/stopped independently:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:MAVEN_HOME = "C:\Users\Arif\tools\apache-maven-3.9.9"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
$env:JAVA_TOOL_OPTIONS = "-javaagent:$PWD\tools\opentelemetry-javaagent.jar"
$env:OTEL_SERVICE_NAME = "auth"
$env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:8200"
$env:OTEL_EXPORTER_OTLP_PROTOCOL = "http/protobuf"
$env:OTEL_RESOURCE_ATTRIBUTES = "service.namespace=ticketing-platform,deployment.environment=local"
$env:OTEL_LOGS_EXPORTER = "none"
$env:OTEL_METRICS_EXPORTER = "none"
$env:OTEL_INSTRUMENTATION_LOGBACK_MDC_ENABLED = "true"
.\mvnw.cmd -pl services/auth spring-boot:run
```

Repeat for `services/event` (same block, `-pl services/event`, `OTEL_SERVICE_NAME = "event"`).
`$env:JAVA_TOOL_OPTIONS` is picked up by the JVM the Maven-forked `spring-boot:run` process
launches, so the agent attaches even though Maven itself (not `java -jar`) is the direct parent —
Java prints `Picked up JAVA_TOOL_OPTIONS: ...` to stderr on startup as confirmation it took effect.

**If you'd rather run booking/payment/notification via Maven instead of Docker Compose** (e.g. for
faster local iteration without a rebuild), the same pattern works — just swap `OTEL_SERVICE_NAME`
and `-pl` and skip the `docker compose up ... booking payment notification` step in 2a for that
service to avoid the port conflict noted there.

## 3. Start the gateway — port note

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:MAVEN_HOME = "C:\Users\Arif\tools\apache-maven-3.9.9"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
$env:JAVA_TOOL_OPTIONS = "-javaagent:$PWD\tools\opentelemetry-javaagent.jar"
$env:OTEL_SERVICE_NAME = "gateway"
$env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:8200"
$env:OTEL_EXPORTER_OTLP_PROTOCOL = "http/protobuf"
$env:OTEL_RESOURCE_ATTRIBUTES = "service.namespace=ticketing-platform,deployment.environment=local"
$env:OTEL_LOGS_EXPORTER = "none"
$env:OTEL_METRICS_EXPORTER = "none"
$env:OTEL_INSTRUMENTATION_LOGBACK_MDC_ENABLED = "true"
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
| Elasticsearch  | 9200                      | `docker compose ps` shows healthy |
| Kibana         | 5601                      | `docker compose ps` shows healthy |
| APM Server     | 8200                      | `docker compose ps` shows healthy |
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

**Confirming traces are actually flowing (don't just trust that OTel env vars were set):**
```powershell
curl "http://localhost:9200/traces-apm*/_search?size=1&sort=@timestamp:desc"
```
A hit with `service.name` matching the service you just exercised confirms the agent successfully
exported to apm-server and apm-server successfully wrote to Elasticsearch. An empty
`hits.hits: []` after a service has been up and received traffic for >30s usually means either the
`OTEL_EXPORTER_OTLP_ENDPOINT` was wrong for where that process runs (containerized services use
`http://apm-server:8200`; Maven-launched ones use `http://localhost:8200` — see steps 2b/3 above),
or `JAVA_TOOL_OPTIONS`/`-javaagent` wasn't actually picked up (check the process's stderr for the
`opentelemetry-javaagent - version:` startup line).

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

Stop in reverse order: frontend → gateway → each Java service (Maven or Docker, whichever way it
was started) → infra/observability.

1. Stop each frontend/gateway/service background task (Maven-launched) and/or
   `docker compose stop booking payment notification` (Docker-launched, per step 2a).
2. **Known gotcha, verified in an earlier session:** stopping a `mvn`/`mvnw spring-boot:run`
   background task kills the Maven parent process, but the forked child JVM running the actual
   Spring Boot app can survive and keep holding its port. After stopping each Maven-launched Java
   service, verify the port is actually free and force-kill if not — do this for every service
   port that was Maven-launched and whichever gateway port is in use (8086 on this machine, 8080
   by default):
   ```powershell
   $port = 8083  # repeat per port
   $procId = (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).OwningProcess
   if ($procId) { Stop-Process -Id $procId -Force }
   ```
3. `docker compose stop postgres kafka kafka-topics-init redis elasticsearch kibana apm-server` —
   prefer plain `stop` over `down` so Postgres/Elasticsearch data survives between sessions,
   unless a full reset (fresh volumes) was actually asked for.

## Scope note

This skill only orchestrates already-existing run commands. It never edits
`docker-compose.yml`, writes a Dockerfile, or changes any service's `application.yml` — that's
**docker-infra**'s job. If infra itself needs to change (a new Dockerfile, a new compose service
block, a new/changed port, a new OTel env var), hand off to that agent instead of patching around
it here, and it will keep this file in sync as part of that change.
