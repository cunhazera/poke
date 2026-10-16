# Plan: Search by name (catalog + local store)

> Status: **implemented 2026-10-16** (v2: the user narrowed v1's filters to **name-only search**; WORK_TRACKER D63–D67). Branch `search`, cut
> from `frontend` (the feature spans both sides). Same patterns as before: the clean-architecture skill, TDD from the
> inside out, Testcontainers, ProblemDetail errors, Newman cases, and Vitest + MSW on the frontend.

## 1. Goal
The spec only requires paginated browsing (US01), but "any additional functionality is welcome", and 1025 Pokémon
without a search box is the first gap a reviewer notices.

**As a** user, **I want to** find a Pokémon by typing part of its name, in the Pokédex and in the local store, **so
that** I don't page through hundreds of entries.

Two different problems:
- **The catalog (PokeAPI)** has **no search endpoint**: search is built on a cached index of every species' name.
- **The local store (our Postgres)** can query directly, and its search also matches the **localized names** (a
  proprietary field), so `ピカチュウ` finds Pikachu.

## 2. Facts checked (live PokeAPI, 2026-10-16)
- `GET /pokemon-species?limit=2000` returns **all 1025 species** (`name` + `url` with the number) in one call:
  **76 KB, about 0.2 s**. Cached for 24 h, it is a complete, cheap name index.
- The local store has a unique `lower(name)` index; localized names live in `pokemon_localized_name`.

## 3. Decisions (each with the reason)
| # | Question | Decision | Why | Alternatives |
|---|---|---|---|---|
| Q1 | How does a name match? | **Case-insensitive substring**, after normalizing the input: trim, lowercase, apostrophes dropped, spaces and dots → `-` (`"Mr. Mime"` → `mr-mime`). 1–50 characters with at least one letter or digit, else **400** "Invalid search" (like pagination errors, no `field`). A blank value means no search | Users type fragments (`char`); the slug form is how PokeAPI names look | Prefix only (misses `chu`); fuzzy matching (overkill) |
| Q2 | Where does catalog search happen? | A new port method returns the **cached species index** (`List<CatalogEntry(id, name)>`); the **interactor** filters, counts and pages it, then runs the **same fan-out** as today for that page only | Matching is application logic, unit-testable with the in-memory fake; only one page is ever fanned out | Filtering in the gateway (hides the rule in an adapter); fetching every profile (2050 calls) |
| Q3 | One listing path or two? | **One:** the unfiltered listing also pages the index (`listEntries(offset, limit)` goes away) | One code path; one cached call serves every page and every search. The response contract doesn't change | Keep `listEntries` for the unfiltered case (two paths to keep consistent) |
| Q4 | What does the local search match? | `q` matches the **name or any localized name** (substring, case-insensitive; localized names are matched as typed, lowercased, without slug normalization, since `Mr. Mime` or `ピカチュウ` aren't slugs) | Shows the proprietary data at work without extra filters | Name only |
| Q5 | How is the local query built? | **One JPQL query** with an `EXISTS` subquery for localized names (no duplicate rows when a Pokémon has several names; paging and totals stay correct); same Pokédex order; batch fetching keeps the page free of N+1 (statement-count test) | One optional predicate doesn't need the Specifications machinery | Spring Data Specifications; a join (duplicates) |
| Q6 | Indexes for substring search? | **None now.** `LIKE '%x%'` can't use a b-tree; at our scale (hundreds of rows) a sequential scan is sub-millisecond. The README notes `pg_trgm` + GIN as the scaling path | No migration for a non-problem; honest about the trade-off | `pg_trgm` GIN indexes now (V3 migration, an extension) |
| Q7 | Repeated or invalid params | Single-valued (the existing interceptor already rejects `?name=a&name=b`, D22); too long or no letter/digit → **400** "Invalid search" | Same strictness as the rest of the API | — |
| Q8 | Frontend | One search box per list (debounced ~300 ms); the search **lives in the URL** (`/?name=char`), changing it resets to page 1, and it is part of the Query cache key; "No Pokémon match" with **Clear search**. In the Pokédex box, a number (`25`) **jumps straight to the detail page** | Linkable, survives back/forward; repeated searches come from the cache | Search in component state (lost on navigation) |

## 4. API
Unchanged without the new parameter.

| Endpoint | New parameter | Notes |
|---|---|---|
| `GET /api/v1/pokemon` | `name` | Public, like the listing. `totalItems` counts the matches |
| `GET /api/v1/local-pokemon` | `q` | Signed in, like the listing |

```
GET /api/v1/pokemon?name=char&page=0&size=20   → Charmander, Charmeleon, Charizard (totalItems 3)
GET /api/v1/pokemon?name=Mr.%20Mime             → Mr. Mime (normalized to mr-mime)
GET /api/v1/pokemon?name=zzz                    → 200, empty page (totalItems 0, totalPages 0)
GET /api/v1/pokemon?name=<51 characters>        → 400 { "title": "Invalid search" }
GET /api/v1/local-pokemon?q=ピカチュウ            → Pikachu
```

## 5. Implementation map (inside out)
| Layer | Classes |
|---|---|
| domain | `NameQuery` (normalization, length rule, `matches(name)`), its exception → 400 with `field` |
| application | `ListPokemonQuery` gains `Optional<NameQuery>`; `PokemonCatalogGateway.listAllEntries()` → `List<CatalogEntry>` (replacing `listEntries`); `ListPokemonInteractor` filters + pages; `ListLocalPokemonQuery` gains `Optional<String>` search; `LocalPokemonGateway.findAll(search, page)` |
| adapter.out.pokeapi | the index call (one cached entry, `pokeapi-species-index`), a trimmed real fixture for `/pokemon-species?limit=…` |
| adapter.out.persistence | the JPQL search query + count in `PokemonJpaRepository` |
| adapter.in.web | `name` / `q` `@RequestParam`s; the 400 |
| frontend | `SearchBox`, a URL-synced search hook, query keys with the search, empty states; `fakeApi.ts` learns both params |

## 6. Tests
- **Domain:** normalization (`Mr. Mime`, spaces, case), length limits, matching.
- **Interactor (fake catalog):** substring and case; paging over matches (totals, last page, past the end); only the page's matches are fanned out (hit counts); unfiltered listing unchanged; upstream failure → 503.
- **Gateway (fake PokeAPI server):** index mapping from a real fixture, cached (second call = 0 upstream hits).
- **JPA (Testcontainers):** name and localized-name matches, case, no duplicate rows with several localized names, totals, Pokédex order, **statement count** unchanged.
- **Web slices:** binding, the 400 with `field`, repeated params, the unchanged unfiltered contract.
- **End to end + Newman:** search cases in the US01 and US03 collections against the live PokeAPI.
- **Frontend:** typing filters the list and updates the URL; a number jumps to the detail page; the search survives back; clear search; empty state; localized-name search in the local store.
- **Mutation checks:** case-sensitive match, paging before filtering, a join instead of `EXISTS` (duplicates), skipping normalization.

## 7. Out of scope
Filters by type, tag, region, origin, generation, ability or stats (v1 of this plan; dropped by the user for
simplicity, each can be added later); sorting; fuzzy search; trigram indexes (Q6).
