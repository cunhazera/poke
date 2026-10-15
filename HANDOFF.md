# Project Handoff

> Read this first when starting a new session. It holds the context needed to resume work.
> For progress history and decisions, see [WORK_TRACKER.md](WORK_TRACKER.md).

## What this is
A technical assessment for a Java position at **Ballast Lane**. The full spec is in `tech.pdf` at the repo root, and it is the source of truth.

The deliverable is a **full-stack Pokémon management app**:
- a **Java + Spring Boot** REST API that integrates with [PokeAPI](https://pokeapi.co/docs/v2)
- a **modern frontend** (React or Vue)
- built with **Clean Architecture** and **TDD**

A separate **GenAI exercise** (a task-management API prompt, plus a write-up) is also part of the assessment. It is **out of scope for now**, since we are focusing on the Pokémon app first.

## Functional requirements (user stories)
| # | Story | Key points |
|---|-------|-----------|
| US01 | Pokémon listing | Paginated results; each entry shows its sprite, category, weight and abilities. *Nice to have: caching.* |
| US02 | Detail view | Image, base stats, description (flavor text), evolution chain |
| US03 | Data sync | Persist Pokémon to a local relational DB so proprietary fields can be added (e.g. localized name, region/geo metadata, internal tags) |
| US04 | Local updates | Update locally stored Pokémon. Return 404 for a missing record, 400 for a malformed payload, plus other defensive validation |

## Technical requirements
- **Mandatory:** public Git repo, tests, proper error handling, and a frontend that consumes the API
- **Nice to have:** a caching layer for PokeAPI responses
- **Database:** a primary entity plus a **users** collection. Every record has a unique PK and at least 2 descriptive attributes.
- **API:** full CRUD on the dataset, standard HTTP verbs, required params, consistent response structure
- **Auth:** user registration and login, with **protected vs. public routes**
- **Layers:** a separate data access layer, and a business logic layer **independent of both the API and data layers**
- **Testing:** thorough unit tests for every core component (TDD preferred)
- **Frontend:** responsive, user-centric, CRUD for the use cases, clean component organization, efficient state management, **no browser console warnings**

## Delivery checklist
- [ ] Public Git repository
- [ ] README with environment setup and technical documentation
- [ ] Seeded demo data and mock credentials
- [ ] Dockerfile (containerized execution)
- [ ] Tests

## Evaluation criteria (presentation and code review)
Clean Architecture · test coverage/TDD · code quality · functionality (no bugs, no console warnings) · presentation (backend and frontend best practices) · **GenAI fluency and critical evaluation of AI-generated code**

> The way Claude Code is used during this build may also be discussed. Log notable prompts and AI corrections in WORK_TRACKER.md, because that material feeds the presentation.

## Current state
- **Phase:** **US01 is done**: `GET /api/v1/pokemon?page&size` (cached, parallel fan-out, ProblemDetail errors). 90 tests are green. Next is US02 (detail view).
- **Test helpers:** `support/FakePokeApiServer` (a real HTTP fake PokeAPI) plus fixtures in `src/test/resources/pokeapi/`, and `application/usecase/FakePokemonCatalog` (an in-memory gateway).
- **Architecture:** Uncle Bob layers (`domain`, `application`, `adapter`, `config`). **Load the `clean-architecture` skill before adding any class.** The rules are enforced by `CleanArchitectureTest`.
- **Tech stack:** Java 27, Spring Boot 4.1.1, Gradle 9.8.1 (Kotlin DSL). The skeleton builds and its tests pass.
- **Build:** JDK 27 is only available Gradle-provisioned, so build with
  `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-27-aarch64-os_x.2/jdk-27+35/Contents/Home ./gradlew build`
- **Next step:** confirm the open US01 choices (genus as category; alternate forms excluded), then TDD US02. Extend `PokemonCatalogGateway` with stats, flavor text and the evolution chain.

## Repo layout
```
CLAUDE.md         # always-loaded agent rules
.claude/skills/clean-architecture/SKILL.md   # where/how to add code
build.gradle.kts  # Spring Boot 4.1.1, Java 27 toolchain, ArchUnit 1.5.1
src/main/java/com/app/poke/
  domain/{model,exception}
  application/{port/in,port/out,usecase,exception}
  adapter/in/web/dto, adapter/out/{pokeapi,persistence}
  config/
src/test/java/com/app/poke/   # mirrors main; architecture/CleanArchitectureTest
tech.pdf          # assessment spec (source of truth)
docs/assessment-spec.md  # spec transcribed for agents (imported by CLAUDE.md)
HANDOFF.md        # this file: context for resuming
WORK_TRACKER.md   # progress, decisions, scope, AI-usage log
```
*(Update this section as the project structure grows.)*

## Session conventions
- Update **Current state** here and log the work in WORK_TRACKER.md at the end of each meaningful step.
- Don't regenerate the planning docs from scratch. Edit them in place.
