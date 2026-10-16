# API tests (black-box)

Postman v2.1 collections that exercise the **running** server over real HTTP, backed by the **live PokeAPI**.
They complement the JUnit suite, which runs against a fake PokeAPI. These tests catch what JUnit can't:
real upstream data, real latency and caching, and the packaged application's actual configuration.

```
api-tests/
├── collections/   one collection per user story (assertions in each request's "Tests" tab)
├── environments/  local.postman_environment.json  (baseUrl)
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
