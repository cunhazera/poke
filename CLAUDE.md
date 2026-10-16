# Poke: agent instructions

Technical assessment: a full-stack Pokémon app (Spring Boot + PokeAPI). The spec is `tech.pdf`.
Read `HANDOFF.md` for context and `WORK_TRACKER.md` for progress and decisions before starting work.

Full requirements (keep them in mind for every change): @docs/assessment-spec.md

## Architecture (non-negotiable)
Uncle Bob's Clean Architecture, layer-first under `com.app.poke`:
`domain` → `application` (`port.in`, `port.out`, `usecase`) → `adapter` (`in.web`, `out.*`) → `config`.

- **Dependencies point inward only.** `domain` and `application` contain no framework code.
- **Before creating or moving any backend class, load the `clean-architecture` skill**
  (`.claude/skills/clean-architecture/SKILL.md`). It says where every kind of class goes and how to name it.
- `CleanArchitectureTest` (ArchUnit) enforces this. Never weaken it to make code pass.
- **Database:** PostgreSQL 18; the schema is owned by Flyway (`src/main/resources/db/migration`). **Never edit an applied migration**; add `V<n+1>`. `@SpringBootTest`s import `support/TestcontainersConfiguration` (Docker must be running).

## Workflow
- **TDD:** write the failing test first, then the code, working from the inside out (domain → use case → adapters → wiring).
- After finishing a story's endpoints, add or extend its collection in `api-tests/collections/` and run `api-tests/run.sh` against the running server (see `api-tests/README.md`).
- After each meaningful step, update `WORK_TRACKER.md` (progress, decisions, AI corrections) and the "Current state" section of `HANDOFF.md`.

## Build
JDK 27 is only available Gradle-provisioned:
```
JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-27-aarch64-os_x.2/jdk-27+35/Contents/Home ./gradlew build
```
