# syntax=docker/dockerfile:1

# ---- build: compile and package (tests run in CI and `docker compose run --rm test`) ----
FROM eclipse-temurin:27-jdk AS build
WORKDIR /workspace

# Dependencies first, so source changes don't re-download them.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -x test \
 && java -Djarmode=tools -jar build/libs/poke-*.jar extract --layers --destination /workspace/extracted

# ---- runtime: JRE only, non-root ----
FROM eclipse-temurin:27-jre-alpine
RUN addgroup -S poke && adduser -S poke -G poke
WORKDIR /app

# Layers from least to most frequently changed, so a code change only replaces the last one.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER poke
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/poke-*.jar"]
