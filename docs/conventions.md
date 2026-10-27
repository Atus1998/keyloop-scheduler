# Conventions

Code conventions for the Unified Service Scheduler (Keyloop challenge, Scenario A).
See [system-design.md](system-design.md) for the design.

## Language

- Code, comments, commit messages, README and docs are in English.

## Java

- No Lombok.
- Use `record` for DTOs and domain objects.
- Domain objects are immutable.
- JPA entities are kept separate from the domain model, because the domain layer has no Spring or database code.
- JPA entities are the one exception to immutability. JPA needs a no-argument constructor and mutable fields.
- The application layer has no Spring annotations; services are wired in `config`.

## Mapping

- Mapping between entities, domain objects and DTOs is written by hand. No MapStruct.
- Map into records through their constructors. A new record field then breaks the old constructor call at compile time, so a missing field cannot slip through.
- Revisit this choice if the number of mapping pairs grows past about 15 to 20.

## Formatting

- Spotless with google-java-format.
- It runs in the Gradle build and in CI. The build fails on bad formatting.

## Tests

- Test method names describe the behaviour in English, for example `returns409WhenTechnicianIsTaken()`.
- No `@DisplayName`.
- Use as few mocks as possible:
  - Domain tests use no mocks. The domain has no outside dependencies.
  - Application service and persistence adapter tests run against a real PostgreSQL through Testcontainers.
  - Use Mockito only when there is no simpler option.

## Documentation

- Short and easy to read. Few words. A reader should get it on the first pass.
- Show, don't tell. Prefer a diagram, table or example over a paragraph.
- Diagrams use Mermaid, so they render on GitHub and live next to the text.
- Plain English, short sentences, no marketing words.
- Mark anything not yet decided with `PROPOSED:`.
- Say each thing in one place. The README links to `docs/system-design.md` and does not repeat it.
- Every command in a doc (build, test, Docker Compose, cURL) is run before it is committed.
- A commit that changes behaviour (status code, reason, rule) updates the docs in the same commit.
- `README.md` sits at the root. Other docs sit in `docs/`.

## Commits

- Small commits. Each commit is one step, together with its tests.
- Use the format `<type>: <description>`, where type is one of `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`.
