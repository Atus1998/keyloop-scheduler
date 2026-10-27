# keyloop-scheduler

Keyloop challenge, Scenario A: a backend API that books dealership service appointments without double booking. No UI.

## Commands

| What | Command |
|---|---|
| Tests | `./gradlew test` |
| Full check (format + tests) | `./gradlew build` |
| Fix formatting | `./gradlew spotlessApply` |
| Run the app with a throwaway PostgreSQL | `./gradlew bootTestRun` |

Docker must be running: tests and `bootTestRun` start `postgres:16` through Testcontainers.

## Where things are

| Path | What |
|---|---|
| `docs/system-design.md` | Rules, data model, API, race handling. The source of truth for behaviour. |
| `docs/thiet-ke.md` | Stack, package layout (hexagonal), risks. |
| `docs/conventions.md` | Code, test, doc and commit conventions. Follow them. |
| `docs/yeu-cau.md`, `docs/backlog.md` | Requirements and the ordered feature list. |
| `src/main/java/com/keyloop/scheduler` | `domain`, `application` (`port/in`, `port/out`, `service`), `adapter/in/web`, `adapter/out/persistence`, `config`. |

Key rules:

- `domain` and `application` have no Spring or JPA imports. Wire services with `@Bean` in `config`.
- Tests run against real PostgreSQL 16 through Testcontainers. Never H2.

## Do not touch

- `gradle/wrapper/` except to upgrade Gradle on purpose.
- A Flyway migration that is already committed. Add a new one instead.
- The marked block in `docs/tien-do.md`. Only `/project-start` and `/feature` edit it.
