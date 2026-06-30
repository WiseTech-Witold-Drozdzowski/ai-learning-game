# syntax=docker/dockerfile:1

# --- Build stage (Gradle 9.6 + JDK 25) ---
FROM gradle:jdk25 AS build
WORKDIR /home/gradle/project
# Build files first — better caching of the dependency layers.
# Use the Gradle bundled in the image (9.6.1) — no wrapper distribution download.
COPY settings.gradle build.gradle ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true
# Then the rest of the sources.
COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

# --- Runtime stage (slim JRE 25) ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
