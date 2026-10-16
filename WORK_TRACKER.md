# Work Tracker

> A running history of progress, decisions, scope and AI usage. Append new entries; don't rewrite history.
> For project context, see [HANDOFF.md](HANDOFF.md).

---

## User stories

### US01: Pokémon Enumeration
**Status:** ✅ Done (2026-10-15). `GET /api/v1/pokemon?page=0&size=20`

**Description (from spec):** The system should allow users to browse Pokémon via paginated results, displaying each entry's **sprite**, **category**, **mass** and a **collection of their skills**.
- *Nice to have:* implement caching for service responses.

**As a** user, **I want to** browse a paginated list of Pokémon **so that** I can see each one's sprite, category, weight and abilities at a glance.

**Acceptance criteria:**
- [x] A paginated listing endpoint: `page` (default 0) and `size` (default 20, max 50)
- [x] Each entry includes: id, name, sprite URL, category (genus), weight in kg, abilities (with a hidden flag)
- [x] The response includes pagination metadata (page, size, totalItems, totalPages, hasNext, hasPrevious)
- [x] Invalid pagination returns 400 (out of range, non-numeric, int overflow) and never reaches PokeAPI
- [x] PokeAPI failures (5xx, 429, timeout, unreachable, malformed JSON) return a consistent 503 ProblemDetail that doesn't leak internals; unexpected errors return a generic 500
- [x] *(Nice to have)* Caching with Caffeine, 24h TTL. Failures are not cached.

**Implementation map:**
| Layer | Classes |
|---|---|
| domain | `PageRequest`, `Page`, `Weight`, `Ability`, `PokemonSummary`, `InvalidPageRequestException`, `PokemonNotFoundException` |
| application | `ListPokemonUseCase` + `ListPokemonQuery` (in), `PokemonCatalogGateway` + `CatalogSlice` + `PokemonProfile` (out), `ListPokemonInteractor`, `CatalogUnavailableException` |
| adapter.out.pokeapi | `PokeApiCatalogGateway` (RestClient + `@Cacheable`), `PokeApiDtos`, `PokeApiMapper` |
| adapter.in.web | `PokemonController`, `PokemonWebMapper`, `GlobalExceptionHandler` |
| config | `PokemonConfig` (wiring, virtual-thread executor, `@EnableCaching`), `PokeApiProperties` |

**Tests (85 for US01):**
- *Unit:* domain invariants and boundaries; the interactor against an in-memory `FakePokemonCatalog`, covering composition, offset math, order preservation, real concurrency, past-the-end pages, missing category, failure propagation and upstream inconsistency
- *Adapter integration:* `PokeApiCatalogGatewayTest` drives the real HTTP client against `FakePokeApiServer` (a JDK `HttpServer`) using trimmed **real** PokeAPI fixtures
- *Web slice:* `PokemonControllerTest` covers binding, defaults, the exact JSON contract, 400/503/500 ProblemDetails, and confirms invalid input never reaches the use case
- *End to end:* `PokemonListingIntegrationTest` runs the full Spring context against the fake PokeAPI and covers the happy path, the cache (2nd request = 0 upstream hits), failures not being cached, and timeouts
- *Smoke (manual, live PokeAPI):* cold page of 20 in 625 ms, warm in 3.5 ms; the last page ends at #1025 Pecharunt

**Field mapping to PokeAPI:**
| US01 field | Source |
|------------|--------|
| id, name | `GET /pokemon/{id}` → `id`, `name` |
| sprite | `/pokemon/{id}` → `sprites.front_default` |
| category | `GET /pokemon-species/{id}` → `genera[]` where `language.name == "en"` → `genus` (e.g. "Seed Pokémon") |
| mass | `/pokemon/{id}` → `weight` (**hectograms**; 69 = 6.9 kg) |
| skills | `/pokemon/{id}` → `abilities[].ability.name` (+ `is_hidden`) |

**Key finding: N+1 fan-out.** `GET /pokemon?limit&offset` returns only `{name, url}` per entry. One page of N entries costs **1 + 2N** upstream calls (list, then pokemon and species for each entry). This makes caching effectively required, not just nice to have. Fetch in parallel per page.

**Response contract (implemented):** `adapter/in/web/dto/`
```json
{
  "items": [
    {
      "id": 1,
      "name": "bulbasaur",
      "spriteUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png",
      "category": "Seed Pokémon",
      "weightKg": 6.9,
      "abilities": [ { "name": "overgrow", "hidden": false }, { "name": "chlorophyll", "hidden": true } ]
    }
  ],
  "page": 0, "size": 20, "totalItems": 1025, "totalPages": 52,
  "hasNext": true, "hasPrevious": false
}
```
- `PokemonSummaryResponse` and `AbilityResponse` hold the entry. `PageResponse<T>` is a generic envelope that every listing endpoint will reuse.
- `hasNext` and `hasPrevious` are derived from page and totalPages rather than stored, so they can't contradict each other.
- Lists are defensively copied (`List.copyOf`), which makes the records immutable.

---

### US02: Detailed View
**Status:** ✅ Done (2026-10-15). `GET /api/v1/pokemon/{id}`. Plan: [`docs/plans/us02-plan.md`](docs/plans/us02-plan.md) (approved v5).

**Description (from spec):** comprehensive data for a chosen Pokémon: **image, core statistics, narrative description, evolutionary lineage**.

**As a** user, **I want to** open a Pokémon's page **so that** I can see its artwork, base stats, Pokédex description and full evolution chain.

**Acceptance criteria:**
- [x] Official artwork as `imageUrl` (plus the small `spriteUrl`); either may be null
- [x] Six base stats as a named object, each 1..255, plus a derived `total`
- [x] `description` = the latest English flavor text, with game line breaks, form feeds and soft hyphens normalized
- [x] `evolutionChain` = a **tree** from the chain's first stage, with a sprite per stage (Eevee ×8, Wurmple two-level branching, Pichu baby stage, single-stage Tauros)
- [x] Also `types`, `heightM`, `weightKg`, `abilities`, `category` (D25)
- [x] 400 for any malformed or non-positive id; 404 for unknown species, including alternate forms (`/pokemon/10001`); 503 upstream; 500 generic
- [x] Cached: a repeat detail costs 0 upstream calls; a listing warms the detail view (only the chain is new)

**Implementation map:**
| Layer | Classes |
|---|---|
| domain | `PokedexNumber`, `BaseStats`, `Height`, `EvolutionStage` (recursive), `PokemonDetail`, `InvalidPokemonIdException` |
| application | `GetPokemonDetailUseCase` + `GetPokemonDetailQuery` (in); `PokemonCatalogGateway.findSpecies` / `findEvolutionChain`, `SpeciesProfile`, `EvolutionLink`, a wider `PokemonProfile` (out); `GetPokemonDetailInteractor`; `Futures` (shared join) |
| adapter.out.pokeapi | `PokeApiCatalogGateway` (+ `pokeapi-evolution` cache), `PokeApiDtos`, `PokeApiMapper` (stats by name, flavor-text normalization, recursive chain) |
| adapter.in.web | `PokemonController#detail` (strict id parsing), `PokemonDetailResponse`, `BaseStatsResponse`, `EvolutionStageResponse`, `PokemonWebMapper`, `GlobalExceptionHandler` |
| config | `PokemonConfig` (`GetPokemonDetailUseCase` bean) |

**Tests:** domain (`PokedexNumberTest`, `BaseStatsTest`, `HeightTest`, `EvolutionStageTest`, `PokemonDetailTest`); `GetPokemonDetailQueryTest`; `GetPokemonDetailInteractorTest` (chain fetched by the chain id, not the Pokédex number; branch order; fetched once; missing species → 404; inconsistent chain → 503; invalid catalog data → 503; real concurrency ×2); `PokeApiCatalogGatewayTest` (new real fixtures: pokemon/species 1-3 and 133, chains 1, 63, 67); `PokemonDetailEndpointTest` (STRICT contract, 9 invalid ids, 404/503/500, trailing slash, 405, HEAD); `PokemonDetailIntegrationTest` (end to end with cache and Postgres). Newman: `us02-pokemon-detail` (29 requests, 107 assertions).

**Smoke (live PokeAPI, in Docker):** Eevee cold 2.1 s (9 stages), warm 4 ms.

---

### Users and authentication
**Status:** ✅ Done (2026-10-15) on branch `auth`. Plan: [`docs/plans/auth-plan.md`](docs/plans/auth-plan.md).

**Spec:** "a secondary collection for user management"; "an auxiliary API for user registration, authentication, and protected vs. public routes"; "seeded data or mock credentials".

**Acceptance criteria:**
- [x] `POST /api/v1/auth/register` → 201 (always `USER`; a `role` field is ignored) · 400 with the field · 409 if the username or email is taken in any case
- [x] `POST /api/v1/auth/login` by username **or** email, in any case → JWT bearer token (HS256, 1 h) · 401 with the same message for an unknown user or a wrong password
- [x] `GET /api/v1/auth/me` → the token's user; protected
- [x] Public: `GET /api/v1/pokemon/**`, register, login, `/actuator/health`. **Everything else needs a token** (secure by default)
- [x] 401/403 (and firewall 400s) are ProblemDetails with `WWW-Authenticate: Bearer`
- [x] Demo users **pre-inserted** by a Flyway repeatable seed in the `demo` profile (compose): `ash`/`pikachu123` (USER), `oak`/`professor123` (ADMIN)

**Implementation map:**
| Layer | Classes |
|---|---|
| domain | `User`, `Role`, `Username`, `EmailAddress`, `RawPassword` (8–72 **bytes**), `InvalidUserDataException`, `UsernameTakenException`, `EmailTakenException`, `UserNotFoundException` |
| application | in: `RegisterUserUseCase`/`Command`, `AuthenticateUserUseCase`/`Command` → `AccessToken`, `GetCurrentUserUseCase` · out: `UserGateway` + `NewUser`, `PasswordHasher`, `TokenIssuer` · three interactors · `InvalidCredentialsException` |
| adapter.out.persistence | `UserJpaEntity`, `UserJpaRepository` (native `lower()` lookup), `JpaUserGateway` (translates unique violations) |
| adapter.out.security | `BCryptPasswordHasher` (dummy-hash timing equalization), `JwtTokenIssuer` |
| adapter.in.web | `AuthController`, `RegisterRequest`, `LoginRequest`, `UserResponse`, `TokenResponse`, `ProblemDetailSecurityHandler`, `GlobalExceptionHandler` (+400/401/404/409, Bean Validation `errors`) |
| config | `SecurityConfig` (filter chain, JWT encoder and decoder with an issuer check, roles → authorities), `AuthConfig`, `JwtProperties` (refuses a missing or short secret) |

**Tests:** value objects (incl. the UTF-8 byte limit and masked `toString`); interactors with in-memory fakes (case-insensitive login, indistinguishable failures, **an unknown user still costs a hash check**); `JpaUserGatewayTest` (`@DataJpaTest` + Testcontainers: round trip, case-insensitive conflicts, **an 8-thread registration race with exactly one winner**, `EXPLAIN` proving the login query uses both `lower()` indexes); `BCryptPasswordHasherTest` (salting, timing of a missing hash); `JwtTokenIssuerTest` (claims, HS256, foreign key rejected); `AuthControllerTest` (contracts, 400/401/404/409/415, with the real security rules); `AuthenticationIntegrationTest` (end to end; missing, malformed, expired, forged and foreign-issuer tokens; public vs. protected-by-default); `DemoSeedTest` (no users without the profile, documented passwords verify, idempotent). Newman: `auth-users` (28 requests / 91 assertions).

---

## PokeAPI reference notes
Researched 2026-10-14 from https://pokeapi.co/docs/v2 and live calls.

- **Base URL:** `https://pokeapi.co/api/v2/`. GET only, no auth.
- **Fair use:** no hard rate limit (it was removed in 2018), but the docs say to **cache resources locally** and limit request frequency. IPs that abuse the API can be banned permanently. The API sends `Cache-Control: public, max-age=86400` and an ETag, so the data is effectively static and a long TTL is safe.
- **Pagination:** `?limit=&offset=`, default 20. The response is `{count, next, previous, results:[{name,url}]}`. `count` is 1351 for `/pokemon` and includes alternate forms with ids ≥ 10001. Open question: should alternate forms be excluded?
- **`/pokemon/{id|name}`:** `id, name, height (dm), weight (hg), abilities[], types[], stats[] {base_stat, stat.name}, sprites {front_default, other.official-artwork.front_default}, species {url}`. The payload is very large (~280 KB with `moves`), so map only the fields we need.
- **`/pokemon-species/{id}`:** `genera[]` (category), `flavor_text_entries[]` (description, filter by language `en`, and the text contains `\n` and `\f` that need normalizing), `evolution_chain.url`
- **`/evolution-chain/{id}`:** `chain` is a recursive ChainLink `{species, evolution_details[] (trigger, min_level, item…), evolves_to[]}` and can branch (e.g. Eevee)
- **Usage by story:** US01 uses `pokemon` + `pokemon-species`. US02 also uses `stats`, `official-artwork`, `flavor_text_entries` and `evolution-chain`.
- **Java wrapper:** `pokeapi-reactor` exists, but it's better to write our own thin client behind a port interface (Clean Architecture) so it can be tested and swapped out.

---

## Progress

| Area | Item | Status |
|------|------|--------|
| Planning | Read and understand the spec (`tech.pdf`) | ✅ Done |
| Planning | Create the handoff and work tracker docs | ✅ Done |
| Planning | Decide the tech stack and architecture | 🔄 In progress (base stack set, see D3) |
| Planning | Break the work down into user stories | 🔄 In progress (US01 registered) |
| Planning | Clean Architecture package structure, agent skill, `CLAUDE.md`, ArchUnit guard | ✅ Done |
| Backend | Project scaffold | ✅ Done (Spring Boot skeleton builds on JDK 27) |
| Backend | PokeAPI client integration | ✅ Done (listing, detail, evolution chains) |
| Backend | US01: paginated listing | ✅ Done |
| Backend | US02: detail view | ✅ Done |
| Backend | Database schema (PostgreSQL 18, Flyway) | ✅ Done: V1 pokemon + child tables, V2 app_user (D24, D27, D28) |
| Backend | US03: sync to local DB | ⬜ Not started (tables ready) |
| Backend | US04: local CRUD and validation | ⬜ Not started |
| Backend | Auth (register, login, protected routes) | ✅ Done: JWT bearer tokens, secure by default (D34–D40) |
| Backend | Caching (nice to have) | ✅ Done (Caffeine, D18) |
| Backend | Error handling (global, consistent format) | ✅ Done (ProblemDetail, D17) |
| Frontend | Project scaffold | ⬜ Not started |
| Frontend | List, detail, edit and auth screens | ⬜ Not started |
| Delivery | Seed data and mock credentials | 🔄 Demo users pre-inserted (`demo` profile, D36); demo Pokémon come with the US03 sync |
| Delivery | Dockerfile / docker-compose | ✅ Done: app + Postgres, plus `test` and `api-tests` tool services (D29) |
| Delivery | README | ✅ Done (quick start, tests, API, architecture, schema, config); extend per story |
| Testing | Black-box API collections (Newman) | ✅ Auth: 28 requests / 91 assertions; US01: 29 / 149; US02: 32 / 116. All passing against the dockerized app and live PokeAPI |
| Delivery | Public Git repo | 🔄 Remote `cunhazera/poke`. Flow: `us0N` → `develop` → `main` (D32). `us02` not pushed yet |
| Delivery | CI (GitHub Actions) | ✅ Build + tests (D23), Docker image + compose smoke test (D29) |
| GenAI | Task-management prompt exercise | ⏸️ Deferred (Pokémon app first) |

Legend: ✅ Done · 🔄 In progress · ⏳ Next · ⬜ Not started · ⏸️ Deferred · ❌ Dropped

---

## Decisions log

| # | Date | Decision | Rationale | Alternatives considered |
|---|------|----------|-----------|------------------------|
| D1 | 2026-10-14 | Build the Pokémon full-stack app first; defer the GenAI exercise | Biggest and most heavily evaluated deliverable | — |
| D2 | 2026-10-14 | Implement full CRUD on local Pokémon data, not just update | The API section requires "comprehensive CRUD" even though US04 only mentions update | Update-only (would miss a stated requirement) |
| D3 | 2026-10-14 | Stack: Java 27 (toolchain), Spring Boot 4.1.1, Gradle 9.8.1 (Kotlin DSL) | Set by the generated project scaffold; Gradle 9.8.1 is the release that adds Java 27 support | — |
| D4 | 2026-10-14 | Organize work by user story (US01 to US04) | Matches how the spec and the presentation are structured | — |
| D5 | 2026-10-14 | ~~Top-level packages `domain`, `application`, `infrastructure`, `presentation`~~ **Superseded by D8** | — | — |
| D6 | 2026-10-14 | US01 response: `weightKg` (converted from hectograms), `category` = species genus, abilities include a `hidden` flag, zero-based `page` | Clients shouldn't need to know PokeAPI's units; genus is what the official Pokédex calls "category" | Raw hectograms; types as the category |
| D8 | 2026-10-15 | Package structure follows Uncle Bob's rings: `domain` (Entities), `application` with `port.in`, `port.out`, `usecase` and `exception` (Use Cases), `adapter.in.web` and `adapter.out.{pokeapi,persistence}` (Interface Adapters), `config` (Frameworks & Drivers, composition root). DTOs moved to `adapter.in.web.dto`. | Matches the vocabulary of the book and of ports and adapters; "adapter in/out" makes the driving vs. driven direction explicit | `presentation`/`infrastructure` (D5): generic names that hide the direction |
| D9 | 2026-10-15 | Naming: `<Verb><Noun>UseCase` (input port), `<Verb><Noun>Interactor` (implementation), `<Noun>Gateway` (output port) | Uncle Bob's terms; makes the role of each class obvious in code review | `...Service`/`...Port` |
| D10 | 2026-10-15 | Interactors carry **no Spring annotations** and are wired with `@Bean` in `config` | Keeps the Use Cases ring truly framework-independent (spec: business layer "independent of API and data access") | `@Service` on interactors (simpler, but leaks Spring inward) |
| D11 | 2026-10-15 | Interactors may return domain models; adapters must map them to their own DTOs | Avoids a Response Model layer that would duplicate domain records 1:1 | Strict Uncle Bob Response Models |
| D12 | 2026-10-15 | Agent guidance: `CLAUDE.md` (always loaded: short rules plus a pointer) and the `clean-architecture` skill (loaded on demand: the full placement guide) **plus** an ArchUnit test (`CleanArchitectureTest`) that fails the build on violations | Docs guide the agent, but only a test *guarantees* the rules hold; the skill keeps detail out of every session's context | Docs only (unenforced); a custom subagent (the rules would apply only when that agent is used) |
| D13 | 2026-10-15 | ArchUnit **1.5.1** | 1.4.1 bundles an ASM that can't read Java 27 class files (major version 71). Every layer looked empty, and the rules silently checked nothing. | — |
| D14 | 2026-10-15 | List from `/pokemon-species` (1025 entries), not `/pokemon` (1351) | Lists one entry per species, with no Mega or regional forms. Verified that a species' default Pokémon shares its id (spot-checked #1025 and #413 Wormadam). | `/pokemon` filtered by id < 10000 (fragile) |
| D15 | 2026-10-15 | The interactor owns the fan-out: 1 list call, then pokemon and species for each entry **in parallel on virtual threads**, reassembled in order | Orchestration is application logic, and putting it here makes it unit-testable. The ports stay small and single-purpose. | The gateway returns full summaries (would make the interactor a pass-through) |
| D16 | 2026-10-15 | Max page size 50; pagination is validated in the domain `PageRequest`, not with Bean Validation | Each entry costs 2 upstream calls. A single source of truth for the rule avoids duplicating it. | `@Min/@Max` on controller params (duplicated rule) |
| D17 | 2026-10-15 | Errors use RFC 9457 `ProblemDetail` everywhere (via `ResponseEntityExceptionHandler`). Upstream failure → **503**; unexpected → generic 500 with details only in the logs. | Spring-native, standard and consistent ("consistent return structures"); no leaking of hosts or stack traces | A custom error DTO |
| D18 | 2026-10-15 | Cache at the gateway with `@Cacheable` + Caffeine (24h, max 5000), one cache per upstream call | PokeAPI data is static, and its fair-use policy asks clients to cache. Caching per call means list and detail pages share entries. | Caching whole pages (low reuse); Redis (overkill for one instance) |
| D19 | 2026-10-15 | Fake PokeAPI for tests = JDK `HttpServer` (no new dependency), serving trimmed real fixtures | Exercises the real HTTP client, timeouts and JSON parsing. No WireMock/Jetty compatibility risk on Boot 4 and Java 27. | WireMock; `MockRestServiceServer` (bypasses real HTTP) |
| D20 | 2026-10-15 | Spec transcribed to `docs/assessment-spec.md` and imported into `CLAUDE.md` | Every session sees the full requirements, not just a summary | — |
| D21 | 2026-10-15 | Black-box API tests in `api-tests/`: Postman v2.1 collections (one per story) with `pm.test` assertions, run by Newman 6.2.3 (`api-tests/run.sh`) against the **running jar plus the live PokeAPI** | Catches what JUnit can't: real upstream data, real latency and caching, packaged config. Importable into Postman for the demo. | Plain curl scripts (no assertions); REST Assured (yet another JVM test layer) |
| D22 | 2026-10-15 | A repeated single-valued query param (`?page=1&page=2`) → **400** via `SingleValuedParameterInterceptor` | Spring silently keeps the first value, which hides client bugs. The check is generic: it inspects the handler's single-valued `@RequestParam`s, so future list params (`?tags=a&tags=b`) still work. | Special-casing page and size in the controller |
| D23 | 2026-10-15 | GitHub Actions CI (`.github/workflows/ci.yml`) on **every branch push and every PR**: `actions/checkout@v7`, `actions/setup-java@v6` (Temurin **27**, matching the toolchain) with `cache: gradle`, then `./gradlew build`. Test reports are uploaded on failure, runs are cancelled by newer pushes, and permissions are read-only. | All are the latest majors as of 2026-10-15. Validated with actionlint and a clean Linux `eclipse-temurin:27-jdk` container (93/93 tests). | `gradle/actions/setup-gradle@v6`: **rejected** because v6 moved caching to a closed-source component under Gradle's Terms of Use, and setup-java's built-in cache is enough |
| D24 | 2026-10-15 | **Username and email are unique regardless of case**, enforced by `lower()` unique expression indexes on plain `VARCHAR` columns. Lookups use `lower(col) = lower(:param)`. PostgreSQL **`citext` is out of scope.** | `citext` needed an extension, a custom Hibernate `JdbcType` and a watch test (see the AI log). The `lower()` index meets the same requirement with plain SQL and plain `String` mapping, and keeps the casing the user typed. | `citext` (tried in plan v3/v4, dropped by the user for complexity); lowercasing in the domain (loses the user's casing) |
| D25 | 2026-10-15 | US02 contract: `evolutionChain` is a tree from the first stage with a sprite per stage (one cached `findPokemon` per stage); stats are a named object with a derived total; `types` and `heightM` are included; `description` is the latest English flavor text, normalized; evolution conditions are left out | Chains branch (Eevee, Wurmple). Per-stage sprites reuse the listing's cache. PokeAPI's `evolution_details` is inconsistent (Leafeon has 6 location entries), and a misleading condition is worse than none. (Plan Q1–Q4.) | A flat list; sprite URLs built from ids (hard-codes PokeAPI's layout); the oldest flavor text |
| D26 | 2026-10-15 | The port `findCategory` became `findSpecies` → `SpeciesProfile(category, description, evolutionChainId)` | One species payload feeds both the listing and the detail view, so it's fetched and cached once (`pokeapi-species`) | Keeping `findCategory` and adding a second species call (the same resource cached twice) |
| D27 | 2026-10-15 | Schema: a surrogate PK plus a unique, nullable `pokedex_number`; stats as six checked columns; child tables for types, abilities, localized names and tags; proprietary geography = `region`, `habitat`, `latitude`/`longitude` (both or neither); a `version` column; the evolution chain is **not** persisted | Locally created Pokémon have no Pokédex number. Columns give per-stat checks. The chain links species to each other and stays a cached PokeAPI read. (Plan Q8, Q9, Q11.) | Pokédex number as the PK; a key/value stats table; JSONB |
| D28 | 2026-10-15 | **Flyway owns the schema** (never edit an applied migration); tests use **Testcontainers `postgres:18-alpine`** (shared `TestcontainersConfiguration`), never H2; `SchemaMigrationTest` proves every constraint with a real INSERT | The same database everywhere, so SQL can't diverge. Constraints are the last line of defense, so each one is verified. | H2 (different SQL dialect); `ddl-auto=update` |
| D29 | 2026-10-15 | Docker: a multi-stage image (27 JDK build → 27 JRE Alpine, layered jar, non-root, healthcheck on `/actuator/health`); `compose.yaml` runs app + Postgres, with on-demand `test` and `api-tests` services; CI builds the image and smoke-tests the stack **without calling PokeAPI** | "Run everything on Docker": a fresh clone needs only Docker (verified). Actuator exposes health only, and its DB check proves migrations ran. (Plan Q6.) | Building the jar outside Docker; a CI smoke test against live PokeAPI (flaky) |
| D30 | 2026-10-15 | The detail path id is bound as text and parsed strictly (`[0-9]{1,10}` + overflow check) in the controller | Every malformed id (`abc`, `+1`, `0x1F`, `1e3`, overflow) gets the same 400 "Invalid Pokémon id" as `0`; the `int` binding would give Spring's generic type-mismatch problem and accept `+1` | `@PathVariable int` |
| D31 | 2026-10-15 | Upstream data that breaks a domain invariant (a stat of 0, no types) → **503**, not 500 | The data came from the catalog, so it's an upstream failure. The gateway (`mapped`) and the interactor translate it. | Letting `IllegalArgumentException` surface as a 500 |
| D32 | 2026-10-15 | Git flow: `develop` integrates stories; each story is a branch cut from `develop` (`us02`) and merged by PR; `develop` → `main` at release | One reviewable PR per story, which matches the presentation (Plan Q7) | Committing straight to `main` |
| D33 | 2026-10-15 | Seed data arrives with the stories that need it: demo users with auth (BCrypt), demo Pokémon from the US03 sync; a `db/seed` Flyway location enabled only in a `demo` profile | Tests start empty, and a BCrypt hash belongs where the `PasswordEncoder` lives (Plan Q10) | Seeding in V1/V2 |
| D34 | 2026-10-15 | Authentication = **stateless JWT bearer tokens** (HS256, 1 h) with Spring Security's own resource server (`spring-boot-starter-security-oauth2-resource-server`, Nimbus); `sub` = user id, `roles` claim → `ROLE_*`; the issuer is checked | Fits the SPA frontend, so no sessions or CSRF surface; no third-party JWT library; standard validation (signature, expiry, issuer) | Session cookies (+CSRF); `jjwt`; opaque tokens in the DB |
| D35 | 2026-10-15 | **Secure by default:** public = `GET/HEAD /api/v1/pokemon/**`, register, login, `/actuator/health`; `anyRequest().authenticated()` | US03/US04 endpoints are protected without extra work. Consequence: an anonymous write or an unknown route gets **401**, not 405/404 (the old Newman checks now run both with and without a token) | Permit all, protecting only listed routes (new endpoints open by mistake) |
| D36 | 2026-10-15 | Users are **pre-inserted by a Flyway repeatable seed** (`db/seed/R__demo_users.sql`, `ON CONFLICT DO NOTHING`), loaded only in the `demo` profile that compose enables; the hashes are made with Spring's `BCryptPasswordEncoder` and verified by `DemoSeedTest` | Answers "can users be pre-inserted?": yes, with no code path that creates admins at runtime, and tests start empty. A repeatable migration can be re-run safely. | An `ApplicationRunner` calling the register use case (can't create ADMIN without a backdoor); a versioned seed (would collide with schema versions) |
| D37 | 2026-10-15 | Password policy: 8–72 **UTF-8 bytes**; usernames `[A-Za-z0-9_.-]{3,50}`; emails trimmed, ≤ 254. Validated by domain value objects; DTOs only check presence (Bean Validation) | BCrypt reads only 72 bytes (Spring Security 7 rejects longer), so the limit is stated up front. Shape vs. rules split per the skill. | Character-based length (a 37×"é" password silently truncated) |
| D38 | 2026-10-15 | Login hardening: the same 401 message for an unknown user and a wrong password, **and the same cost** (BCrypt against a dummy hash); registration relies on the unique index, not a pre-check (race-safe, proven by an 8-thread test) | No account enumeration by message or timing; no check-then-insert race | A "user not found" message; checking existence before the insert |
| D39 | 2026-10-15 | Every security error is a ProblemDetail: `ProblemDetailSecurityHandler` is the entry point (401), the access-denied handler (403) **and** the firewall's `RequestRejectedHandler` (400) | Spring Security rejects in its filter chain, before `@RestControllerAdvice`; its defaults are an empty 401 and a bodyless 400 (found by a test on `/pokemon/%20`) | Default handlers (inconsistent bodies) |
| D40 | 2026-10-15 | Output ports for services use role nouns (`PasswordHasher`, `TokenIssuer`); ArchUnit gained "every non-record type in `port.out` is an interface" (verified with a planted class) | `PasswordHashGateway` reads badly, and the old rule only covered `*Gateway` names, so it was strengthened rather than bent | Renaming to `*Gateway` |
| D7 | 2026-10-14 | Web starter: `spring-boot-starter-webmvc` (the Boot 4 name), which brings in Jackson 3 (`tools.jackson.*`) | Needed for REST and JSON | WebFlux (not needed; servlet + virtual threads is simpler) |

### Open decisions
- [x] Database: **PostgreSQL 18** with Flyway; Testcontainers in tests (D27, D28)
- [x] Auth mechanism: stateless JWT (HS256) with Spring Security's resource server (D34)
- [x] Cache implementation: Caffeine (D18)
- [ ] Frontend framework and tooling (React or Vue, Vite, state management library)
- [ ] Repo structure (monorepo with `backend/` and `frontend/`, or separate repos)
- [ ] US01 "category": implemented as genus (D6), still **pending user confirmation**. Should types be added as well?
- [ ] US01 alternate forms: excluded by listing species (D14), **pending user confirmation**
- [ ] Sync strategy for US03 (on demand per Pokémon, bulk, or on first view)

---

## Scope

### Included
- US01 to US04 as described in `tech.pdf`
- User registration and login with protected/public routes
- Full CRUD on locally stored Pokémon
- Unit tests for core components
- README, seed data, Dockerfile

### Left out / deferred
| Item | Reason |
|------|--------|
| GenAI task-management exercise | Deferred until the Pokémon app is done |

---

## AI usage log
> Notable prompts, cases where AI output was wrong or needed correction, and how it was validated. This feeds the "GenAI tools" evaluation criterion in the presentation.

| Date | Context | Prompt / action | Outcome / correction |
|------|---------|-----------------|---------------------|
| 2026-10-15 | Users and authentication | "Add user authentication as required; check if users can be pre-inserted; understand the auth requirements" | **Found by tests/review:** (1) `@WebMvcTest` silently used Boot's default security (everything 401) until the slices imported `SecurityConfig`, so the slices now test the real rules; (2) the firewall returned a **bodyless 400** for `/pokemon/%20` (fixed, D39); (3) registering one object as two beans (`ProblemDetailSecurityHandler` + `RequestRejectedHandler`) broke context loading, so it became one bean; (4) **a vacuous test removed:** "the token doesn't contain the email" can never fail, because a JWT is base64-encoded; it was replaced with an exact set of claims; (5) two JDK 27 warnings in tests: spring-security-test's reflective final-field write (`--enable-final-field-mutation`, test JVM only); (6) Hibernate logged WARN for every expected 409, so its logger was lowered to ERROR. **Mutation checks:** character-based password length and an unmasked `toString` (2 failures); skipping the hash for unknown users (the timing test failed); raw DB exceptions instead of domain conflicts and `upper()` instead of `lower()` (4 failures, including the race and `EXPLAIN`); dropping the issuer check (1); `anyRequest().permitAll()` (4). **Tooling:** generating the seed hashes took three classpath attempts (commons-logging flavor, then spring-core) with Spring's own encoder. |
| 2026-10-15 | US02 implementation | "Implement US02 following the plan" (TDD, inside out, mutation checks) | **Wrong test, right schema:** a 52-character username was rejected by `VARCHAR(50)` before the regex check, so the test expecting `app_user_username_check` failed. The test was split (length vs. characters, with a 50-character boundary); the schema was right. **Weak red caught:** the gateway's red phase was only a compile error, so two mutations were applied afterwards (first flavor text instead of the latest; no soft-hyphen join) and exactly the 2 matching tests failed. **Bug found in review:** upstream data breaking a domain rule (no types, stat 0) would have been a 500; a test was added first, then translated to 503 (D31). **Test artifact:** MockMvc doesn't strip HEAD bodies, so the empty body is asserted in Newman against the real server instead. **Hit counts:** the shared fake server's per-path counters are cumulative across tests, so the assertion became a delta. **Warnings removed:** Actuator's cache metrics (fixed with `recordStats`) and JDK 27's JNA restricted-method warning in Docker (`--enable-native-access=ALL-UNNAMED` for tests). Other mutation checks: dropping the email index and the cascades failed exactly 2 schema tests; a sequential executor failed both concurrency tests; removing the evolution cache failed the cache test. |
| 2026-10-15 | US02 plan: case-insensitive email/username | The user asked for `citext`, then for it to work with Hibernate. Spiked it (Boot 4.1.1, Hibernate 7, Flyway, Postgres 18 via Testcontainers, 11 tests). | **Findings:** (1) a plain `@Column(columnDefinition="citext")` binds `String` as `varchar`, so `citext = varchar` resolves to *case-sensitive* text equality and `findByEmail("ASH@…")` found nothing; (2) `@ColumnTransformer(write="?::citext")` doesn't affect `WHERE` parameters; (3) **`@JdbcTypeCode(SqlTypes.OTHER)`, the first fix the AI tried, silently stored serialized bytes** (`\x4173…`), caught only by a `CHECK` regex; (4) a custom `VarcharJdbcType` binding with `Types.OTHER` worked. **Outcome:** the user judged it too complex, so it was reverted to `lower()` unique indexes (D24). That design was re-verified on Postgres 18: case-variant duplicates are rejected, and `EXPLAIN` shows the `lower()` lookup uses the index while `upper()` (Spring Data's `IgnoreCase`) doesn't. Lesson: constraints caught an AI-chosen mapping that corrupted data. |
| 2026-10-15 | Black-box API tests | "Create Postman-like JSON collections with calls, params and edge cases, then run them against the real server" | **The first run found a real bug** the 90 JUnit tests had missed: `page=1&page=2` → 200, silently serving page 1. My expectation of 400 was the designed contract and no JUnit test covered it. Fixed with TDD (red controller test, then interceptor, then green), and the rerun went from 3 failed assertions to 0 of 140. Lesson for the presentation: each test layer has blind spots, so testing against a real running server pays off. |
| 2026-10-15 | US01 implementation | "Implement US01 with unit and integration tests, no dumb tests…" Followed the skill's TDD recipe from the inside out, with a red run before each green. | **Validation by mutation testing:** (1) running the interactor on a sequential executor made both concurrency tests fail, so they really detect concurrency and ordering; (2) the first caching mutation (removing `@EnableCaching`) broke *every* test because the `CacheManager` bean disappeared. That mutation was too coarse, so it was redone by removing only `@Cacheable`, and then exactly the cache test failed. **Fixed:** Mockito self-attach warning (a future JDK will block it), now loaded as `-javaagent`. **Cleaned:** an over-engineered baseline-hit helper in the integration test was replaced with a local variable. **Smoke script bug:** an unquoted `?` in a zsh URL was treated as a filename wildcard. |
| 2026-10-15 | Architecture guard (ArchUnit) | Added `CleanArchitectureTest` | **Caught 1:** with ArchUnit 1.4.1 every layer was "empty". The cause was `Unsupported class file major version 71` (Java 27) hidden in the test output, so the rules would have passed vacuously; upgrading to 1.5.1 fixed it. **Caught 2:** the AI-written rule `.and().haveSimpleNameEndingWith("UseCase").or()...("Gateway")` has left-to-right precedence, so it would have applied to *any* class named `*Gateway` anywhere; it was rewritten as `haveNameMatching(".*(UseCase\|Gateway)")`. **Validated** by planting violations (a domain record using Spring's `HttpStatus` plus a web DTO, and a `class` Gateway): 3 rules failed as expected, then the plants were removed. |
| 2026-10-14 | US01 response DTOs (TDD) | Wrote the JSON-shape tests first, then the records | **Correction 1:** `assertThat(mapper.valueToTree(...))` was ambiguous because `valueToTree` is generic, so the type had to be given explicitly. **Correction 2:** the tree comparison failed even though the JSON printed identically. `long totalItems` became a `LongNode` while the expected `1025` parsed as an `IntNode`. The fix was to compare the *wire format* (serialize, then re-parse), which is also what actually matters to clients. |
| 2026-10-14 | Spec analysis | Asked Claude Code to read and summarize `tech.pdf` | It flagged gaps between the user stories and the technical requirements: full CRUD vs. US04's update-only, and auth required although no story mentions it |

---

## Session history

### 2026-10-14: Session 1
- Read the spec and confirmed what has to be delivered
- Created `HANDOFF.md` and `WORK_TRACKER.md`
- Analyzed the generated Spring Boot scaffold and built it with JDK 27. The build and the `contextLoads` test pass.
  - JDK 27 isn't installed system-wide. It is the Gradle-provisioned Temurin 27 at `~/.gradle/jdks/eclipse_adoptium-27-aarch64-os_x.2/jdk-27+35/Contents/Home`, so run builds with `JAVA_HOME` pointing there.
- Registered US01 (Pokémon Enumeration)
- Researched PokeAPI (docs and live calls) and mapped the US01 fields. Found the N+1 fan-out on the listing endpoint.
- Built the US01 response contract with TDD (`PokemonSummaryResponse`, `AbilityResponse`, `PageResponse<T>`): 4 new tests, 5/5 passing

### 2026-10-15: Session 2
- Designed the Clean Architecture package skeleton (D8 to D11), with a `package-info.java` documenting each layer
- Created `CLAUDE.md` and `.claude/skills/clean-architecture/SKILL.md`, plus the `CleanArchitectureTest` ArchUnit guard (D12, D13)
- Build is green: 10 tests (5 arch, 4 DTO, 1 context)
- Transcribed the spec to `docs/assessment-spec.md` (D20)
- **Implemented US01** (D14 to D19). 90 tests green, no build warnings, smoke-tested against live PokeAPI.
- Added the black-box API test layer (`api-tests/`, D21). It found the duplicate-param bug, which is now fixed (D22). JUnit has 93 tests and Newman runs 26 requests with 140 assertions, all green.
- Added the GitHub Actions CI workflow (D23). Linted, and the build was simulated in a clean JDK 27 Linux container.
- Next: confirm the genus and alternate-forms choices, then US02 (detail view: stats, description, evolution chain) ("category" meaning, alternate forms), then design and TDD the US01 backend

### 2026-10-15: Session 3
- Created `develop` (merged `us01`) and `us02` (D32). Wrote, revised and got approval for the US02 plan (v5): schema + Flyway; the `citext` spike, then dropped in favor of `lower()` indexes (D24)
- Refactored `findCategory` into `findSpecies` (D26)
- Added the PostgreSQL schema (V1, V2) with Flyway, Testcontainers and `SchemaMigrationTest`, 40 constraint tests (D27, D28)
- **Implemented US02** (D25, D30, D31): 226 JUnit tests green, no warnings
- Docker: Dockerfile, compose (app + Postgres + `test` + `api-tests`), Actuator health, CI smoke job (D29). Verified from a fresh clone with only Docker
- Newman: the US02 collection (29 requests / 107 assertions), with US01 still green
- README written
- Next: push `us02` and open a PR to `develop` (awaiting the user's go-ahead), then US03 (sync to the local DB)

### 2026-10-15: Session 3 (continued): authentication
- Branch `auth` cut from `us02` (keeps the US02 PR clean)
- Researched the auth requirements in the spec and wrote `docs/plans/auth-plan.md`; implemented JWT auth, secure by default (D34–D40)
- Pre-inserted demo users through a Flyway repeatable seed in the `demo` profile (D36)
- 325 JUnit tests green, no warnings; Newman: auth 28/91, US01 29/149, US02 32/116, all green against the compose stack
- Next: push `us02` and `auth` and open PRs (awaiting the user's go-ahead), then US03
