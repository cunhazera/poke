# Plan: users and authentication (Spring Security)

> Status: **implemented 2026-10-15** (see WORK_TRACKER D34–D40). Branch `auth`, cut from `us02`. It follows the same patterns: the clean-architecture skill, TDD from the inside out, and ProblemDetail errors.

## 1. What the spec requires
| Spec text | Meaning for us |
|---|---|
| "relational storage with a primary entity and a **secondary collection for user management**" | `app_user` (already created by V2) is backed by real code: an entity, a repository and a gateway |
| "an **auxiliary API for user registration, authentication**" | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` |
| "**protected vs. public routes**" | Reads of the catalog stay public. Everything else needs a valid token, **secure by default**, so future endpoints (US03 sync, US04 updates) are protected without extra work. `GET /api/v1/auth/me` is the first protected route. |
| "**seeded data or mock credentials** for the demo" | Demo users are **pre-inserted** by a Flyway seed migration, active only in the `demo` profile, which compose enables (D33) |
| "proper error handling", "consistent return structures" | 401/403 are ProblemDetails too, like every other error |
| "thorough unit test coverage for every core component" | Value objects, interactors, gateway (Testcontainers), web slice, security rules end to end |
| Frontend later (React/Vue) | Stateless bearer tokens fit an SPA. CORS is added with the frontend. |

## 2. Design
**Mechanism: stateless JWT (HS256) using Spring Security's own resource server** (`spring-boot-starter-security-oauth2-resource-server`).
Login returns a signed token; requests send `Authorization: Bearer <token>`. There's no session and no CSRF surface, and no third-party JWT library:
the encoder and decoder are Spring's Nimbus integration.

| Endpoint | Access | Result |
|---|---|---|
| `POST /api/v1/auth/register` `{username, email, password}` | public | **201** `{id, username, email, role}` (no `Location`: there's no user resource URL yet) · 400 invalid · **409** username or email taken (case-insensitive) |
| `POST /api/v1/auth/login` `{login, password}` (login = username **or** email) | public | **200** `{accessToken, tokenType: "Bearer", expiresIn}` · **401** with one generic message for an unknown user or a wrong password |
| `GET /api/v1/auth/me` | **authenticated** | **200** the current user · 401 without a token or with an invalid/expired one |
| `GET /api/v1/pokemon/**`, `GET /actuator/health` | public | unchanged |
| anything else | **authenticated** | 401 |

**Validation rules (domain value objects):**
- **Username:** 3–50 of `[A-Za-z0-9_.-]`.
- **Email:** a sane format, at most 254 characters.
- **Password:** 8–72 **bytes** in UTF-8. BCrypt silently ignores anything past 72 bytes, and Spring Security 7 rejects such passwords outright, so the domain says it first, with a clear 400.
- **Storage:** values keep the casing the user typed, and uniqueness ignores case (D24).

**Defensive details:**
- **No user enumeration:** login answers 401 with the same message for an unknown user and a wrong password. It also runs BCrypt against a dummy hash for unknown users, so response time doesn't reveal whether the user exists.
- **Registration race:** the unique index is the guarantee (§6.4 of the US02 plan). The gateway translates the unique violation on `uq_app_user_username` / `uq_app_user_email` into `UsernameTakenException` / `EmailTakenException` → 409.
- **No role escalation:** registration always creates `USER`. `ADMIN` exists only through the seed.
- **Secret:** the JWT secret comes from `JWT_SECRET`, at least 32 bytes, checked at startup. Compose ships a demo value; there's no default in `application.properties`.
- **Passwords:** never logged or returned, and stored as BCrypt (strength 10).

**Clean Architecture placement (per the skill):**
| Layer | Classes |
|---|---|
| domain | `User`, `Role`, `Username`, `EmailAddress`, `RawPassword`; `InvalidUserDataException`, `UsernameTakenException`, `EmailTakenException`, `UserNotFoundException` |
| application | in: `RegisterUserUseCase` + `RegisterUserCommand`, `AuthenticateUserUseCase` + `AuthenticateUserCommand` → `AccessToken`, `GetCurrentUserUseCase` · out: `UserGateway`, `PasswordHasher`, `TokenIssuer` · usecase: three interactors · exception: `InvalidCredentialsException` |
| adapter.out.persistence | `UserJpaEntity`, `UserJpaRepository`, `JpaUserGateway` (`@Transactional`, translates the unique violations) |
| adapter.out.security | `BCryptPasswordHasher`, `JwtTokenIssuer` |
| adapter.in.web | `AuthController`, DTOs (`RegisterRequest`, `LoginRequest`, `UserResponse`, `TokenResponse`), `ProblemDetailSecurityHandler` (401, 403 and firewall 400) |
| config | `SecurityConfig` (filter chain, public routes, JWT encoder and decoder), `AuthConfig` (wiring), `JwtProperties` |

**Seed (pre-inserted users):** `src/main/resources/db/seed/R__demo_users.sql`, a Flyway *repeatable* migration with `ON CONFLICT DO NOTHING`.
It loads only when `spring.flyway.locations` includes `db/seed` (`application-demo.properties`; compose sets `SPRING_PROFILES_ACTIVE=demo`).
The hashes are generated with Spring's own `BCryptPasswordEncoder`, and a test proves each demo password matches its hash.

| Username | Email | Password | Role |
|---|---|---|---|
| `ash` | `ash@pallet.town` | `pikachu123` | USER |
| `oak` | `oak@pallet.town` | `professor123` | ADMIN |

## 3. Out of scope (for now)
Refresh tokens and logout (the token simply expires, 1 h by default); rate limiting and lockout on login; password reset; email verification;
CORS (comes with the frontend); per-role rules on endpoints that don't exist yet (US03/US04 will use `ADMIN`/`USER`).
