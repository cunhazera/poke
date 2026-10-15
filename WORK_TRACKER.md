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
| Backend | PokeAPI client integration | ✅ Done (listing; US02 will extend it) |
| Backend | US01: paginated listing | ✅ Done |
| Backend | US02: detail view | ⬜ Not started |
| Backend | US03: sync to local DB | ⬜ Not started |
| Backend | US04: local CRUD and validation | ⬜ Not started |
| Backend | Auth (register, login, protected routes) | ⬜ Not started |
| Backend | Caching (nice to have) | ✅ Done (Caffeine, D18) |
| Backend | Error handling (global, consistent format) | ✅ Done (ProblemDetail, D17) |
| Frontend | Project scaffold | ⬜ Not started |
| Frontend | List, detail, edit and auth screens | ⬜ Not started |
| Delivery | Seed data and mock credentials | ⬜ Not started |
| Delivery | Dockerfile / docker-compose | ⬜ Not started |
| Delivery | README | ⬜ Not started |
| Delivery | Public Git repo | ⬜ Not started |
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
| D7 | 2026-10-14 | Web starter: `spring-boot-starter-webmvc` (the Boot 4 name), which brings in Jackson 3 (`tools.jackson.*`) | Needed for REST and JSON | WebFlux (not needed; servlet + virtual threads is simpler) |

### Open decisions
- [ ] Database (e.g. PostgreSQL, H2 for dev/tests)
- [ ] Auth mechanism (e.g. JWT with Spring Security)
- [ ] Cache implementation (e.g. Caffeine, Redis)
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
- Next: confirm the genus and alternate-forms choices, then US02 (detail view: stats, description, evolution chain) ("category" meaning, alternate forms), then design and TDD the US01 backend
