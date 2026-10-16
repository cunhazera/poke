# API tests (black-box)

Postman v2.1 collections that exercise the **running** server over real HTTP, backed by the **live PokeAPI**.
They complement the JUnit suite, which runs against a fake PokeAPI. These tests catch what JUnit can't:
real upstream data, real latency and caching, and the packaged application's actual configuration.

```
api-tests/
├── collections/   one collection per user story (assertions in each request's "Tests" tab)
├── environments/  local.postman_environment.json  (baseUrl)
├── generators/    optional Python builders for new collections (see generators/README.md)
└── run.sh         runs every collection with Newman
```

## Run
```bash
docker compose up -d --build           # app + PostgreSQL
docker compose run --rm api-tests      # Newman in a container, against http://app:8080
```
Without Docker for the tests: `api-tests/run.sh [baseUrl]` (needs Node; default `http://localhost:8080`).
Or import the collections and the environment into Postman and use **Run collection**.

The collections expect the **demo profile** (pre-inserted users `ash`/`pikachu123` and `oak`/`professor123`), which compose enables.

## Coverage: Users and authentication (`/api/v1/auth`)
| Folder | What it checks |
|---|---|
| 1. Demo accounts | Login as `ash` (by username) and `oak` (by email, in another case); `/me` shows USER and ADMIN |
| 2. Registration | 201 contract without the password; a `role: ADMIN` field is ignored; 409 for a username or email taken in another case; 400 for a short or 73-byte password, a bad username or email, missing fields, malformed JSON; 415 for a form body |
| 3. Login | A new user logs in by email in any case; a wrong password, an unknown user (same message) and a wrong-case password → 401 with `WWW-Authenticate: Bearer`; blank → 400 |
| 4. Protected vs public | `/me` without a token, or with a malformed, tampered or Basic one → 401 ProblemDetail; the catalog and health stay public; `/actuator/env` is not |

## Coverage: US03 Data Synchronization (`/api/v1/local-pokemon`)
| Folder | What it checks |
|---|---|
| 1. Pre-inserted demo data | The six demo Pokémon in Pokédex order, with the full contract; Pikachu's catalog fields and proprietary data (localized names, region, habitat, coordinates, tags) |
| 2. Sync (ADMIN) | A new sync → 201 + `Location` (200 when re-run); re-syncing Pikachu → 200, same id, version + 1, **proprietary data untouched**; #1026 and #10001 → 404; #0 and #abc → 400 |
| 3. Access control | A USER syncing → 403; anonymous sync, list and read → 401 |
| 4. Errors | Unknown local id → 404; id 0 and abc → 400; page size 51 → 400 |

The collections can be re-run against the same database: a sync that already happened answers 200 instead of 201.

## Coverage: US04 Local Data Modification (`/api/v1/local-pokemon`, full CRUD)
| Folder | What it checks |
|---|---|
| 1. Setup | Log in as `ash` and `oak`; sync #2 ivysaur to edit |
| 2. Editing a synced Pokémon | A proprietary edit → 200, version + 1, catalog untouched; a **re-sync keeps the edit**; an edit on a stale version → 409; catalog fields → 400 "Catalog fields not editable"; neither rejected edit changed anything |
| 3. A local Pokémon, create to delete | Create → 201 + `Location`, no Pokédex number, units and stat total; a USER reads it; replace it whole → 200; a proprietary-only edit of a local Pokémon → 400 (`name`); its own name and `pikachu` → 409 "Name taken"; delete → 204, then 404 on read and on a second delete |
| 4. Validation | Missing fields listed in `errors`; an unknown field (`pokedexNumber`); height 0.25; a misspelled or repeated type; stat 256; a fractional or non-numeric stat (`stats.hp`); a capitalized name; a `javascript:` URL; latitude 91; a tag with spaces; malformed JSON; a form body → 415; an edit without a version; id 0 → 400; unknown id → 404 |
| 5. Access control | A USER creating, editing or deleting → 403; anonymous → 401 |

Re-runnable: it edits #2 ivysaur (not one of the demo Pokémon the US03 collection checks) and creates and deletes its own uniquely named local Pokémon.

## Coverage: Search by name (`?name=` on the Pokédex, `?q=` on the local store)
| Folder | What it checks |
|---|---|
| 1. Pokédex search | `char` → exactly the 7 live matches (chimchar and pecharunt too), with the listing contract; case; `Mr. Mime` → mr-mime; `farfetch'd` → farfetchd; paging over matches (`saur`, page 2 of 2); no match → empty page with zero totals; blank → whole catalog; a repeat is served from the cache (< 150 ms) |
| 2. Pokédex search errors | 51 characters, no letter or digit, a repeated `name` → 400 ProblemDetail |
| 3. Local store search | By name in any case; by Japanese (`ヒトカゲ`), accented French (`SALAMÈ`) and pt-BR localized names; no match; 51 characters → 400; anonymous → 401 |

Needs the demo data: it searches the localized names of the pre-inserted Pokémon.

## Coverage: US01 Pokémon Enumeration (`GET /api/v1/pokemon`)
| Folder | What it checks |
|---|---|
| 1. Happy path | Defaults, a middle page, the exact entry contract (sprite, category, kg, skills), and well-formedness across 50 entries |
| 2. Pagination edge cases | size 1 and 50, the last partial page (#1025), an exact page boundary, a page beyond the end, empty and unknown params |
| 3. Caching | A repeat request is served in under 150 ms and is byte-identical |
| 4. Validation errors | size 0/51/-5, page -1, offset overflow, non-numeric, decimal, int overflow, duplicate params → 400 ProblemDetail |
| 5. HTTP semantics | POST and unknown routes → 401 without a token; with the demo user's token: POST → 405 with `Allow`, unknown route → 404 ProblemDetail; `Accept: application/xml` → 406 |

## Coverage: US02 Pokémon Detail (`GET /api/v1/pokemon/{id}`)
| Folder | What it checks |
|---|---|
| 1. Happy path: chain shapes | The exact contract, units, stats (range and total), clean description text; chains that are linear (Ivysaur, a middle stage), branching (Eevee ×8, and Vaporeon showing the tree from its root), branching two levels deep (Wurmple), starting at a baby (Pichu for Pikachu), single-stage (Tauros), and the last entry (#1025) |
| 2. Consistency with the listing | The detail of #1 has the same id, name, sprite, category, weight and abilities as its US01 listing entry |
| 3. Caching | A repeat Eevee request is served in under 150 ms and is byte-identical |
| 4. Invalid id (400) | `0`, `-1`, `abc`, `1.5`, int overflow, `+1`, `0x1F`, `1e3`, blank → ProblemDetail "Invalid Pokémon id" |
| 5. Not found (404) | #1026, an alternate form (#10001, which has no species) and int max |
| 6. HTTP semantics | POST/DELETE → 401 without a token and 405 with one; HEAD → headers and an empty body; trailing slash → 404; XML → 406 |

Every request also asserts that it responds within 10 s and that no stack traces or internal hosts leak.

> The tests depend on live PokeAPI data (e.g. 1025 species, #1025 = pecharunt). If PokeAPI adds a generation,
> update the totals.
