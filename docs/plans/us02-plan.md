# Plan: US02 (Detailed View) + database schema (Flyway) + Docker + README

> Status: **approved 2026-10-15** (v5; all §10 recommendations accepted). Branch `us02` (cut from `develop`, which has `us01` merged in).
> Follows the US01 patterns: the clean-architecture skill, TDD from the inside out, a fake PokeAPI
> with trimmed real fixtures, ProblemDetail errors, Caffeine caching and a Newman collection.
>
> **v5 changes:** `citext` dropped (too much complexity for what it buys) and **moved out of scope** (§11). Username and email are back to `VARCHAR` with `lower()` unique indexes. The requirement stands: **both are unique regardless of case.** §6.4 is now how that's queried from JPA.
>
> ~~**v4 changes:** `app_user.username` is `citext` too (Q12 resolved). New §6.4, **Hibernate mapping for `citext`**, proven by a spike on Hibernate 7 + Postgres 18; the domain no longer lowercases emails.~~ (superseded by v5)
>
> ~~**v3 changes:** `app_user.email` uses the PostgreSQL `citext` extension (§6.2, §6.3), with the case-sensitivity catch verified on Postgres 18 and its mitigation.~~ (superseded by v5)
>
> **v2 changes:** added the PostgreSQL schema covering what the spec requires (§6) and Flyway migrations in
> the Spring project (§7). Postgres joins `compose.yaml` now, so the former Q5 is resolved.

## 1. Goal

**US02 (spec):** comprehensive data for a chosen Pokémon: **image, core statistics, narrative
description, evolutionary lineage**.

**As a** user, **I want to** open a Pokémon's page **so that** I can see its artwork, base stats,
Pokédex description and full evolution chain.

**Delivery (requested alongside):**
- a **database schema** for everything the spec says must be persisted, managed by **Flyway**
- a **README** explaining how to run the project
- **everything runs in Docker** (app + PostgreSQL), so the reader installs nothing but Docker

## 2. Endpoint and contract

`GET /api/v1/pokemon/{id}`, where `id` is the national Pokédex number (1..1025), matching the US01 listing.
The example below is illustrative; the description text and the shortened chain aren't real output.

```json
{
  "id": 133,
  "name": "eevee",
  "category": "Evolution Pokémon",
  "imageUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/133.png",
  "spriteUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/133.png",
  "description": "Its genetic code is irregular. It may mutate if it is exposed to radiation from element STONEs.",
  "heightM": 0.3,
  "weightKg": 6.5,
  "types": ["normal"],
  "abilities": [ { "name": "run-away", "hidden": false }, { "name": "adaptability", "hidden": false }, { "name": "anticipation", "hidden": true } ],
  "stats": { "hp": 55, "attack": 55, "defense": 50, "specialAttack": 45, "specialDefense": 65, "speed": 55, "total": 325 },
  "evolutionChain": {
    "id": 133, "name": "eevee", "spriteUrl": "…/133.png",
    "evolvesTo": [
      { "id": 134, "name": "vaporeon", "spriteUrl": "…/134.png", "evolvesTo": [] },
      { "id": 135, "name": "jolteon",  "spriteUrl": "…/135.png", "evolvesTo": [] }
    ]
  }
}
```

- **`evolutionChain` is a tree, not a list.** PokeAPI chains branch: Eevee has 8 children, and Wurmple splits into Silcoon and Cascoon. Flattening the tree would lose that information.
- **Stats are a fixed object with named fields, not an array.** There are always exactly six, and named fields are easier for the frontend. The total is derived, never stored.
- **`types` and `heightM`** aren't required by the spec. They cost nothing, since they're in the same upstream payload, and they make the detail page useful (see Q1).

**Errors** (ProblemDetail, as in D17):

| Case | Status |
|---|---|
| `id` is non-numeric, `0`, negative or overflows (`/pokemon/abc`, `/pokemon/-1`) | **400** "Invalid Pokémon id" |
| A valid number that isn't a species: `/pokemon/99999`, or alternate forms such as `/pokemon/10001` (the Pokémon exists, its species doesn't) | **404** "Pokémon not found" |
| PokeAPI is down, times out or returns malformed data | **503** (as in US01) |
| Anything unexpected | generic **500** (as in US01) |

## 3. PokeAPI data sources (verified live 2026-10-15)

| Field | Call | Notes |
|---|---|---|
| imageUrl | `/pokemon/{id}` → `sprites.other.official-artwork.front_default` | falls back to `null` when missing |
| stats | `/pokemon/{id}` → `stats[]{base_stat, stat.name}` | map by **name**, never by position |
| types, height | `/pokemon/{id}` → `types[]` (by slot), `height` (decimetres) | |
| description | `/pokemon-species/{id}` → `flavor_text_entries[]` | see Q2 |
| evolution chain id | `/pokemon-species/{id}` → `evolution_chain.url` | |
| lineage | `/evolution-chain/{chainId}` → recursive `chain{species, evolves_to[]}` | species id parsed from the URL (D14: default Pokémon id = species id) |

**Upstream call budget per detail view (all cached):** `pokemon` + `species` (in parallel) → `evolution-chain`
→ one `pokemon` call per stage for the sprites (in parallel; see Q3). For Bulbasaur that's 2 + 1 + 3 calls cold, and **0 warm**.
The listing and the detail view share the `pokemon` cache, so stages already seen in a listing cost nothing.

**Data quirks to handle (each one gets a test):**
- Old flavor texts contain `\n`, `\f` and soft hyphens (`"A strange seed was\nplanted on its\fback"`), so whitespace must be normalized.
- Not every language has an entry. Pecharunt (#1025) has only **2** English entries, while Bulbasaur has 28, so a species with no English text needs handling.
- `evolution_details` is inconsistent: Leafeon has 6 entries, mostly location-based. See Q4.
- A single-stage chain (e.g. Tauros) has a root with no children.

## 4. Refactor US01 first (one shared species call)

US01's `findCategory(id)` fetches `/pokemon-species/{id}` and keeps only the genus. US02 needs the
same payload's flavor text and evolution-chain URL. If both stay, the same resource is fetched and cached twice.

**Change:** replace `findCategory(id) : Optional<String>` with
`findSpecies(id) : SpeciesProfile(category, description, evolutionChainId)` and have the US01 interactor use `.category()`.

This is safe under the existing tests: US01's interactor, gateway and integration tests stay green, and only the fake changes.
It lands as its own commit before any US02 code.

## 5. Implementation steps for US02 (TDD, inside → out, red before green)

| # | Layer | Work | Tests (written first) |
|---|---|---|---|
| 0 | refactor | §4: `SpeciesProfile`, `findSpecies`, rename the cache `pokeapi-species` | the existing 93 stay green; the gateway test asserts that one species call feeds both fields |
| 1 | domain | `PokedexNumber` (validates `id >= 1`), `BaseStats` (6 values in 1..255, `total()`), `Height` (decimetres → `meters()`, like `Weight`), `EvolutionStage` (recursive record: id, name, spriteUrl, `List.copyOf` children; `contains(id)`), `PokemonDetail`, `InvalidPokemonIdException` | invariants, boundaries (`0`, `-1`, `Integer.MAX_VALUE`), total math, decimetre rounding, immutability of the tree, tree search |
| 2 | port.in | `GetPokemonDetailUseCase`, `GetPokemonDetailQuery(PokedexNumber)` | query validation |
| 3 | port.out | extend `PokemonProfile` (+ `imageUrl`, `height`, `types`, `stats`); add `findEvolutionChain(chainId) : EvolutionChainShape` (a tree of ids and names; sprites are filled in by the interactor) | — (interfaces) |
| 4 | usecase | `GetPokemonDetailInteractor(catalog, executor)`: profile and species in parallel, then the chain, then stage sprites in parallel, then assemble | against `FakePokemonCatalog`: happy path; **the chain is fetched by the species' chain id, not the Pokémon id** (they differ: Eevee 133 → chain 67); a branching chain keeps its order; a missing species → 404; a stage whose Pokémon 404s → `CatalogUnavailable`; the root is the requested Pokémon or one of its ancestors (`contains` check, otherwise `CatalogUnavailable`); real concurrency (latch test, as in US01) |
| 5 | adapter.out.pokeapi | `findPokemon` maps the new fields; `findSpecies`; `findEvolutionChain` (recursive mapping); `PokeApiMapper.description` (latest English entry, whitespace normalized); `@Cacheable("pokeapi-evolution")` | `FakePokeApiServer` + **trimmed real fixtures**: `pokemon-133`, `species-133`, `evolution-chain-67` (branching), `evolution-chain-1` (linear), a species with no English flavor text, a single-stage chain; 404 / 5xx / malformed / timeout per call |
| 6 | adapter.in.web | `PokemonController#detail` (`@PathVariable`), `PokemonDetailResponse`, `BaseStatsResponse`, `EvolutionStageResponse`, mapper; handler maps `InvalidPokemonIdException` and path type mismatches → 400 | `@WebMvcTest`: the exact JSON contract (STRICT), each 400 variant never reaching the use case, 404/503/500 bodies with nothing leaked, a trailing slash, and `HEAD`/`POST` behavior |
| 7 | config | `GetPokemonDetailUseCase` bean; add the cache name | — |
| 8 | integration | `PokemonDetailIntegrationTest` (full context + fake PokeAPI + Postgres container): happy path, **second request = 0 upstream hits**, cache shared with the listing (list, then detail costs only species + chain), failures not cached | as listed |
| 9 | black-box | `api-tests/collections/us02-pokemon-detail.postman_collection.json`: Bulbasaur (linear), Eevee (branching), Tauros (single stage), Pecharunt (last entry), each 400/404 variant, contract checks; run against the dockerized server | Newman run is green |
| 10 | docs | tracker (US02 section, decisions, AI log), HANDOFF, `CleanArchitectureTest` still green | — |

## 6. Database schema (PostgreSQL 18)

### 6.1 What the spec requires us to persist

| Spec requirement | Covered by |
|---|---|
| US03: "persist Pokémon data into a local relational store" | `pokemon` + `pokemon_type` + `pokemon_ability`: everything US01 and US02 show, except the evolution tree (Q9) |
| US03: proprietary **localized nomenclature** | `pokemon_localized_name` (one name per language) |
| US03: proprietary **geographical metadata** | `pokemon.region`, `pokemon.habitat`, `pokemon.latitude` / `longitude` |
| US03: proprietary **internal classification tags** | `pokemon_tag` |
| US04: update with **defensive validation** | `CHECK` constraints as the last line of defense (the domain validates first); a `version` column for optimistic locking, so two concurrent edits get **409** instead of a silent lost update |
| Database: "a **primary entity** and a **secondary collection for user management**" | `pokemon` (primary) + `app_user` (secondary) |
| "Unique primary key and at least two descriptive attributes" | `pokemon`: `id` PK; `name`, `category`, `description`… · `app_user`: `id` PK; `username`, `email`, `role` |
| Auth: "user registration, authentication, protected vs. public routes" | `app_user.password_hash` (BCrypt) + `role` (`USER` / `ADMIN`) |
| CRUD (D2): create, read, update, delete | `ON DELETE CASCADE` on all child tables; `created_at` / `updated_at` |

### 6.2 Tables

```sql
-- V1__create_pokemon.sql
CREATE TABLE pokemon (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pokedex_number     INTEGER      UNIQUE CHECK (pokedex_number > 0),   -- NULL = created locally (Q8)
    name               VARCHAR(100) NOT NULL,
    category           VARCHAR(100),
    description        TEXT,
    height_dm          INTEGER      NOT NULL CHECK (height_dm >= 0),
    weight_hg          INTEGER      NOT NULL CHECK (weight_hg >= 0),
    sprite_url         VARCHAR(500),
    image_url          VARCHAR(500),
    hp                 SMALLINT     NOT NULL CHECK (hp              BETWEEN 1 AND 255),
    attack             SMALLINT     NOT NULL CHECK (attack          BETWEEN 1 AND 255),
    defense            SMALLINT     NOT NULL CHECK (defense         BETWEEN 1 AND 255),
    special_attack     SMALLINT     NOT NULL CHECK (special_attack  BETWEEN 1 AND 255),
    special_defense    SMALLINT     NOT NULL CHECK (special_defense BETWEEN 1 AND 255),
    speed              SMALLINT     NOT NULL CHECK (speed           BETWEEN 1 AND 255),
    -- proprietary: geographical metadata
    region             VARCHAR(50),
    habitat            VARCHAR(50),
    latitude           NUMERIC(9,6) CHECK (latitude  BETWEEN -90  AND 90),
    longitude          NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_pokemon_coordinates
        CHECK ((latitude IS NULL) = (longitude IS NULL)),                -- a coordinate needs both halves
    -- bookkeeping
    synced_at          TIMESTAMPTZ,                                       -- last PokeAPI sync; NULL if local-only
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0                    -- optimistic locking
);
CREATE UNIQUE INDEX uq_pokemon_name ON pokemon (lower(name));

CREATE TABLE pokemon_type (
    pokemon_id BIGINT      NOT NULL REFERENCES pokemon (id) ON DELETE CASCADE,
    slot       SMALLINT    NOT NULL CHECK (slot BETWEEN 1 AND 2),
    name       VARCHAR(30) NOT NULL,
    PRIMARY KEY (pokemon_id, slot)
);

CREATE TABLE pokemon_ability (
    pokemon_id BIGINT      NOT NULL REFERENCES pokemon (id) ON DELETE CASCADE,
    slot       SMALLINT    NOT NULL CHECK (slot BETWEEN 1 AND 3),
    name       VARCHAR(50) NOT NULL,
    hidden     BOOLEAN     NOT NULL,
    PRIMARY KEY (pokemon_id, slot)
);

-- proprietary: localized nomenclature
CREATE TABLE pokemon_localized_name (
    pokemon_id BIGINT       NOT NULL REFERENCES pokemon (id) ON DELETE CASCADE,
    language   VARCHAR(10)  NOT NULL CHECK (language ~ '^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$'),  -- BCP 47, e.g. "pt-BR"
    name       VARCHAR(100) NOT NULL,
    PRIMARY KEY (pokemon_id, language)
);

-- proprietary: internal classification tags
CREATE TABLE pokemon_tag (
    pokemon_id BIGINT      NOT NULL REFERENCES pokemon (id) ON DELETE CASCADE,
    tag        VARCHAR(50) NOT NULL CHECK (tag ~ '^[a-z0-9][a-z0-9-]*$'),                     -- e.g. "starter", "legendary"
    PRIMARY KEY (pokemon_id, tag)
);
CREATE INDEX ix_pokemon_tag_tag ON pokemon_tag (tag);                 -- "all Pokémon tagged X"
```

```sql
-- V2__create_app_user.sql   ("user" is a reserved word in PostgreSQL)
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL CHECK (username ~ '^[A-Za-z0-9_.-]{3,50}$'),
    email         VARCHAR(254) NOT NULL CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),   -- 254: RFC 5321 path limit; sanity only, real validation in the domain
    password_hash VARCHAR(100) NOT NULL,                              -- BCrypt, never plaintext
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- requirement: username and email are unique REGARDLESS OF CASE (AshKetchum = ashketchum, Ash@Pallet.town = ash@PALLET.TOWN)
CREATE UNIQUE INDEX uq_app_user_username ON app_user (lower(username));
CREATE UNIQUE INDEX uq_app_user_email    ON app_user (lower(email));
```

### 6.3 Design choices
- **Surrogate PK plus a unique, nullable `pokedex_number`.** Sync upserts by Pokédex number. A locally created Pokémon (the "C" in CRUD) has no Pokédex number, so it can never collide with a future sync (Q8).
- **Stats are columns, not rows.** There are exactly six, each with its own range check, so the database enforces the shape. A key/value table couldn't.
- **Child tables for the multi-valued fields** (types, abilities, localized names, tags), not arrays or JSONB. This gives real constraints and keys, and is easy to query and map with JPA `@ElementCollection` in US03.
- **Units stay as PokeAPI stores them** (decimetres, hectograms) as exact integers. Conversion to metres and kilograms stays in the domain (`Height`, `Weight`), the single source of truth.
- **Case-insensitive uniqueness** for Pokémon names, usernames and emails, through unique `lower()` expression indexes. `Bulbasaur` and `bulbasaur` can't both exist.
- **Values keep the casing the user typed.** Only the index is lowercased, so `AshKetchum` is displayed as registered, and `ashketchum` still can't register a second account.

### 6.4 Case-insensitive lookups from JPA (US03 / auth)
Plain `VARCHAR` columns map to plain `String` fields, so no custom types are needed. Two rules keep lookups consistent with the unique indexes:
- **Queries compare `lower(column) = lower(:param)`**, the same expression as the index, so Postgres can use it. For example `select u from UserJpaEntity u where lower(u.email) = lower(:email)`.
- **Spring Data's `findByEmailIgnoreCase` is avoided:** Hibernate renders it as `upper(email) = upper(?)`, which doesn't match the `lower()` index (a sequential scan). The explicit `@Query` above is used instead.
- **Registration relies on the index, not a pre-check:** a check-then-insert can race. The insert's unique violation (`DataIntegrityViolationException` on `uq_app_user_username` / `uq_app_user_email`) is translated by the gateway into a domain `UsernameTakenException` / `EmailTakenException`, which maps to **409**. A friendly pre-check may still run for a nicer message, but the index is the guarantee.

**Tests (in `UserJpaGatewayTest`, `@DataJpaTest` + Testcontainers, when the entity arrives):** register `AshKetchum` / `Ash@Pallet.town`, then `ashketchum` and `ASH@PALLET.TOWN` each → 409. Login by `ASHKETCHUM` or `ash@pallet.town` finds the user. The stored values keep their original casing. `EXPLAIN` on the lookup shows `Index Scan using uq_app_user_email`, which pins the `lower()` / `upper()` pitfall. The test sets `enable_seqscan = off`, because on a tiny table the planner prefers a sequential scan anyway. Verified on Postgres 18: `lower()` gets an index scan, `upper()` a sequential scan.

## 7. Flyway in the Spring project

| Item | Detail |
|---|---|
| Dependencies | `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime), `spring-boot-starter-jdbc` (DataSource). JPA (`spring-boot-starter-data-jpa`) is added in US03, when the first entity exists. All versions are Boot-managed. |
| Location | `src/main/resources/db/migration/V1__create_pokemon.sql`, `V2__create_app_user.sql` |
| Config | `spring.datasource.url/username/password` from env vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`) with local defaults matching compose. `spring.flyway.validate-on-migrate=true` (the default). `spring.jpa.hibernate.ddl-auto=validate` is set in US03, so **Flyway owns the schema** and Hibernate only checks it. |
| Rule | **Applied migrations are never edited.** Each change gets a new `V<n>__…` file. This goes in the skill and in CLAUDE.md. |
| Tests | **Testcontainers** (`spring-boot-testcontainers`, `testcontainers-postgresql`, `postgres:18-alpine`) with `@ServiceConnection`, in one shared `@TestConfiguration` imported by every `@SpringBootTest`. The same Postgres version runs in tests, compose and CI. No H2, so the SQL can't diverge from production. GitHub's `ubuntu-latest` runners have Docker, so CI is unchanged. |
| Schema tests | `SchemaMigrationTest` (plain JDBC against the container, no JPA yet). Checks that the migrations apply cleanly from empty and `flyway validate` passes. Then it checks that each constraint rejects what it should: duplicate Pokédex number, `Bulbasaur` vs `bulbasaur`, stat `0` and `256`, latitude without longitude, a third type slot, an invalid tag or language, an unknown role. For username and email: `AshKetchum` vs `ashketchum` and `Ash@Pallet.town` vs `ash@pallet.TOWN` are rejected, the original casing is preserved, and a malformed address or one over 254 characters is rejected. Deleting a Pokémon cascades to all four child tables. Each assertion uses a real `INSERT`, not a metadata lookup. |
| Architecture | Migrations are resources, outside the Java layers, so ArchUnit is unaffected. In US03, JPA entities go in `adapter.out.persistence` as the skill already says. |

**Seed data.** Demo users (mock credentials for the README) come in with auth. They need a BCrypt hash, so they belong in the story that adds `PasswordEncoder`. Seeded Pokémon come from the US03 sync rather than a giant SQL insert. A separate `db/seed` Flyway location is enabled only in a `demo` profile, so tests start empty (Q10).

## 8. Docker and README (separate commits on the same branch)

**Goal:** `git clone` → `docker compose up --build` → the app runs on `http://localhost:8080` with its database migrated. Nothing to install except Docker.

| File | Content |
|---|---|
| `Dockerfile` | Multi-stage: **build** on `eclipse-temurin:27-jdk` (`./gradlew bootJar`, with tests skipped in the image because CI runs them; a Gradle cache mount speeds rebuilds) → **runtime** on `eclipse-temurin:27-jre-alpine`, non-root user, `HEALTHCHECK`, layered jar. Both tags were verified to exist on Docker Hub. |
| `.dockerignore` | `build/`, `.gradle/`, `.git/`, `.idea/`, `api-tests/`, etc. |
| `compose.yaml` | `postgres` (`postgres:18-alpine`, named volume, `pg_isready` healthcheck, credentials from `.env` with demo defaults) · `app` (port 8080, `depends_on: postgres: condition: service_healthy`, so Flyway migrates on startup, env-overridable `POKEAPI_BASE_URL`, healthcheck) |
| compose profile `tools` | `test`: runs `./gradlew build` inside `eclipse-temurin:27-jdk` with the Docker socket mounted for Testcontainers, so tests need no local JDK. `api-tests`: runs Newman (`node:22-alpine` + `npx newman@6.2.3`) against `app`, waiting for its healthcheck. |
| `.env.example` | `DB_USER`, `DB_PASSWORD`, `DB_NAME` (demo values; `.env` is git-ignored) |
| `README.md` | What it is · quick start (3 commands) · running the tests (unit/integration and black-box), all through Docker · API reference (US01, US02) · **database schema** (ER diagram in Mermaid + migration rules) · architecture overview (rings diagram + link to the skill) · configuration (env vars) · project docs (tracker, handoff, spec) · *running without Docker* as a short appendix (JDK 27 + a Postgres). Kept up to date with every story. |

Small enabling change: `api-tests/run.sh` gets a `BASE_URL` override so it can target `http://app:8080` inside compose.
Optionally, CI gets a `docker build` job so the Dockerfile can't rot (Q6).

## 9. Commit order on `us02`
1. Refactor: shared species call (§4)
2. Flyway + schema + Testcontainers + `SchemaMigrationTest` (§6, §7). Existing integration tests switch to the shared container config.
3. US02 domain → web (§5, steps 1–8), as several commits
4. Docker + compose + README (§8)
5. US02 Newman collection, run against the compose stack (§5 step 9)
6. Docs: tracker, handoff, skill (migration rule)

## 10. Decisions (accepted 2026-10-15: all recommendations)

| # | Question | My recommendation |
|---|---|---|
| Q1 | Include `types` and `heightM` in the detail view (not in the spec)? | **Yes.** They're free (same payload) and a Pokédex page without types feels broken. They're also in the schema. |
| Q2 | Which flavor text is the "description"? | **The most recent English entry**, whitespace normalized. Modern wording, no ALL-CAPS "POKéMON". Alternative: the oldest (Red/Blue). |
| Q3 | Sprites on every evolution stage (costs 1 cached call per stage)? | **Yes.** The frontend needs an image per stage. The calls run in parallel, are shared with the listing cache, and are usually warm. The alternative is building the URL from the id, which would hard-code PokeAPI's sprite layout into our code. |
| Q4 | Expose evolution conditions ("level 16", "water stone")? | **Not in US02.** PokeAPI's `evolution_details` is inconsistent (Leafeon has 6 location-based entries), and a misleading condition is worse than none. Log it as a possible extra. |
| ~~Q5~~ | ~~Postgres now or in US03?~~ | **Resolved (v2):** now, with the schema and Flyway. |
| Q6 | Add a `docker build` job to CI? | **Yes, cheap.** It catches a broken Dockerfile on every PR. |
| Q7 | Git flow: one PR per story (`us02` → `develop`), then `develop` → `main` at release? Should I push `develop` and `us02` now? | **Yes** to the flow. I'll push only when you say so. |
| Q8 | Pokémon primary key: a surrogate `id` plus a unique `pokedex_number`, or the Pokédex number as the PK? | **Surrogate.** Locally created Pokémon (CRUD create) have no Pokédex number. Using it as the PK would force invented numbers that could collide with a later sync. The API keeps addressing synced Pokémon by Pokédex number. |
| Q9 | Persist the evolution chain locally? | **No.** It links species to each other, so storing one Pokémon's chain would need every member synced too. The detail view keeps reading it from PokeAPI (cached). Revisit if US03 needs offline lineage. |
| Q10 | Seed data: Flyway `db/seed` location under a `demo` profile (on in compose, off in tests)? | **Yes.** Demo users arrive with auth, and demo Pokémon with the US03 sync. |
| Q11 | Geographical metadata: `region` + `habitat` + coordinates, as above? | **Yes.** It's the smallest set that is meaningfully "geographical". Say so if you had something else in mind (e.g. a list of sighting locations). |
| ~~Q12~~ | ~~`username` as `citext` too?~~ | **Withdrawn (v5):** `citext` is out of scope (§11). Both columns use `lower()` unique indexes. |

## 11. Out of scope for this branch
- **PostgreSQL `citext`** for username and email. Tried in v3/v4 and **dropped by decision: too much complexity for what it buys.** Case-insensitive uniqueness, the actual requirement, is met with `lower()` unique indexes (§6.2). What `citext` would have cost:
  - an extension in the migrations
  - a custom Hibernate `JdbcType` (`CitextJdbcType`): with a plain `@Column(columnDefinition = "citext")`, Hibernate binds strings as `varchar` and **lookups become case-sensitive**; `@ColumnTransformer` doesn't cover `WHERE` clauses; and `@JdbcTypeCode(SqlTypes.OTHER)` **stored serialized bytes instead of the text**
  - a control test to watch for Hibernate behavior changes

  All three were verified by a spike on Hibernate 7 + Postgres 18. The findings are kept in WORK_TRACKER (decision D24 and the AI log) in case the choice is revisited.
- JPA entities, repositories and the sync itself (US03); editing (US04); auth endpoints; the frontend. The **tables** exist now; the code that uses them comes with each story.
- Lookup by name (`/pokemon/pikachu`). Easy to add later. The listing gives ids.
- Alternate forms (Megas, regional variants), consistent with D14
- Moves, locations, evolution conditions (Q4)

## 12. Definition of done
- [ ] `./gradlew build` green (all old and new tests, including ArchUnit and `SchemaMigrationTest` on a real Postgres 18), with no new warnings
- [ ] `docker compose up --build` starts Postgres, migrates it with Flyway and serves both endpoints. A fresh clone needs nothing but Docker.
- [ ] US01 + US02 Newman collections green against the dockerized app and live PokeAPI
- [ ] README covers setup, tests, API, schema and architecture
- [ ] WORK_TRACKER (US02 section, decisions D24+, AI log) and HANDOFF updated; the skill gains the "never edit an applied migration" rule
- [ ] CI green on the `us02` push, then a PR to `develop`
