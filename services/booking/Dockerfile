# Multi-stage build for the booking service.
#
# Build context MUST be the repo root (see docker-compose.yml's `context: .` /
# `dockerfile: services/booking/Dockerfile` for this service), not services/booking/ alone: this
# is a multi-module Maven build and booking depends on the sibling `messaging` module
# (com.demo.ticketing:messaging, see docs/adr/0003), so the build stage needs the parent pom and
# every module's sources, not just this one directory.
#
# Stage 1: build the booking jar (and its messaging dependency) with Maven against JDK 21.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy just the POMs first so Maven's dependency resolution is cached across rebuilds that only
# change application source, not dependency versions.
COPY pom.xml ./pom.xml
COPY messaging/pom.xml ./messaging/pom.xml
COPY gateway/pom.xml ./gateway/pom.xml
COPY services/auth/pom.xml ./services/auth/pom.xml
COPY services/event/pom.xml ./services/event/pom.xml
COPY services/booking/pom.xml ./services/booking/pom.xml
COPY services/payment/pom.xml ./services/payment/pom.xml
COPY services/notification/pom.xml ./services/notification/pom.xml

# Now the real sources for the modules this build actually needs (messaging + booking).
COPY messaging/src ./messaging/src
COPY services/booking/src ./services/booking/src

# Build only booking and its required reactor dependencies (-am), skip tests (this Dockerfile
# builds a deployable artifact; tests run separately via `mvn test`/CI, not on every image build).
RUN mvn -B -pl services/booking -am package -DskipTests

# Stage 2: slim JRE runtime image, plus the OpenTelemetry Java agent for traces/metrics export to
# apm-server (see docker-compose.yml's OTEL_* env vars on this service's block; the agent jar
# itself is bundled into the image here rather than downloaded at container start so startup
# doesn't depend on network access to GitHub).
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/services/booking/target/booking-*.jar /app/app.jar
COPY tools/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

EXPOSE 8083

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
