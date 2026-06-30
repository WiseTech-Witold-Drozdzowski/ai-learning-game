# Backend — AI Career Coach

Skeleton (project shell) of the backend. Modular monolith per
[`docs/design/TECHNICAL_DESIGN.md`](../docs/design/TECHNICAL_DESIGN.md) and
[`docs/design/BACKEND_DESIGN.md`](../docs/design/BACKEND_DESIGN.md).

## Stack

- Java 25 (LTS) + Spring Boot 4.1.0
- Gradle 9.6 (wrapper included)
- PostgreSQL 17 + Flyway
- Spring Security + OAuth2 client (temporarily open — see `auth/SecurityConfig`)

## Requirements

Only **Docker** + **docker compose** — no local Java/Gradle needed (everything
runs in containers). A local JDK 25 is optional, only if you want to run Gradle
directly (`./gradlew ...`).

## Module layout (`src/main/java/com/careercoach`)

```
goals tasks jobs coach ai gamification calendar auth config
```

Empty modules carry a `package-info.java` describing their responsibility.
The only fleshed-out slice is a thin vertical proof of wiring:
`PingController` (web), `Job` + `JobRepository` (JPA), `V1__init_skeleton.sql`
(Flyway).

## Run the tests (in Docker)

```bash
./run-tests.sh
```

Spins up a throwaway Postgres and runs the suite against it in a Gradle/JDK 25
container. Exit code = test result.

Temporary smoke tests included:
- `SmokeUnitTest` — pure unit (no Spring), confirms the toolchain.
- `PingControllerTest` — web layer (`@WebMvcTest`).
- `JobPersistenceTest` — full path: Spring context → Flyway → JPA → Postgres.

## Run the app locally

```bash
docker compose up --build
```

App on http://localhost:8080, Postgres on `localhost:5432`.

```bash
curl http://localhost:8080/api/ping
curl http://localhost:8080/actuator/health
```
