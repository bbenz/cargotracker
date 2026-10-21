# syntax=docker/dockerfile:1

# Build the WAR with Maven. The cache mount keeps Maven downloads between builds.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Popenliberty -DskipTests package

# Run it on Open Liberty.
FROM icr.io/appcafe/open-liberty:kernel-slim-java21-openj9-ubi-minimal
COPY --chown=1001:0 --from=build /workspace/src/main/liberty/config/ /config/
RUN features.sh
COPY --chown=1001:0 --from=build /workspace/target/liberty/wlp/usr/shared/resources/hsqldb-*.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 --from=build /workspace/src/main/ai-docs/ /config/ai-docs/
COPY --chown=1001:0 --from=build /workspace/target/cargo-tracker.war /config/apps/cargo-tracker.war

# Model settings come from the environment at run time. The image never holds a key.
ENV AI_DOCS_DIR=/config/ai-docs
EXPOSE 8080

RUN configure.sh
