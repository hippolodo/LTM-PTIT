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

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /workspace/app.jar ./app.jar

# Leave room for non-heap JVM/native memory within a 512 MiB instance.
ENV SPRING_PROFILES_ACTIVE=render \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:InitialRAMPercentage=10.0 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+ExitOnOutOfMemoryError"

USER app:app
# Informational only: Spring resolves Render's PORT at runtime.
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
