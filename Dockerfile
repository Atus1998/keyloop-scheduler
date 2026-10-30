# syntax=docker/dockerfile:1
# Builds the API from source, so a clean clone needs only Docker (no local JDK).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# Wrapper and build files first. The dependencies go to a BuildKit cache mount, not a layer:
# later builds on this machine reuse them; a new machine or CI downloads them again.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar \
    && cp build/libs/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
# Owned by root, so the app user can run the jar but not change it.
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
