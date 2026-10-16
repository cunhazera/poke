# Plan: US03 (Data Synchronization)

> Status: **implemented 2026-10-16**, merged in PR #3 (WORK_TRACKER D41–D46). Branch `us03`, cut from `develop`. Same patterns as before: the clean-architecture skill, TDD from the inside out, Testcontainers, ProblemDetail errors, and a Newman collection.

## 1. Goal
**Spec:** "Develop a mechanism to persist Pokémon data into a local relational store. This replication layer is intended to facilitate the
addition of proprietary fields (localized nomenclature, geographical metadata, internal classification tags)."

**As an** administrator, **I want to** copy a Pokémon from PokeAPI into our database **so that** we can enrich it with our own data.
**As a** signed-in user, **I want to** browse the local copies, including their proprietary fields.

## 2. Decisions
| # | Decision | Why |
|---|---|---|
| S1 | **On-demand sync, one Pokémon per call**, plus pre-inserted demo copies | It's explicit and bounded (2 cached upstream calls), and there's no surprise traffic to PokeAPI. A bulk or scheduled sync can be added later on the same use case. |
| S2 | **Re-sync refreshes only the catalog-owned fields and never touches the proprietary ones** | This is the point of the replication layer. The rule lives in the domain (`LocalPokemon.refreshedFrom`), not in SQL, so it's unit-tested. |
| S3 | The local copy splits into `PokedexEntry` (from PokeAPI: name, category, description, height, weight, images, types, abilities, stats) and `ProprietaryData` (localized names, region, habitat, coordinates, tags) | Makes the boundary visible in the code. US04 edits only `ProprietaryData` and the editable catalog fields. |
| S4 | Local resources are addressed by their **surrogate `id`**. Sync is addressed by **Pokédex number**. | Locally created Pokémon (US04) have no Pokédex number (schema decision D27) |
| S5 | **Sync needs `ADMIN`; local reads need any signed-in user** | Sync writes to a shared store. This is also the first role rule, so the 403 path is exercised for real. |
| S6 | Concurrency: `version` with optimistic locking, plus the unique `pokedex_number` | Two simultaneous syncs or edits → **409**, never a lost update |
| S7 | Sync reads through the existing **cached** catalog gateway | PokeAPI data is static (24 h cache, D18) |

## 3. API
| Endpoint | Access | Result |
|---|---|---|
| `POST /api/v1/local-pokemon/sync/{pokedexNumber}` | ADMIN | **201** + `Location` on the first sync, **200** on a re-sync. Errors: 400 invalid number · 404 no such species · 503 upstream · 409 concurrent sync · 401/403 |
| `GET /api/v1/local-pokemon?page&size` | signed in | Page of local copies, in Pokédex order (local-only ones last), same envelope as US01 |
| `GET /api/v1/local-pokemon/{id}` | signed in | The local copy · 400 invalid id · 404 unknown |

```json
{
  "id": 1, "pokedexNumber": 25, "name": "pikachu", "category": "Mouse Pokémon", "description": "…",
  "heightM": 0.4, "weightKg": 6.0, "spriteUrl": "…", "imageUrl": "…", "types": ["electric"],
  "abilities": [ { "name": "static", "hidden": false } ], "stats": { "hp": 35, "…": 0, "total": 320 },
  "localizedNames": { "ja": "ピカチュウ", "pt-BR": "Pikachu" },
  "region": "Kanto", "habitat": "forest", "coordinates": { "latitude": 35.6762, "longitude": 139.6503 },
  "tags": ["mascot", "starter"], "syncedAt": "2026-10-15T12:00:00Z", "version": 0
}
```

## 4. Layers
| Layer | Classes |
|---|---|
| domain | `LocalPokemon` (+ `fromCatalog`, `refreshedFrom`), `PokedexEntry`, `ProprietaryData`, `Coordinates`, `LocalPokemonNotFoundException`, `ConcurrentModificationException` |
| application | in: `SyncPokemonUseCase` + `SyncPokemonCommand` → `SyncResult(pokemon, created)`, `ListLocalPokemonUseCase` + `Query`, `GetLocalPokemonUseCase` · out: `LocalPokemonGateway` · three interactors (the sync reuses `PokemonCatalogGateway` and runs its two calls concurrently) |
| adapter.out.persistence | `PokemonJpaEntity` (+ `@ElementCollection` for types, abilities, localized names and tags; `@Version`), `PokemonJpaRepository`, `JpaLocalPokemonGateway` (maps violations and stale versions to 409) |
| adapter.in.web | `LocalPokemonController`, `LocalPokemonResponse` (+ `CoordinatesResponse`), mapper, handler additions |
| config | role rule for sync in `SecurityConfig`; wiring in `LocalPokemonConfig` |
| seed | `db/seed/R__demo_pokemon.sql` (demo profile): a few synced Pokémon **with proprietary data**, generated from a real sync |

## 5. Out of scope (US04 and later)
Editing proprietary fields, creating local-only Pokémon, delete, bulk or scheduled sync, filtering the local list.
