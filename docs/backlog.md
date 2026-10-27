# Backlog

One line = one branch. Order matters. Rules and reason codes: [system-design.md](system-design.md), section 2.

| # | Feature | Done when | Needs |
|---|---|---|---|
| 1 | CI on GitHub Actions | A push runs `./gradlew build` (Spotless + Testcontainers tests) and it is green. | — |
| 2 | Schema, constraints, seed, reference-data adapter | Adapter tests: overlapping `CONFIRMED` rows give `ResourceConflictException(EXCLUSION)`; back-to-back and `CANCELLED` overlaps insert; `closes_at <= opens_at` is rejected. | — |
| 3 | Domain rules and pickers | Plain tests, no Spring: every 422 rule; day 90 allowed, day 91 rejected; both DST change days; fewest-load and tie rules. | — |
| 4 | `GET /appointments/{id}` + `ApiExceptionHandler` + `ArchitectureTest` | 200 for a stored row; 404 `APPOINTMENT_NOT_FOUND`; `/appointments/abc` gives 400; ArchUnit rules pass. | 2 |
| 5 | `POST /appointments` happy path (use case, `BeanConfig`, `TransactionPort`) | 201 + `Location`, and GET returns the same body; 400 for bad shape; 404 for each unknown id. | 3, 4 |
| 6 | Booking rejections | One API test per row of the booking checks table: right status and `reason`. | 5 |
| 7 | Sequential safe retry | The same body twice: 201, then 200 with the same id. | 5 |
| 8 | Race handling + proof tests | N ≥ 10 parallel requests for one slot: one 201, the rest 409 with a reason. N identical parallel requests: one 201, the rest 200. No 500. Repeated 5 times. | 6, 7 |
| 9 | Observability | `X-Request-Id` on every response; JSON logs carry it; `/actuator/health` shows db UP; POST latency is recorded. | 5 |
| 10 | OpenAPI + Swagger UI | Swagger UI loads; a test on `/v3/api-docs` finds both paths and all status codes. | 5 |
| 11 | Dockerfile + Docker Compose | From a clean clone, `docker compose up` gives health UP and the README cURL returns 201. | 5 |
| 12 | README | Every command in it has been run. It links to `system-design.md` and repeats nothing. | 8–11 |

Items 2 and 3 can run in parallel.
