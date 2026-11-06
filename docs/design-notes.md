# Design

The full design is in [system-design.md](system-design.md). This file holds only what the build workflow needs and does not repeat it.

## Chosen stack and why

Java 21 · Spring Boot 4.1 · Spring Data JPA · PostgreSQL 16 · Flyway · Gradle · JUnit 5 · Testcontainers 2 · springdoc-openapi 3 · Actuator + Micrometer · Docker Compose · GitHub Actions.

Reasons for each part: [system-design.md](system-design.md), section 7.

| Choice | Why | Cost |
|---|---|---|
| Spring Boot 4.1, not 3.5 | Spring Boot 3.5 lost free (OSS) support on 2026-06-30. 4.1 is the current release. | New versions to learn: Jackson 3, Hibernate 7, Testcontainers 2.0, springdoc-openapi 3.x. Fewer examples online. |
| Java 21, not 25 | Still an LTS release. Lower risk of tool support gaps. | None for this project. |

## Architecture: hexagonal (ports and adapters)

Diagram and layer roles: [system-design.md](system-design.md), sections 3 and 4. Classes per package:

```
com.keyloop.scheduler
├── domain                 records + BookingRules, TechnicianPicker, BayPicker, Reason (enum)
├── application            no Spring annotations
│   ├── port/in            BookAppointmentUseCase, GetAppointmentUseCase
│   ├── port/out           ReferenceDataPort, AppointmentPort, AvailabilityPort, TransactionPort,
│   │                      DealershipDayLockPort
│   └── service            BookAppointmentService, GetAppointmentService, RaceResolver, BookingOutcome,
│                          BookingRejectedException, ResourceConflictException (EXCLUSION | DEADLOCK),
│                          BookingQueueTimeoutException
├── adapter
│   ├── in/web             AppointmentController, request/response records, ApiExceptionHandler, RequestIdFilter
│   └── out/persistence    entities, Spring Data repos, *Adapter classes, SpringTransactionAdapter, SqlStates
└── config                 BeanConfig (@Bean wiring), ClockConfig
```

| Rule | How |
|---|---|
| Dependencies point inward | `adapter → application → domain`. ArchUnit test: `domain` and `application` import no `org.springframework` or `jakarta.persistence`; `application` does not use `adapter`. |
| One transaction per step | `TransactionPort` has two methods: `inTransaction(Supplier)` and `inReadOnlyTransaction(Supplier)`. No outer transaction, so a request holds one connection at a time. |
| DB errors | `SqlStates` walks the cause chain to `SQLException#getSQLState()`. It never relies on exception class names. |
| Time | `Clock` wired in `BeanConfig`. The domain gets `Instant now` as a parameter. |
| JPA | `open-in-view=false`. Entities stay in the persistence adapter; ports return domain records. |
| Seed data | Repeatable migration `db/seed/R__seed.sql`, safe to run twice. Loaded only with the `demo` profile (`application-demo.properties`), which Docker Compose and `bootTestRun` turn on. Tests insert their own data. |

## Risks

| Risk | Mitigation |
|---|---|
| `TransactionPort` grows or leaks Spring types | Keep two methods, no Spring types in signatures. |
| `BeanConfig` misses a bean | One context-start test; API tests use real beans. |
| Boot 4: with only `flyway-core`, migrations silently do not run | Use `spring-boot-starter-flyway` + `flyway-database-postgresql`. A test proves migrations ran. |
| Boot 4 test changes: no `@MockBean`, no default `TestRestTemplate`, bean overriding off | `@MockitoBean` / `@TestBean`, `RestTestClient`. Copy imports from Boot 4 docs. |
| Hibernate 7 exception classes vary; `ddl-auto=validate` may reject column types | Check SQLState. Fix mismatches with `columnDefinition`. |
| Jackson 3 may nest `reason` under `properties` | Use only Boot's mapper. A test asserts `$.reason` at top level. |
| springdoc 3 lags Boot releases | Pinned 3.1.1 (built on Boot 4.1.0; runs on 4.1.2). swagger-core brings Jackson 2 next to Jackson 3; only the spec uses it. |
| Testcontainers 2 renamed artifacts and packages | Use the Boot-managed version and `@ServiceConnection`. |
| Race tests can be flaky | Start-gate latch, a warm-up request, assert totals only, repeat 5 times, size the pool. Bookings queue per dealership and day, so a round takes tens of ms; `deadlock_timeout` (1 s) only matters when lines race, such as one vehicle at two dealerships. |
| Hidden dependence on JVM time zone | Run tests with a non-UTC `TZ`. |
| Compose starts the API before the DB is ready | `pg_isready -h 127.0.0.1` healthcheck (TCP, so not during init scripts) + `depends_on: service_healthy`. |
| Init script runs only on a new volume | `docker compose down -v` to start again; `scripts/compose-smoke.sh` uses its own project and volume. |
