# Plan: Frontend (React)

> Status: **implemented 2026-10-16** on branch `frontend` (cut from `develop`), merged in PR #5. The framework
> (React, D54) and location (`frontend/`, D55) were decided with the user; the tooling below is the recommended set
> (WORK_TRACKER D56–D62), applied as recommended and open to the user's veto.

## 1. Goal
**Spec:** "demonstrate full-stack capability with a modern framework", evaluated on a **responsive, user-centric** design,
**CRUD operations** for the functional use cases, **clean component organization and efficient state management**, and
**no warnings in the browser console**. Demo credentials are already seeded (`ash`/`pikachu123` USER, `oak`/`professor123` ADMIN).

## 2. Questions (each with a recommendation)
| # | Question | Recommendation | Why | Alternatives |
|---|---|---|---|---|
| F1 | Build tool / language | **Vite + TypeScript** (strict) | The standard React toolchain; types mirror the API contract, so a contract drift is a compile error | CRA (dead); plain JS |
| F2 | Server state | **TanStack Query**: one query per resource, keyed by its params (`['pokemon', page]`), with `placeholderData: keepPreviousData` for pagination and cache invalidation after writes | The API is the source of truth; Query gives caching, dedup, loading/error states and invalidation without hand-written reducers. That *is* "efficient state management" | Redux Toolkit + RTK Query (more ceremony); `useEffect` + `useState` (re-fetches, races) |
| F3 | Client state | **One small session store** (token, user, expiry) outside React, persisted in `localStorage` and read with `useSyncExternalStore`; everything else is URL state (`?page=`) or component state | The session is the only truly global client state. Living outside React lets the API client read the token and sign out on a 401 without a provider. Page in the URL makes back/forward and sharing work | A React context (the fetch client couldn't reach it); Zustand/Redux (no need) |
| F4 | Routing | **React Router** (declarative routes), with a `RequireAuth` guard (signed in) and an admin check for write screens | De-facto standard | TanStack Router |
| F5 | Styling | **Plain CSS**: one global sheet (tokens as CSS variables, buttons, form controls) + **CSS Modules** per component; responsive grids | No extra dependency, scoped class names, responsive with a few media queries | Tailwind; a component library (heavier, hides the work) |
| F6 | Forms | **Controlled components** + the server's ProblemDetail (`field` / `errors`) shown next to the right input; light client checks only where they help the user (required, ranges) | The domain already owns the rules (D52); duplicating all of them in the UI would drift | React Hook Form + Zod (a second copy of the rules) |
| F7 | API access | A thin typed `fetch` client: adds the bearer token, parses ProblemDetail into an `ApiError`; a **401 signs the user out** | One place for auth and error handling | axios (not needed) |
| F8 | Same-origin vs. CORS | **Same origin**: the Vite dev server proxies `/api`, and in Docker **nginx** serves the SPA and proxies `/api` to the app | No CORS config in the backend, no preflights, and the backend stays untouched | Spring CORS config |
| F9 | Tests | **Vitest + React Testing Library + MSW** (a fake API at the network level, using the real JSON contract); tests drive the UI like a user (roles, labels) | Same philosophy as the backend: fakes over mocks; the real fetch client, Query and router run in every test | Mocking hooks (tests that can't fail); Cypress/Playwright only (slow) |
| F10 | Docker / compose / CI | `frontend/Dockerfile` (node build → nginx), a `web` service in compose on **:3000**; CI gets a frontend job (lint, typecheck, test, build) | `docker compose up --build` still runs the whole stack | Serving the SPA from Spring Boot (couples the builds) |

## 3. Screens (by story)
| Route | Who | Story | Content |
|---|---|---|---|
| `/` | anyone | US01 | Paginated Pokédex grid: sprite, name, number, category, weight, abilities (hidden ones marked); page in the URL |
| `/pokemon/:id` | anyone | US02 | Artwork, types, description, height/weight, abilities, base-stat bars with total, evolution tree (branches), previous/next; **ADMIN: "Sync to local store"** (US03) |
| `/local` | signed in | US03 | Paginated local store: synced vs. local-only badge, region, tags; **ADMIN: sync by Pokédex number, "New Pokémon"** |
| `/local/:id` | signed in | US03 | Catalog + proprietary data (localized names, region, habitat, coordinates, tags, synced at) ; **ADMIN: edit, delete (confirm), re-sync** |
| `/local/new`, `/local/:id/edit` | ADMIN | US04 | Create form (all fields) / edit form (proprietary only for a synced Pokémon, everything for a local one), `version` echoed, 409 → "changed by someone else, reload" |
| `/login`, `/register` | anyone | auth | Forms with server errors per field; after login go back to where the user came from |

States everywhere: loading skeletons, empty states, error panels with retry (503 "PokeAPI unavailable"), 404 pages.
A USER sees the local store read-only (the write buttons are hidden; the API still enforces 403).

## 4. Layout
```
frontend/
  src/
    api/          client.ts (fetch + ProblemDetail), types.ts (contract), pokemon.ts, localPokemon.ts, auth.ts (query/mutation hooks)
    auth/         session.ts (the session store), guards.tsx (RequireAuth, AdminOnly)
    components/   shared UI: Layout, Pagination, Pokemon (types, abilities, stats, evolution tree, image), Field, Status, SyncNotice
    pages/        one component per route (+ its test); localPokemonForm.ts maps form text to API payloads
    test/         fakeApi.ts (MSW fake backend), render.tsx (renders the whole app at a URL), setup.ts
  Dockerfile, nginx.conf.template   node build → nginx (SPA + /api proxy), non-root
```
