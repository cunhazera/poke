# Plan: Search and filters (catalog + local store)

> Status: **draft 2026-10-16**, for the user to judge. Branch `search`, cut from `frontend` (the feature spans both
> sides). Same patterns as before: the clean-architecture skill, TDD from the inside out, Testcontainers, ProblemDetail
> errors, Newman cases, and Vitest + MSW on the frontend.

## 1. Goal
The spec only requires paginated browsing (US01), but "any additional functionality is welcome", and 1025 Pokémon
without a search box is the first gap a reviewer notices. Two different problems:
- **The catalog (PokeAPI)** has **no search endpoint**: we must build search from data we can cache.
- **The local store (our Postgres)** can run real queries, and it is where search can use the **proprietary fields**
  (localized names, region, tags), the part PokeAPI can't do.

**As a** user, **I want to** find Pokémon by name and type in the Pokédex, and by name, localized name, type, tag,
region and origin in the local store, **so that** I don't page through hundreds of entries.

## 2. Facts checked (live PokeAPI, 2026-10-16)
- `GET /pokemon-species?limit=2000` returns **all 1025 species** (`name` + `url` with the number) in one call:
  **76 KB, about 0.2 s**. Cached for 24 h, it is a complete, cheap name index.
- `GET /type/fire` lists the type's Pokémon (`pokemon[].pokemon.{name,url}`): **109 entries, of which 81 are species**;
  the rest are alternate forms (ids ≥ 10001, e.g. `scovillain-mega`), which the catalog already excludes (D14).
  The payload is large (moves, damage relations), but there are only 18 types and they are cacheable.
- Local store indexes today: `lower(name)` unique, `pokemon_tag(tag)`.

## 3. Questions (each with a recommendation)
| # | Question | Recommendation | Why | Alternatives |
|---|---|---|---|---|
| Q1 | How does a catalog name search match? | **Case-insensitive substring** on the slug, after normalizing the input: trim, lowercase, spaces and dots → `-` (`"Mr. Mime"` → `mr-mime`). 1–50 characters, else **400** | Users type fragments (`char`); the slug form is what PokeAPI names look like | Prefix only (misses `-chu` searches); fuzzy matching (overkill) |
| Q2 | Where does catalog filtering happen? | A new port method returns the **cached species index** (`List<CatalogEntry(id, name)>`); the **interactor** filters, counts and pages it, then runs the **same fan-out** as today for the 20 matches | Filtering is application logic, unit-testable with the in-memory fake; only one page is ever fanned out | Filtering in the gateway (hides the rule in an adapter); fetching every profile (2050 calls) |
| Q3 | One listing path or two? | **One:** the unfiltered listing also pages the index (the existing `listEntries(offset, limit)` goes away) | One code path, and the index is one cached call shared by every page and every search. The response contract doesn't change | Keep `listEntries` for the unfiltered case (two paths to test and keep consistent) |
| Q4 | Catalog type filter | `?type=fire`, one of the **18 canonical types** (reuses `PokemonType`), else **400**. Port method `findSpeciesOfType(type)` → the set of species numbers (cached); combined with the name filter by **AND** | One cached call per type; alternate forms are dropped like everywhere else | Filtering by fetched profiles (needs every profile) |
| Q5 | Local store filters | `q` (substring of the name **or any localized name**, case-insensitive), `type`, `tag` (exact slug), `region` (case-insensitive exact), `origin` (`synced` / `local`); all optional, combined by **AND**; same Pokédex order and page envelope | Shows off the proprietary data (find Pikachu by `ピカチュウ`, everything tagged `starter`) | Only a name filter |
| Q6 | How is the local query built? | A domain/application `LocalPokemonFilter` record → the JPA gateway turns it into **Spring Data JPA Specifications** (Criteria, `EXISTS` subqueries for types, tags and localized names, so no duplicate rows and paging stays correct) | Composable optional predicates with no string-built SQL; batch fetching keeps the page free of N+1 (statement-count test) | One JPQL with `(:x is null or …)` per filter (grows unreadable); native SQL |
| Q7 | Indexes for substring search? | **None now.** `lower(name) LIKE '%x%'` can't use a b-tree; at our scale (hundreds of rows) a sequential scan is sub-millisecond. Note `pg_trgm` + GIN as the scaling path in the README | No migration for a non-problem; honest about the trade-off | `pg_trgm` GIN indexes now (V3 migration, an extension) |
| Q8 | Repeated or invalid filter params | Single-valued (the existing interceptor already rejects `?tag=a&tag=b`, D22); invalid values → **400 naming the param** (`type`, `origin`, `q` too long, `tag` not a slug) | Same strictness as the rest of the API | Multi-valued tags (any vs. all semantics to define) |
| Q9 | Frontend | A search box (debounced ~300 ms) and filter controls above each list; **filters live in the URL** (`/?name=char&type=fire`), changing one resets to page 1, and they are part of the Query cache key; "No matches" with **Clear filters**. In the Pokédex box, a number (`25`) **jumps straight to the detail page** | Linkable, survives back/forward; the cache serves repeated searches instantly | Filters in component state (lost on navigation) |

## 4. API
Unchanged without filters. New optional query parameters:

| Endpoint | New parameters | Notes |
|---|---|---|
| `GET /api/v1/pokemon` | `name`, `type` | Public, like the listing. `totalItems` counts the matches |
| `GET /api/v1/local-pokemon` | `q`, `type`, `tag`, `region`, `origin` | Signed in, like the listing |

```
GET /api/v1/pokemon?name=char&page=0&size=20        → Charmander, Charmeleon, Charizard (totalItems 3)
GET /api/v1/pokemon?type=fire&name=saur             → 200, empty page (totalItems 0, totalPages 0)
GET /api/v1/pokemon?type=fyre                       → 400 { "field": "type", "detail": "Unknown type 'fyre'" }
GET /api/v1/local-pokemon?q=ピカチュウ                 → Pikachu
GET /api/v1/local-pokemon?tag=starter&origin=synced → the synced starters
```

## 5. Implementation map (inside out)
| Layer | Classes |
|---|---|
| domain | `NameQuery` (normalization + length rule), `LocalPokemonFilter` (q, type, tag, region, origin; validated), `PokemonOrigin` enum; reuse `PokemonType`, `PageRequest`, `Page` |
| application | `ListPokemonQuery` gains `Optional<NameQuery>`, `Optional<PokemonType>`; `PokemonCatalogGateway`: `listAllEntries()` → `List<CatalogEntry>`, `findSpeciesOfType(PokemonType)` → `Set<Integer>` (replacing `listEntries`); `ListPokemonInteractor` filters + pages; `ListLocalPokemonQuery` gains a `LocalPokemonFilter`; `LocalPokemonGateway.findAll(filter, page)` |
| adapter.out.pokeapi | index and type calls, two new caches (`pokeapi-species-index`, `pokeapi-type`), forms (ids > count) dropped in the mapper; trimmed real fixtures for `/pokemon-species?limit=…` and `/type/{name}` |
| adapter.out.persistence | `LocalPokemonSpecifications` (filter → `Specification`), `PokemonJpaRepository` extends `JpaSpecificationExecutor` |
| adapter.in.web | new `@RequestParam`s on both controllers; invalid filter → 400 with `field` |
| frontend | `SearchBar` / filter components, URL-synced filter hook, query keys with filters, empty states; `fakeApi.ts` learns the filters |

## 6. Tests
- **Domain:** name normalization and limits; filter validation (each invalid param).
- **Interactor (fake catalog):** substring and case; AND with type; paging over matches (totals, last page, past the end); only the page's matches are fanned out (hit counts); forms never appear; upstream failure → 503.
- **Gateway (fake PokeAPI server):** index and type mapping from real fixtures, forms dropped, both cached.
- **JPA (Testcontainers):** each filter alone and combined, localized-name match, no duplicate rows when a Pokémon has several tags or names, totals correct, Pokédex order kept, **statement count** unchanged (no N+1).
- **Web slices:** parameter binding, the 400s with `field`, repeated params, the unchanged unfiltered contract.
- **End to end + Newman:** search cases in the US01 and US03 collections against the live PokeAPI.
- **Frontend:** typing filters the list and updates the URL; a number jumps to the detail page; filters survive reload/back; clear filters; empty state; local filters (localized name, tag, origin).
- **Mutation checks:** drop the type intersection, make the match case-sensitive, page before filtering, join instead of `EXISTS` (duplicates).

## 7. Out of scope
Generation, ability or stat filters (each needs more PokeAPI fan-out for little value); sorting options; fuzzy or
typo-tolerant search; trigram indexes (Q7).
