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
./gradlew bootRun              # terminal 1 (see CLAUDE.md for JAVA_HOME)
api-tests/run.sh               # terminal 2; optional arg: base URL
```
Or import the collection and environment into Postman and use **Run collection**.

## Coverage: US01 Pokémon Enumeration (`GET /api/v1/pokemon`)
| Folder | What it checks |
|---|---|
| 1. Happy path | Defaults, a middle page, the exact entry contract (sprite, category, kg, skills), and well-formedness across 50 entries |
| 2. Pagination edge cases | size 1 and 50, the last partial page (#1025), an exact page boundary, a page beyond the end, empty and unknown params |
| 3. Caching | A repeat request is served in under 150 ms and is byte-identical |
| 4. Validation errors | size 0/51/-5, page -1, offset overflow, non-numeric, decimal, int overflow, duplicate params → 400 ProblemDetail |
| 5. HTTP semantics | POST → 405 with `Allow`, unknown route → 404 ProblemDetail, `Accept: application/xml` → 406 |

Every request also asserts that it responds within 10 s and that no stack traces or internal hosts leak.

> The tests depend on live PokeAPI data (e.g. 1025 species, #1025 = pecharunt). If PokeAPI adds a generation,
> update the totals.
