# Poke

A Pokémon management API built with **Java 27 + Spring Boot 4**, using Uncle Bob's **Clean Architecture** and **TDD**.
It reads from [PokeAPI](https://pokeapi.co/docs/v2), caches what it reads, and keeps a local **PostgreSQL** store for
proprietary data. This is the Ballast Lane Java technical assessment (spec: [`tech.pdf`](tech.pdf), transcribed in
[`docs/assessment-spec.md`](docs/assessment-spec.md)).

| Story | Status | Endpoint |
|---|---|---|
| US01: Pokémon enumeration (paginated: sprite, category, mass, abilities) | ✅ | `GET /api/v1/pokemon?page&size` |
| US02: Detailed view (image, base stats, description, evolution chain) | ✅ | `GET /api/v1/pokemon/{id}` |
| US03: Data synchronization to the local database (with proprietary fields) | ✅ | `POST /api/v1/local-pokemon/sync/{n}`, `GET /api/v1/local-pokemon[/{id}]` |
| US04: Local updates with validation (+ create and delete: full CRUD) | ✅ | `POST /api/v1/local-pokemon`, `PUT` and `DELETE /api/v1/local-pokemon/{id}` |
| Users and authentication (register, login, protected routes) | ✅ | `/api/v1/auth/register`, `/login`, `/me` |
| Frontend | ⏳ | — |

## Quick start

The only requirement is **Docker** (with Compose v2).

```bash
git clone https://github.com/cunhazera/poke.git && cd poke
docker compose up --build          # app + PostgreSQL; Flyway migrates the database on startup
curl http://localhost:8080/api/v1/pokemon/133
```

**Demo data.** The `demo` profile, which compose turns on, pre-inserts these users and six local Pokémon
(#1, #4, #7, #25, #133, #150) with proprietary data:

| Username | Email | Password | Role |
|---|---|---|---|
| `ash` | `ash@pallet.town` | `pikachu123` | USER |
| `oak` | `oak@pallet.town` | `professor123` | ADMIN |

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"login":"ash","password":"pikachu123"}' | jq -r .accessToken)
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/auth/me
```

The app listens on `http://localhost:8080`. Stop it with `Ctrl+C`, or with `docker compose down`. Add `--volumes` to also wipe the database.

## Running the tests

Everything runs in containers, so no local JDK or Node is needed:

| What | Command | Notes |
|---|---|---|
| Unit, integration and architecture tests | `docker compose run --rm test` | Runs `./gradlew build` on JDK 27. Integration tests start their own PostgreSQL 18 through Testcontainers, using the mounted Docker socket. |
| Black-box API tests (Newman) | `docker compose up -d --build`<br>`docker compose run --rm api-tests` | Runs the Postman collections in [`api-tests/`](api-tests/README.md) against the running app and the **live** PokeAPI |

CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs the build and tests on every push and pull request. It
also builds the Docker image and smoke-tests the compose stack.

**Test layers:**
- **Domain and use-case unit tests:** plain JUnit, with an in-memory fake catalog. Nothing is mocked that the code under test doesn't actually call.
- **Adapter tests:** the real HTTP client runs against a fake PokeAPI server that serves trimmed real payloads.
- **`SchemaMigrationTest`:** runs the real Flyway migrations on PostgreSQL 18 and proves every constraint with an `INSERT`.
- **Web-slice tests:** pin the exact JSON contract and every error.
- **End-to-end tests:** start the full Spring context, including the cache.
- **ArchUnit:** enforces the dependency rule.
- **Newman collections:** run against the real server.

## API

All errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json`, with the same shape
everywhere. They never include stack traces or internal hosts.

### `GET /api/v1/pokemon?page=0&size=20` (US01)
A page of Pokémon in Pokédex order. There is one entry per species (1025), and alternate forms are excluded.
`size` is 1–50 and `page` is zero-based.

```json
{
  "items": [
    { "id": 1, "name": "bulbasaur", "spriteUrl": "https://…/1.png", "category": "Seed Pokémon", "weightKg": 6.9,
      "abilities": [ { "name": "overgrow", "hidden": false }, { "name": "chlorophyll", "hidden": true } ] }
  ],
  "page": 0, "size": 20, "totalItems": 1025, "totalPages": 52, "hasNext": true, "hasPrevious": false
}
```

### `GET /api/v1/pokemon/{id}` (US02)
`id` is the national Pokédex number. `evolutionChain` is a **tree** that starts at the chain's first stage, since chains can
branch (Eevee has 8 evolutions). `description` is the latest English Pokédex entry.

```json
{
  "id": 133, "name": "eevee", "category": "Evolution Pokémon",
  "imageUrl": "https://…/official-artwork/133.png", "spriteUrl": "https://…/133.png",
  "description": "Harbors the potential to evolve into manifold forms. …",
  "heightM": 0.3, "weightKg": 6.5, "types": ["normal"],
  "abilities": [ { "name": "run-away", "hidden": false }, { "name": "adaptability", "hidden": false },
                 { "name": "anticipation", "hidden": true } ],
  "stats": { "hp": 55, "attack": 55, "defense": 50, "specialAttack": 45, "specialDefense": 65, "speed": 55, "total": 325 },
  "evolutionChain": { "id": 133, "name": "eevee", "spriteUrl": "https://…/133.png", "evolvesTo": [
    { "id": 134, "name": "vaporeon", "spriteUrl": "https://…/134.png", "evolvesTo": [] }, "…"
  ] }
}
```

| Status | When |
|---|---|
| 400 | `page`/`size` out of range, malformed or repeated; `id` not a positive integer (`0`, `abc`, `1.5`, `+1`, overflow) |
| 404 | No species has that number (`/pokemon/1026`), or the number belongs to an alternate form (`/pokemon/10001`) |
| 503 | PokeAPI is down, slow (5 s read timeout) or returned invalid data |
| 500 | Anything unexpected (generic message; details only in the logs) |

### Local store (US03 and US04, `/api/v1/local-pokemon`)
Our own copies of Pokémon, which add **proprietary fields** PokeAPI doesn't have:
- **localized names**, by BCP 47 language tag
- **geographical metadata**: region, habitat and reference coordinates
- **internal classification tags**

A **re-sync refreshes only the catalog fields and never touches the proprietary ones.**

| Endpoint | Access | Result |
|---|---|---|
| `POST /sync/{pokedexNumber}` | **ADMIN** | **201** + `Location` for a first copy, **200** for a refresh. 400 invalid number · 404 no such species · 503 PokeAPI down (nothing changes) · 409 concurrent sync · 401/403 |
| `GET /?page&size` | signed in | A page of local copies in Pokédex order, with the same envelope as the listing |
| `GET /{id}` | signed in | One local copy · 400 invalid id · 404 unknown |
| `POST /` | **ADMIN** | Create a Pokémon that exists **only locally** (no Pokédex number, never synced): **201** + `Location`. 400 invalid · 409 name taken |
| `PUT /{id}` | **ADMIN** | Replace its editable data (body includes the `version` you read): **200**, version + 1. 400 invalid · 404 unknown · 409 stale version or name taken |
| `DELETE /{id}` | **ADMIN** | **204**. 404 unknown. A deleted synced copy can be synced again |

```json
{ "id": 4, "pokedexNumber": 25, "name": "pikachu", "category": "Mouse Pokémon", "description": "…",
  "heightM": 0.4, "weightKg": 6.0, "types": ["electric"], "stats": { "…": 0, "total": 320 },
  "localizedNames": { "fr": "Pikachu", "ja": "ピカチュウ", "pt-BR": "Pikachu" },
  "region": "Kanto", "habitat": "forest", "coordinates": { "latitude": 35.6762, "longitude": 139.6503 },
  "tags": ["gen-1", "mascot"], "syncedAt": "2026-10-16T01:20:31.523Z", "version": 0 }
```

**Who owns what (US04):** a **synced** Pokémon's catalog fields belong to PokeAPI, and the next sync would overwrite
them, so a `PUT` on it carries **only the proprietary fields**. Sending a catalog field gets **400** "Catalog fields not
editable". A Pokémon **created locally** has no upstream owner, so its `PUT` carries everything, as when it was created.
A `PUT` is a full replacement: an omitted proprietary field is cleared.

```json
PUT /api/v1/local-pokemon/4      (a synced Pokémon: proprietary fields only)
{ "version": 0, "localizedNames": { "ja": "ピカチュウ" }, "region": "Kanto", "habitat": "forest",
  "coordinates": { "latitude": 35.6762, "longitude": 139.6503 }, "tags": ["mascot"] }

POST /api/v1/local-pokemon        (a local Pokémon: catalog fields, proprietary ones optional)
{ "name": "missingno", "category": "Glitch Pokémon", "heightM": 1.0, "weightKg": 1590.8,
  "types": ["normal"], "abilities": [ { "name": "pressure", "hidden": false } ],
  "stats": { "hp": 33, "attack": 136, "defense": 1, "specialAttack": 6, "specialDefense": 6, "speed": 29 },
  "tags": ["glitch"] }
```

**Validation (400, always naming the field):**
- **Shape:** missing required fields are listed in `errors` (`stats.attack`, `types[1]`); a malformed body, a value of the wrong JSON type (`"hp": "high"`) or a fraction where an integer belongs (`"hp": 33.7`) is rejected, never truncated; a non-JSON body gets **415**.
- **Unknown fields are rejected** ("Unknown field", e.g. `pokedexNumber` or `id`), so nobody believes they changed something they can't.
- **Domain rules** (`field` in the problem): names are lowercase slugs like PokeAPI's (`mr-mime`), unique regardless of case; one or two distinct types out of the 18 canonical ones; at most three abilities with distinct names; stats 1–255; height 0–100 m and weight 0–10000 kg with at most one decimal; image URLs absolute `http(s)`; category ≤ 100 and description ≤ 2000 characters; at most 20 localized names (BCP 47 tags) and 20 tags (lowercase slugs); region and habitat ≤ 50; coordinates need both halves, in range, ≤ 6 decimals.
- **Path ids** are strict: `0`, `-1`, `abc`, overflow → 400.

Two things make concurrent writes safe:
- **Optimistic locking:** every save checks the `version` it read.
- **A unique Pokédex number** in the database.

Either way, a concurrent sync or edit gets a **409** instead of silently overwriting someone else's change.

### Authentication (`/api/v1/auth`)
Login uses stateless **JWT bearer tokens** (HS256, valid for 1 h), validated by Spring Security's resource server. Send the token as
`Authorization: Bearer <accessToken>`.

| Endpoint | Access | Body | Responses |
|---|---|---|---|
| `POST /register` | public | `{ "username", "email", "password" }` | **201** `{id, username, email, role}` · 400 (with `field` or `errors`) · **409** username or email taken |
| `POST /login` | public | `{ "login": username or email, "password" }` | **200** `{accessToken, tokenType: "Bearer", expiresIn}` · **401** |
| `GET /me` | **token** | — | **200** the current user · 401 |

**Validation rules:**
- **Username:** 3–50 of `A-Z a-z 0-9 _ . -`.
- **Email:** a valid address, at most 254 characters.
- **Password:** 8–72 bytes (BCrypt's limit).
- **Uniqueness:** username and email are each **unique regardless of case**, and both keep the casing that was typed.
- **Login:** accepts either the username or the email, in any case.

**Public vs. protected:**
- **Public:** `GET` on `/api/v1/pokemon/**`, register, login and `/actuator/health`.
- **ADMIN only:** every write to the local store: sync, create, update and delete (`POST`, `PUT`, `DELETE` on `/api/v1/local-pokemon/**`). A USER gets **403**.
- **Protected:** **everything else needs a token**, so new endpoints are protected by default. Writes such as `POST /api/v1/pokemon/1` get **401** without a token and 405 with one.
- **Errors:** 401 and 403 are ProblemDetails too, with `WWW-Authenticate: Bearer`.

**Defensive details:**
- **Passwords:** stored as BCrypt (strength 10), and never returned or logged.
- **Login failures:** a wrong password and an unknown user get the same 401. The unknown user still costs a BCrypt check, so response time doesn't reveal which accounts exist.
- **Roles:** self-registration always creates `USER`, and a `role` field in the body is ignored. (Other request bodies reject unknown fields; the auth bodies deliberately keep ignoring them.)
- **Duplicates:** the database's case-insensitive unique indexes guarantee uniqueness, even under concurrent sign-ups.
- **Startup:** the app refuses to start without a `JWT_SECRET` of at least 32 bytes.

**Caching:** PokeAPI responses are cached for 24 h with Caffeine, one cache per upstream resource. The listing and
the detail view share entries. A warm detail view takes about 4 ms. Cold, Eevee took about 2 s, because its chain has 9 stages. Failures are never cached.

## Architecture

Dependencies point **inward only**. The domain and the use cases contain no framework code, and
[`CleanArchitectureTest`](src/test/java/com/app/poke/architecture/CleanArchitectureTest.java) (ArchUnit) fails the build if that changes.

```
com.app.poke
├── domain/          Entities: immutable records with their invariants (PokemonDetail, BaseStats, EvolutionStage…)
├── application/     Use cases
│   ├── port/in/     input boundaries:  ListPokemonUseCase, GetPokemonDetailUseCase, RegisterUserUseCase, AuthenticateUserUseCase…
│   ├── port/out/    output boundaries: PokemonCatalogGateway, UserGateway, PasswordHasher, TokenIssuer
│   └── usecase/     interactors (no Spring annotations), e.g. GetPokemonDetailInteractor
├── adapter/
│   ├── in/web/      REST controllers, DTOs, mappers, GlobalExceptionHandler + security errors (ProblemDetail)
│   ├── out/pokeapi/      RestClient gateway + PokeAPI DTOs (cached)
│   ├── out/persistence/  JPA entities, Spring Data repositories, gateways (users, local Pokémon)
│   └── out/security/     BCrypt password hasher, JWT token issuer
└── config/          composition root: @Bean wiring, properties, SecurityConfig (public vs. protected routes)
```

The interactors run the upstream fan-out concurrently on virtual threads. For a detail view, the profile and species
are fetched in parallel, then the evolution chain, then every other stage's sprite in parallel.
The placement rules for new code are in [`.claude/skills/clean-architecture/SKILL.md`](.claude/skills/clean-architecture/SKILL.md).

## Database

PostgreSQL 18. The schema is owned by **Flyway** ([`src/main/resources/db/migration`](src/main/resources/db/migration)),
and an applied migration is never edited: every change gets a new `V<n>__…sql`. The tables already exist. The code that
uses them is mapped by JPA with `ddl-auto=validate`: `app_user` for authentication, and `pokemon` plus its child tables for the local store.
Demo users and Pokémon come from *repeatable* seed migrations in `db/seed/`, applied only in the `demo` profile.
The demo Pokémon's catalog columns are the app's own sync output.

```mermaid
erDiagram
    pokemon ||--o{ pokemon_type : has
    pokemon ||--o{ pokemon_ability : has
    pokemon ||--o{ pokemon_localized_name : "is known as"
    pokemon ||--o{ pokemon_tag : "is tagged"
    pokemon {
        bigint id PK
        int pokedex_number UK "null = created locally"
        varchar name UK "unique ignoring case"
        varchar category
        text description
        int height_dm
        int weight_hg
        smallint hp_attack_defense_etc "six stats, 1..255"
        varchar region "proprietary: geography"
        varchar habitat "proprietary: geography"
        numeric latitude_longitude "proprietary: both or neither"
        timestamptz synced_at
        bigint version "optimistic locking"
    }
    pokemon_type {
        bigint pokemon_id FK
        smallint slot "1..2"
        varchar name
    }
    pokemon_ability {
        bigint pokemon_id FK
        smallint slot "1..3"
        varchar name
        boolean hidden
    }
    pokemon_localized_name {
        bigint pokemon_id FK
        varchar language "BCP 47, e.g. pt-BR"
        varchar name
    }
    pokemon_tag {
        bigint pokemon_id FK
        varchar tag "slug, e.g. starter"
    }
    app_user {
        bigint id PK
        varchar username UK "unique ignoring case"
        varchar email UK "unique ignoring case"
        varchar password_hash "BCrypt"
        varchar role "USER or ADMIN"
    }
```

## Configuration

Every setting has a working default. To override one, copy [`.env.example`](.env.example) to `.env` (Compose reads it), or set the
variable in the environment.

| Variable | Default | Purpose |
|---|---|---|
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `poke` / `poke` / `poke` | PostgreSQL credentials (demo values) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/poke` | JDBC URL used by the app (compose points it at the `postgres` service) |
| `DB_PORT` / `APP_PORT` | `5432` / `8080` | Host ports published by compose |
| `POKEAPI_BASE_URL` | `https://pokeapi.co/api/v2` | Upstream catalog |
| `JWT_SECRET` | compose: a demo value; **the app has no default** | HS256 signing key, at least 32 bytes. **Set your own outside demos.** |
| `JWT_VALIDITY` | `1h` | Access-token lifetime |
| `SPRING_PROFILES_ACTIVE` | compose: `demo` | `demo` pre-inserts the demo users (Flyway `db/seed`). Leave it empty for an empty user table. |

## Project docs
- [`HANDOFF.md`](HANDOFF.md): the current state and how to resume work
- [`WORK_TRACKER.md`](WORK_TRACKER.md): progress, the decision log (D1…), scope, and how AI tooling was used and corrected
- [`docs/plans/`](docs/plans): per-story implementation plans
- [`api-tests/README.md`](api-tests/README.md): the black-box test collections

## Appendix: running without Docker
You need JDK 27 and a PostgreSQL 18 on `localhost:5432` (for example `docker compose up -d postgres`). Docker must also be running for the
Testcontainers-based tests.

```bash
JWT_SECRET=local-dev-secret-of-at-least-32-bytes SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun   # the app
./gradlew build                                                                                # all tests
```
