# Plan: US04 (Local Data Modification) + full CRUD

> Status: **implemented 2026-10-15** (recommendations accepted by the user; WORK_TRACKER D47–D53). Branch `us04`, cut from `us03`
> (US04 builds on the US03 store; it will be rebased onto `develop` once `us03` is merged). Same patterns as before: the clean-architecture skill, TDD from the inside out,
> Testcontainers, ProblemDetail errors, and a Newman collection.

## 1. Goal
**Spec:** "Implement functionality to update any Pokémon stored in the local database. Ensure robust validation: return 404 for
missing records, 400 for malformed payloads, plus further defensive logic." The API section also asks for **comprehensive CRUD**
(D2), so US04 adds create and delete for local Pokémon too.

**As an** administrator, **I want to** edit our proprietary data on any local Pokémon, create Pokémon that exist only in our
store, and delete local copies, **so that** the local store is ours to curate.

## 2. Questions (each with a recommendation)
| # | Question | Recommendation | Why | Alternatives |
|---|---|---|---|---|
| Q1 | What can be edited on a **synced** Pokémon? | **Only the proprietary fields.** Catalog fields belong to PokeAPI; the next sync would overwrite them anyway. | Keeps the US03 rule (S2) honest: an edit that a re-sync silently undoes is a bug waiting to happen. | Everything editable, and sync overwrites (surprising data loss) |
| Q2 | And on a **locally created** Pokémon? | **Everything** (catalog fields + proprietary), since there is no upstream owner. | There's nothing to sync from. | — |
| Q3 | Endpoint shape | **`PUT /{id}`** with the full editable representation. For a synced Pokémon the body carries **only the proprietary fields**: a catalog field in the body → **400** "catalog fields of a synced Pokémon are owned by PokeAPI; re-sync instead". For a local one, the body carries both. | One endpoint, a full replacement (simple, idempotent, no null-vs-absent ambiguity), and the rule from Q1 is enforced explicitly, not silently ignored. | `PATCH` with JSON Merge Patch (partial updates, but null-means-delete semantics and more code); two endpoints `/proprietary` + `/` |
| Q4 | Optimistic locking from the client | **`version` is required in the PUT body.** A stale version → **409** (reuses `ConcurrentUpdateException`). Missing → 400. | The version is already in every response, so the frontend just echoes it back. Two editors can't overwrite each other. | `ETag`/`If-Match` + 428 (more HTTP-correct, more plumbing for the SPA); last write wins |
| Q5 | Who may write? | **ADMIN for create, update and delete**; reads stay open to any signed-in user. | Consistent with sync (S5): writes to the shared store are an admin job. `ash` shows the read-only view, `oak` the editor. | Any signed-in user may edit |
| Q6 | Delete semantics | **Hard delete**, `DELETE /{id}` → **204**, 404 if missing. Child rows go via the existing `ON DELETE CASCADE`. A deleted synced Pokémon can be synced again (201). | Simple and reversible for catalog Pokémon (re-sync); the schema already supports it. | Soft delete (a `deleted_at` column, filters everywhere) |
| Q7 | Unknown JSON fields (e.g. `id`, `pokedexNumber`, `syncedAt`, typos) | **400**, naming the field. Only for the write DTOs (`@JsonIgnoreProperties(ignoreUnknown = false)` + Jackson's `FAIL_ON_UNKNOWN_PROPERTIES` for those types). | Defensive: a client sending `pokedexNumber` must learn it can't change it, rather than think it did. | Ignore unknown fields (Jackson's default) |
| Q8 | Height and weight in requests | Metres and kilograms like the responses, **at most one decimal** (0.25 m → 400), converted to dm/hg in the domain. | The API speaks one unit system (D6); the store keeps PokeAPI's integers without rounding surprises. | Accept dm/hg in requests (two unit systems) |
| Q9 | Types for local Pokémon | **One or two of the 18 canonical types**, distinct, lowercase (`fire`, `water`…). | Defensive: `"fyre"` or `["fire","fire"]` are typos, not types. | Any non-blank string (current `PokedexEntry` rule) |
| Q10 | Duplicate names | Name is unique case-insensitively (existing `uq_pokemon_name`) → **409** "name taken". A local Pokémon named like an unsynced species (e.g. `mew`) makes a later sync of #151 return 409 with the same message. | Relies on the index (race-safe, like D38); no extra upstream call on create. | Checking PokeAPI on create (an upstream dependency on a local write) |

## 3. API
| Endpoint | Access | Result |
|---|---|---|
| `POST /api/v1/local-pokemon` | ADMIN | **201** + `Location`, body = the new Pokémon (`pokedexNumber` and `syncedAt` null, `version` 0). 400 malformed/invalid · 409 name taken |
| `PUT /api/v1/local-pokemon/{id}` | ADMIN | **200** + the updated Pokémon (version + 1). 400 invalid id/payload/catalog field on a synced one · 404 missing · 409 stale version or name taken |
| `DELETE /api/v1/local-pokemon/{id}` | ADMIN | **204**. 400 invalid id · 404 missing |
| (US03) `GET` list/one, `POST /sync/{n}` | unchanged | |

Every error is a ProblemDetail, shaped like the register endpoint's so the frontend can highlight the right input:
missing fields are listed in an `errors` map (`stats.attack`, `types[1]`), and a broken domain rule, an unknown field or
a wrong JSON type carries a `field` (`heightM`, `pokedexNumber`, `stats.hp`).

```json
PUT /api/v1/local-pokemon/1        (synced Pokémon: proprietary fields only)
{ "version": 3,
  "localizedNames": { "ja": "ピカチュウ", "pt-BR": "Pikachu" },
  "region": "Kanto", "habitat": "forest",
  "coordinates": { "latitude": 35.6762, "longitude": 139.6503 },
  "tags": ["mascot", "starter"] }

POST /api/v1/local-pokemon          (local Pokémon: catalog + proprietary)
{ "name": "missingno", "category": "Glitch Pokémon", "description": "…",
  "heightM": 1.0, "weightKg": 1590.8, "spriteUrl": null, "imageUrl": null,
  "types": ["normal"], "abilities": [ { "name": "pressure", "hidden": false } ],
  "stats": { "hp": 33, "attack": 136, "defense": 1, "specialAttack": 6, "specialDefense": 6, "speed": 29 },
  "localizedNames": {}, "region": null, "habitat": null, "coordinates": null, "tags": ["glitch"] }
```

## 4. Defensive checks (the "further defensive logic")
- Strict path ids (reuses `PathIds`): `abc`, `0`, `-1`, overflow → 400
- Malformed JSON, wrong JSON types (`"hp": "high"`), a missing body or the wrong `Content-Type` → 400 / 415, always a ProblemDetail
- Required fields present (Bean Validation on the DTO, shape only); domain value objects own the rules (D37 split)
- Domain rules → 400 with the field: stats 1..255, ≤ 3 abilities with distinct names, types per Q9, name 1–100, image URLs must be `http(s)`, ≤ 500 chars; language tags, tag slugs and place lengths (existing `ProprietaryData`); latitude/longitude both or neither and in range (existing `Coordinates`)
- Request size limits: at most 20 localized names and 20 tags; description ≤ 2000 chars
- Concurrency: stale `version` → 409; a race between load and flush → 409 (Hibernate `@Version`, as in US03)
- Authorization: anonymous → 401, USER → 403, for all three writes

## 5. Layers
| Layer | Classes |
|---|---|
| domain | `LocalPokemon.withProprietary(data, version)` and `LocalPokemon.local(entry, data)` / `withEntry(...)` (the Q1/Q2 rule: a catalog change on a synced Pokémon throws `CatalogFieldNotEditableException`); `PokemonType` (the 18 canonical types); stricter `PokedexEntry` checks for local entries; `PokemonNameTakenException` |
| application | in: `CreateLocalPokemonUseCase` + `Command`, `UpdateLocalPokemonUseCase` + `Command` (id, expected version, catalog part or null, proprietary part), `DeleteLocalPokemonUseCase` · out: `LocalPokemonGateway` gains `delete(id)` (returns whether it existed) and maps the name index to `PokemonNameTakenException` · three interactors |
| adapter.out.persistence | `JpaLocalPokemonGateway`: delete, name-index violation → name taken (distinguished from the Pokédex-number index by constraint name) |
| adapter.in.web | `CreateLocalPokemonRequest`, `UpdateLocalPokemonRequest` (+ nested DTOs), mapper, three controller methods; `GlobalExceptionHandler` maps `InvalidPokemonDataException` → 400 with `errors` (currently unmapped: it only came from sync, which turns it into 503), `CatalogFieldNotEditableException` → 400, name taken → 409 |
| config | `SecurityConfig`: `POST/PUT/DELETE /api/v1/local-pokemon/**` → ADMIN; wiring in `LocalPokemonConfig` |

## 6. Tests
- Domain: the Q1/Q2 rule, `PokemonType`, the new entry checks, version handling
- Interactors with the in-memory gateway: update keeps the catalog part, stale version → conflict, missing → not found, delete then re-sync creates again
- `JpaLocalPokemonGatewayTest` (Testcontainers): delete cascades child rows, name collision → name taken (both create and sync), version bump on update
- `LocalPokemonControllerTest`: the contract and every 400/401/403/404/409/415 path
- Integration: edit as `oak` → re-sync → proprietary edit still there; delete → re-sync → 201
- Mutation checks on the key rules (catalog-field guard, stale version, unknown-field rejection)
- Newman `us04-local-crud`, re-runnable (creates and deletes its own local Pokémon)

## 7. Out of scope
Partial updates (`PATCH`), filtering or searching the local list, audit history, bulk edits, editing the evolution chain (not stored, D27).
