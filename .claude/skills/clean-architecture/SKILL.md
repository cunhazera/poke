---
name: clean-architecture
description: Where and how to add backend code in this project under Uncle Bob's Clean Architecture. Load BEFORE creating or moving any Java class (model, entity, value object, use case, port, gateway, adapter, controller, DTO, mapper, JPA entity, client, exception, config or bean) or adding a new feature or user story to the backend.
---

# Clean Architecture: placement and construction rules

This project follows Robert C. Martin's *Clean Architecture*. The rules below are **enforced by
`src/test/java/com/app/poke/architecture/CleanArchitectureTest.java`** (ArchUnit). Code that
breaks them fails the build. Never weaken that test to make code pass. Fix the code instead.

## 1. The Dependency Rule
> Source code dependencies can only point **inward**. Nothing in an inner circle can know
> anything at all about something in an outer circle. (Uncle Bob)

```
  ┌───────────────────────────────────────────────────────────────┐
  │ Frameworks & Drivers   config/ (Spring wiring), Spring, JPA,   │
  │                        HTTP clients, DB, the PokeAPI           │
  │  ┌─────────────────────────────────────────────────────────┐  │
  │  │ Interface Adapters   adapter/in/web, adapter/out/*       │  │
  │  │  ┌───────────────────────────────────────────────────┐  │  │
  │  │  │ Use Cases (Application)   application/            │  │  │
  │  │  │  ┌─────────────────────────────────────────────┐  │  │  │
  │  │  │  │ Entities (Domain)   domain/                 │  │  │  │
  │  │  │  └─────────────────────────────────────────────┘  │  │  │
  │  │  └───────────────────────────────────────────────────┘  │  │
  │  └─────────────────────────────────────────────────────────┘  │
  └───────────────────────────────────────────────────────────────┘
```

| Layer (package) | May depend on | Must NEVER depend on |
|---|---|---|
| `domain` | `java.*` only | everything else, including Spring, Jackson, JPA and Lombok |
| `application` | `domain`, `java.*` | adapters, config, any framework |
| `adapter.in.*` | `application.port.in`, `domain`, frameworks | `application.usecase`, `application.port.out`, `adapter.out`, `config` |
| `adapter.out.*` | `application.port.out`, `application.exception`, `domain`, frameworks | `adapter.in`, `config` |
| `config` | everything (it's the composition root) | (nothing may depend on `config`) |

Control flow goes *outward* (use case → gateway → PokeAPI), while the source dependency points *inward*
(the adapter implements an interface owned by the application). This is the **Dependency Inversion
Principle** at the boundary.

## 2. Package map
```
com.app.poke
├── PokeApplication.java            # Spring Boot entry point (stays at root)
├── domain/                          # ENTITIES: enterprise business rules
│   ├── model/                       #   entities and value objects (immutable records)
│   └── exception/                   #   domain rule violations
├── application/                     # USE CASES: application business rules
│   ├── port/in/                     #   input boundaries: <Verb><Noun>UseCase + Command/Query records
│   ├── port/out/                    #   output boundaries: <Noun>Gateway interfaces
│   ├── usecase/                     #   interactors: <Verb><Noun>Interactor
│   └── exception/                   #   application failures (e.g. upstream unavailable)
├── adapter/                         # INTERFACE ADAPTERS
│   ├── in/web/                      #   driving: REST controllers, web mappers, exception handler
│   │   └── dto/                     #     HTTP request/response DTOs (public API contract)
│   └── out/
│       ├── pokeapi/                 #   driven: PokeAPI client + its private DTOs and mapper
│       └── persistence/             #   driven: JPA entities, Spring Data repos, gateway impls
└── config/                          # FRAMEWORKS & DRIVERS: @Configuration, @Bean wiring, properties
```
Tests mirror this tree under `src/test/java/com/app/poke/`.

**Adding a new outbound technology** (e.g. a cache or a message bus): create `adapter/out/<tech>/`.
**Adding a new delivery mechanism** (e.g. CLI, GraphQL): create `adapter/in/<mechanism>/`.
Never add new top-level packages without recording a decision in `WORK_TRACKER.md`.

## 3. "Where does X go?" decision table

| You are adding… | Package | Naming | Notes |
|---|---|---|---|
| A business concept (Pokémon, Ability, User) | `domain.model` | `Pokemon`, `Ability` | `record`; validate invariants in the compact constructor; no annotations |
| A value with rules (weight, page request) | `domain.model` | `Weight`, `PageRequest` | Put unit conversions and validation **here**, not in adapters |
| A violated business rule | `domain.exception` | `<Noun>NotFoundException`, `Invalid<Noun>Exception` | Extends `RuntimeException`; no HTTP knowledge |
| An action a user can perform | `application.port.in` | `<Verb><Noun>UseCase` (interface) | One use case = one interface = one public method |
| The input to that action | `application.port.in` | `<Verb><Noun>Command` (writes) / `<Verb><Noun>Query` (reads) | Records; validate in the constructor |
| Implementation of the action | `application.usecase` | `<Verb><Noun>Interactor` | `implements` its UseCase; **no Spring annotations**; dependencies via constructor |
| Something a use case needs from the outside world | `application.port.out` | `<Noun>Gateway` (interface) | Speaks domain types only, never HTTP or JPA types |
| A failure of an external system | `application.exception` | `<Noun>UnavailableException` | Gateways translate tech exceptions into these |
| A REST endpoint | `adapter.in.web` | `<Noun>Controller` | Depends on `...UseCase` interfaces only; no business logic |
| A request/response body | `adapter.in.web.dto` | `<Noun>Request`, `<Noun>Response` | Bean Validation annotations go here (shape), not business rules |
| Domain → response mapping | `adapter.in.web` | `<Noun>WebMapper` | Plain class or static methods |
| Exception → HTTP status mapping | `adapter.in.web` | `GlobalExceptionHandler` | `@RestControllerAdvice`; single consistent error body |
| A PokeAPI call | `adapter.out.pokeapi` | `PokeApi<Noun>Gateway implements <Noun>Gateway` | Raw PokeAPI DTOs are `PokeApi<Noun>Dto`, package-private where possible |
| A DB table | `adapter.out.persistence` | `<Noun>JpaEntity`, `<Noun>JpaRepository` | JPA entities **never leave** this package; map them to domain |
| A table, column, index or constraint | `src/main/resources/db/migration` | `V<n>__<verb>_<noun>.sql` | **Flyway owns the schema.** Add a new version; **never edit an applied migration**. Prove each constraint in `SchemaMigrationTest` with a real INSERT. |
| A DB-backed gateway | `adapter.out.persistence` | `Jpa<Noun>Gateway implements <Noun>Gateway` | `@Transactional` lives here, not in interactors |
| Bean wiring / properties | `config` | `<Feature>Config`, `<Noun>Properties` | Interactors are created here with `@Bean` |

## 4. Recipe: adding a feature (TDD, inside → out)
Work from the center outward, writing the **test first** at each step:

1. **Domain:** add or extend models in `domain.model`. Unit test them (plain JUnit, no Spring).
2. **Input port:** define `<Verb><Noun>UseCase` and its `Command`/`Query` in `application.port.in`.
3. **Output port(s):** define the `<Noun>Gateway` interfaces the use case needs in `application.port.out`.
4. **Interactor:** test `<Verb><Noun>Interactor` with **fake or mock gateways** (no Spring, no network), then implement it.
5. **Driven adapter:** implement the gateway in `adapter.out.<tech>`. Test against a stubbed server
   (`MockRestServiceServer`/WireMock) or `@DataJpaTest`. Translate external errors into `application.exception`.
6. **Driving adapter:** add the controller, DTOs and mapper in `adapter.in.web`. Test with `@WebMvcTest`, mocking the **UseCase interface**.
7. **Wiring:** register the interactor as a `@Bean` in `config`, injecting gateway implementations by their interface type.
8. **Verify:** `./gradlew build`. The ArchUnit suite must pass. Then update `WORK_TRACKER.md`.

## 5. Hard rules (each one is checked or reviewable)
- **No framework code in `domain` or `application`:** no `@Service`, `@Component`, `@Autowired`,
  `@Transactional`, `@Entity`, `@JsonProperty`, `ResponseEntity` or `HttpStatus`. *(ArchUnit-enforced.)*
- **Controllers call `UseCase` interfaces, never `Interactor` classes or gateways.** *(ArchUnit-enforced.)*
- **Ports are interfaces.** *(ArchUnit-enforced.)*
- **Each boundary has its own data shapes:** web DTOs ≠ domain models ≠ PokeAPI DTOs ≠ JPA entities. Map at the boundary.
- **Validation is split by meaning:**
  - *shape* (missing field, wrong type, page size > max) is checked in the web DTO with Bean Validation → **400**
  - *business rules* are checked in domain or application, which throw domain exceptions
  - the handler maps those to **404**, **409** or **422** as appropriate
- **Schema changes go through Flyway only.** Never edit an applied `V<n>__*.sql`; Hibernate runs with `ddl-auto=validate`, never `update`. Tests use Testcontainers PostgreSQL (`support/TestcontainersConfiguration`), never H2.
- **Constructor injection only.** All fields are `final`, with no field injection.
- **Immutability by default:** use records, `List.copyOf` and no setters on domain types.
- **Don't add a layer you don't need.** A DTO must not be a 1:1 copy "just because". If you find one, flag it in the tracker.

## 6. Deliberate, documented trade-offs
- **Interactors may return domain models** to adapters, rather than the separate "Response Models"
  Uncle Bob describes. Domain models are immutable records, and every adapter must still map them to its own DTO, so
  domain types are never serialized directly. This avoids a mapping layer that adds no value.
- **Transactions live in the persistence adapter** (or a config-level decorator), keeping interactors framework-free.
- **Packages are organized by layer first.** Class names carry the feature (`ListPokemon...`, `RegisterUser...`).
  If one layer grows past roughly 15 classes, split it into feature sub-packages inside that layer and record it as a decision.

## 7. Before you finish: checklist
- [ ] Every new class sits in the package the table above dictates
- [ ] A test was written first, and it lives in the mirrored test package
- [ ] No framework imports in `domain` or `application`
- [ ] No adapter-specific type crosses a port
- [ ] `./gradlew build` is green, including `CleanArchitectureTest`
- [ ] `WORK_TRACKER.md` is updated: progress, any new decision, and any AI correction worth noting
