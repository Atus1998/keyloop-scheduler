# keyloop-scheduler

Backend API that books dealership service appointments without double booking. No UI. Keyloop challenge, Scenario A.

- **System design:** [docs/system-design.md](docs/system-design.md): rules and assumptions, architecture, data flow, decisions, observability.
- **AI collaboration:** [narrative below](#ai-collaboration-narrative).
- **Video:** [walkthrough below](#video-walkthrough).

Java 21, Spring Boot 4.1, PostgreSQL 16, Gradle. Two ways to run it: Docker Compose (no JDK needed) or Gradle on a throwaway database.

## Run with Docker Compose

Needs Docker and a free port 8080. No local JDK.

```bash
docker compose up --build
```

The API is at `http://localhost:8080` once `/actuator/health` shows `"db":{"status":"UP"}`. The database is loaded with demo data (profile `demo`) and is not reachable from the host.

Book an appointment two days from now at 09:00 Saigon time, at dealership 1 for vehicle 1, service type 1:

```bash
START="$(date -v+2d +%F 2>/dev/null || date -d '+2 days' +%F)T09:00:00+07:00"
curl -i -X POST http://localhost:8080/appointments \
  -H 'Content-Type: application/json' \
  -d "{\"dealershipId\": 1, \"vehicleId\": 1, \"serviceTypeId\": 1, \"startTime\": \"$START\"}"
```

The answer is `201 Created` with a `Location` header. Sending the same request again returns `200` with the same appointment.

Swagger UI: `http://localhost:8080/swagger-ui.html`.

Stop, and start again from an empty database (the init script that creates the database users runs only on a new volume):

```bash
docker compose down -v
```

## Check the Compose setup

Builds, starts, books, restarts on the same volume and cleans up, under its own project name:

```bash
./scripts/compose-smoke.sh
```

## Run from source

Needs JDK 21 and Docker (Testcontainers starts `postgres:16`). Port 8080 must be free.

```bash
./gradlew bootTestRun
```

The app starts with the same demo data as Compose, so the cURL above works unchanged. `Ctrl+C` stops it and removes the database.

## Project layout

Hexagonal: `domain` and `application` have no Spring or JPA imports. Class-level detail: [docs/design-notes.md](docs/design-notes.md).

| Path | What |
|---|---|
| `src/main/java/com/keyloop/scheduler/domain` | Records and booking rules |
| `src/main/java/com/keyloop/scheduler/application` | Use cases and ports |
| `src/main/java/com/keyloop/scheduler/adapter` | `in/web` (REST) and `out/persistence` (JPA) |
| `src/main/java/com/keyloop/scheduler/config` | `@Bean` wiring |
| `src/main/resources/db` | Flyway migrations and demo seed |
| `scripts/compose-smoke.sh` | Compose smoke test |

## Develop

| What | Command |
|---|---|
| Tests (Docker must be running) | `./gradlew test` |
| Full check (format + tests) | `./gradlew build` |
| Run on a throwaway PostgreSQL with demo data | `./gradlew bootTestRun` |

## Assumptions and limits

The brief leaves many rules open. My choices are in [section 2 of the design](docs/system-design.md#2-rules-and-assumptions). The main limits:

- No authentication.
- One opening window per day for every dealership: no holidays, no shifts.
- Bookings for one dealership and day wait in line. A wait over 2 s gives 503 and must be sent again ([why](docs/system-design.md#c-parallel-requests-a-queue-per-dealership-and-day)).
- A database timeout gives 500.

## AI collaboration narrative

**Tools.** Claude in chat to question the brief and critique the plan. Claude Code to write the documents, tests and code. ChatGPT/Codex to review and test the work.

**Strategy: I decide, the AI drafts.**

- I fixed the rules and the design before any code ([design](docs/system-design.md), sections 2 and 8). How I used AI in that phase: section 10.
- The AI works under written rules in [`docs/conventions.md`](docs/conventions.md). For example: no Spring or JPA in `domain` and `application`, tests on real PostgreSQL and never H2, never edit a committed migration, update the docs in the same commit as the behaviour.
- The work is cut into 12 small features, one branch each. Each one stops for my review, and I merge it.

**Verification: every feature follows the same steps, visible in `git log`.**

1. Failing tests first. The `test: … (red)` commit says how many tests fail and why.
2. Code until the tests pass (`feat: …`).
3. A separate review pass. Findings are fixed in `fix: address review (round N)`. Findings left for later were recorded as follow-up work.
4. The AI may not change a red test to make the code pass. Twice a red test had to change, only with my approval, and the commit says why:
   - the parallel same-vehicle test also accepts `SLOT_TAKEN`, because losers can deadlock twice before the winner commits;
   - `statement_timeout` is 30 s, not 10 s, because ten racing requests sometimes needed about 20 s.

**Where the AI was wrong, and how it was caught.**

- It set `hibernate.jdbc.time_zone=UTC`, which shifted the dealerships' opening hours by the JVM offset. It was caught before the commit and removed; times are stored as UTC without it, and a test checks that.
- Review found that the database conflict exception carried SQL details (constraint names, ids) that an error handler could send to the client. Errors now return a fixed message, and the log keeps only the conflict kind.
- A smoke-test check also passed when the network failed, not only when PostgreSQL refused the connection. Review caught it; the check now looks for PostgreSQL's own refusal message. A test that passes for the wrong reason is worse than no test.
- Before each feature, I reviewed the plan and asked the AI to explain its assumptions and how the steps fit the existing code. Sometimes that showed the plan was more complex than the feature needed. I asked for a simpler plan and asked the AI to apply that lesson in later planning.

**Final quality.**

- The race rule is proven over real HTTP: 10 parallel requests for one slot, with 1, 3 or 10 technicians and bays, repeated 5 times. Exactly as many 201s as free slots, the others 409 with the real reason, never 500 ([`BookAppointmentConcurrencyApiTest`](src/test/java/com/keyloop/scheduler/adapter/in/web/BookAppointmentConcurrencyApiTest.java)).
- All tests run on PostgreSQL 16 through Testcontainers. ArchUnit checks the layer rules, Spotless checks the format, and CI runs `./gradlew build` on every push. `scripts/compose-smoke.sh` checks the Docker stack end to end.
- At the end I trimmed the suite: tests of framework and infrastructure details were removed, so the tests describe booking behaviour, not configuration.
- I read each diff before merging it.

## Video walkthrough

The walkthrough video is available on [Google Drive](https://drive.google.com/drive/folders/168EYHY0fDl1ZPS7R-mCwY2ifN-FZZm2N?usp=sharing).
