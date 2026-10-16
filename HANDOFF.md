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
- [x] README with environment setup and technical documentation
- [ ] Seeded demo data and mock credentials
- [x] Dockerfile (containerized execution) + compose
- [x] Tests (unit, integration, architecture, schema, black-box)

## Evaluation criteria (presentation and code review)
Clean Architecture · test coverage/TDD · code quality · functionality (no bugs, no console warnings) · presentation (backend and frontend best practices) · **GenAI fluency and critical evaluation of AI-generated code**

> The way Claude Code is used during this build may also be discussed. Log notable prompts and AI corrections in WORK_TRACKER.md, because that material feeds the presentation.

## Current state
- **Phase:** US01, US02, auth, US03 and US04 are merged into `develop` (PRs #1–#4). **The frontend is built** on branch `frontend` (cut from `develop`, **not pushed**), awaiting the user's review before a PR.
- **Frontend** (`frontend/`, plan `docs/plans/frontend-plan.md`, D54–D62): Vite 8 + React 19 + TypeScript, React Router 8, TanStack Query (server state) + a small session store (`auth/session.ts`), CSS Modules. Screens: `/` catalog, `/pokemon/:id` (admin sync), `/local` (admin sync by number, "New Pokémon"), `/local/:id` (admin edit, re-sync, delete with confirmation), `/local/new`, `/local/:id/edit`, `/login`, `/register`. Same-origin `/api` (Vite proxy in dev, nginx in Docker; no CORS). Tests: `cd frontend && npm test` (65, Vitest + Testing Library + MSW fake backend in `src/test/fakeApi.ts`); `npm run lint`, `npm run typecheck`. Node 24+ (25 works; the test config disables Node's own `localStorage`).
- **US03:** `POST /api/v1/local-pokemon/sync/{n}` (ADMIN) and `GET /api/v1/local-pokemon[/{id}]` (signed in). A re-sync keeps the proprietary data (`LocalPokemon.refreshedFrom`). Optimistic locking via `version` → 409. Six demo Pokémon are seeded (`db/seed/R__demo_pokemon.sql`).
- **US04:** `POST /api/v1/local-pokemon`, `PUT`/`DELETE /api/v1/local-pokemon/{id}`, all ADMIN. A synced copy accepts only proprietary edits (`LocalPokemon.edited`, else 400 "Catalog fields not editable"); a local one is replaced whole. `PUT` needs the `version` (stale → 409). Request bodies are read strictly (unknown fields and fractional integers → 400 naming the field; auth DTOs opt out). Names are unique regardless of case (409).
- **Auth:** JWT bearer tokens; public = catalog GETs, register, login, health; everything else needs a token. Demo users `ash`/`pikachu123` and `oak`/`professor123` (ADMIN) are pre-inserted by the `demo` profile (compose). The JWT secret comes from `JWT_SECRET` (tests: `src/test/resources/config/application.properties`).
- **Run everything:** `docker compose up --build` (UI on http://localhost:3000, API on :8080). Tests: `docker compose run --rm test` (JUnit, 555) and `docker compose run --rm api-tests` (Newman). See README.md.
- **Database:** PostgreSQL 18; the schema is owned by Flyway (`db/migration`: V1 pokemon + child tables, V2 app_user; `db/seed`: demo users, `demo` profile only). **Never edit an applied migration.** JPA with `ddl-auto=validate`: `app_user` is mapped (`adapter.out.persistence`); the pokemon tables are mapped in US03. Case-insensitive lookups use `lower(col) = lower(:p)`, never `IgnoreCase`.
- **Test helpers:**
  - `support/FakePokeApiServer` (a real HTTP fake PokeAPI: `stubFirstThree`, `stubBulbasaurLine`), with fixtures in `src/test/resources/pokeapi/`
  - `support/TestcontainersConfiguration`: import it in every `@SpringBootTest`. Docker must be running.
  - `application/usecase/FakePokemonCatalog` + `CatalogFixtures`: an in-memory gateway
- **Black-box tests:** a collection per story in `api-tests/collections/`; run them against the compose stack.
- **Architecture:** Uncle Bob layers (`domain`, `application`, `adapter`, `config`). **Load the `clean-architecture` skill before adding any class.** The rules are enforced by `CleanArchitectureTest`.
- **Tech stack:** Java 27, Spring Boot 4.1.1, Gradle 9.8.1 (Kotlin DSL), PostgreSQL 18, Flyway, Testcontainers, Caffeine, Actuator (health only).
- **Build without Docker:** JDK 27 is only available Gradle-provisioned, so build with
  `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-27-aarch64-os_x.2/jdk-27+35/Contents/Home ./gradlew build`
- **Next step:** the user reviews branch `frontend` (and the plan's recommendations F1–F10); then push and open a PR to `develop`. Afterwards: the GenAI exercise and the release to `main`.
- **Frontend requirements to keep in mind (spec):** responsive and user-centric; CRUD for the functional use cases; clean component organization and efficient state management; **no warnings in the browser console**; demo credentials (`ash`/`pikachu123` USER, `oak`/`professor123` ADMIN).

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
src/main/resources/db/migration/  # Flyway migrations (V1 pokemon, V2 app_user)
Dockerfile, compose.yaml, .env.example  # run everything in Docker
README.md         # setup, tests, API, architecture, schema
docs/plans/       # per-story implementation plans (us02, auth, us03, us04; frontend next)
tech.pdf          # assessment spec (source of truth)
docs/assessment-spec.md  # spec transcribed for agents (imported by CLAUDE.md)
.github/workflows/ci.yml  # CI: build + all tests, plus a Docker image + compose smoke test, on every push and PR
frontend/         # React app (D54–D62): src/{api,auth,components,pages,test}, Dockerfile + nginx.conf.template
api-tests/        # Postman collections + Newman runner (black-box tests vs. the running server); generators/ builds new ones
HANDOFF.md        # this file: context for resuming
WORK_TRACKER.md   # progress, decisions, scope, AI-usage log
```
*(Update this section as the project structure grows.)*

## Session conventions
- Update **Current state** here and log the work in WORK_TRACKER.md at the end of each meaningful step.
- Don't regenerate the planning docs from scratch. Edit them in place.
