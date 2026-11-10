# syntax=docker/dockerfile:1

# Build with the Java and Maven versions already selected by this project.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
COPY --chmod=0755 mvnw ./mvnw
COPY pom.xml ./pom.xml

# Also support a Windows checkout with CRLF. Keep dependencies in a cached layer.
RUN sed -i 's/\r$//' mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src ./src
# Tests run during the image build; a failing test must fail the deployment.
RUN ./mvnw -B -ntp verify \
    && cp target/randomconnect-0.0.1-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /workspace/app.jar ./app.jar
COPY --chown=app:app --chmod=0755 docker-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh

# Leave room for non-heap JVM/native memory within a 512 MiB instance.
ENV SPRING_PROFILES_ACTIVE=render \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40.0 -XX:InitialRAMPercentage=10.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=16m -Xss512k -XX:+ExitOnOutOfMemoryError"

USER app:app
# Informational only: Spring resolves Render's PORT at runtime.
EXPOSE 10000
ENTRYPOINT ["/app/docker-entrypoint.sh"]
