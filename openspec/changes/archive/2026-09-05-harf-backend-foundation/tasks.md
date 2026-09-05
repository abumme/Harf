Tasks are grouped into review-sized batches: each group is intended to land as one PR reviewable in ≤10-15 minutes. Groups are ordered by dependency; the subtlest logic (refresh rotation, link/merge) is isolated into its own group so the reviewer can focus.

## 1. Module Scaffolding (build files only — mechanical review)

- [x] 1.1 Create `:sharedData` KMP module (commonMain, JVM/Android/iOS/JS/Wasm targets) and add it to `settings.gradle.kts`; verify `./gradlew :sharedData:compileKotlinJvm` succeeds
- [x] 1.2 Create `:backend` JVM module (Ktor server app) and add it to `settings.gradle.kts`; verify `./gradlew :backend:build` succeeds with a minimal "hello world" route
- [x] 1.3 Add Ktor server, Exposed, PostgreSQL, HikariCP, JWT, Google token verifier, JOSE/JWKS library, and kotlinx-datetime (+ its kotlinx-serialization support for `Instant`) versions+entries to `gradle/libs.versions.toml`; verify the version catalog resolves via `./gradlew :backend:dependencies`
- [x] 1.4 Add `docker-compose.yml` at repo root defining a PostgreSQL service (and optionally the backend service); verify `docker compose up -d db` starts a reachable Postgres instance
- [x] 1.5 Add `:sharedUI` dependency on `:sharedData`; verify `./gradlew :sharedUI:compileKotlinJvm` still succeeds

## 2. Shared Contracts (`:sharedData` — API surface review)

- [x] 2.1 Define `@Serializable` DTOs for auth (`AnonymousAuthResponse`, `LinkAccountRequest`, `RefreshRequest`, `TokenPairDto`, `OAuthProvider` enum) and verify round-trip JSON encode/decode in a unit test
- [x] 2.2 Define `@Serializable` DTOs for sync (`UserStatsDto` with `updatedAt: Instant`) and a generic `ApiResult<T>`/error envelope type; verify unit tests for serialization
- [x] 2.3 Define API route path constants shared by client and server (e.g. `/api/v1/auth/anonymous`, `/api/v1/auth/link`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, `/api/v1/account`, `/api/v1/sync/stats`)
- [x] 2.4 Define `AuthService` and `SyncService` interfaces (no implementation) describing the operations from the `account-auth` and `stats-sync` specs

## 3. Backend Database Layer (schema review)

- [x] 3.1 Define Exposed tables for `users` (id, created_at — provider identity lives ONLY in `oauth_identities`; an account with no identity row is anonymous) and `oauth_identities` (user_id, provider, provider_subject, unique per provider+subject); verify schema creation against local Postgres
- [x] 3.2 Define Exposed table for `refresh_tokens` (id, user_id, token_hash, created_at, expires_at, revoked_at, replaced_by, rotated_at); verify schema creation against local Postgres
- [x] 3.3 Define Exposed table for `user_stats` (user_id, stats fields, updated_at); verify schema creation against local Postgres
- [x] 3.4 Wire HikariCP + Exposed database connection from environment-configured Postgres URL (with docker-compose defaults); verify `:backend` connects successfully on startup

## 4. Anonymous Auth + JWT Plumbing (`:backend`)

- [x] 4.1 Implement `POST /api/v1/auth/anonymous`: create a `users` row and issue access+refresh tokens; verify with an integration test matching the "First launch creates an anonymous account" scenario
- [x] 4.2 Implement JWT access token issuance/verification (signing key from config, `sub`/`exp`/`iat` claims) and a Ktor `Authentication` plugin enforcing it; verify with a test that an expired/invalid token is rejected

## 5. Refresh Rotation + Grace Window (`:backend` — subtlest logic, review alone)

- [x] 5.1 Implement refresh token issuance (hashed storage), `POST /api/v1/auth/refresh` rotation with the ~60s retry grace window (re-presenting a just-rotated token returns the already-issued pair), and outside-window reuse-detection chain revocation; verify with tests matching the "Refresh exchanges...", "Retry within the grace window...", and "Reused refresh token outside the grace window..." scenarios

## 6. Provider Token Verification (`:backend`)

- [x] 6.1 Integrate Google ID token verification (`GoogleIdTokenVerifier`) for the `google` provider, accepting the full list of client IDs (Android/iOS/Desktop) as valid audiences; verify with a test using a mocked/stubbed verifier for both valid and invalid tokens
- [x] 6.2 Integrate Apple identity token verification via JWKS fetch + JOSE/JWT validation for the `apple` provider (tokens originate from iOS clients only); verify with a test using a mocked JWKS response for both valid and invalid tokens

## 7. Account Link & Merge (`:backend` — subtle merge semantics, review alone)

- [x] 7.1 Implement `POST /api/v1/auth/link`: verify provider token, attach-or-merge account per design (merge deletes the orphaned anonymous account and its data); verify with tests matching "Successful link..." and "Link to an identity already owned by another account" scenarios

## 8. Logout + Account Deletion (`:backend`)

- [x] 8.1 Implement `POST /api/v1/auth/logout` revoking all of the account's refresh tokens; verify with a test matching the "Logout revokes all refresh tokens" scenario
- [x] 8.2 Implement `DELETE /api/v1/account` deleting the account and all dependent rows in one transaction; verify with a test matching the "Deleting an account removes all server-side data" scenario

## 9. Stats Sync Endpoints (`:backend`)

- [x] 9.1 Implement `POST /api/v1/sync/stats` with last-write-wins comparison against stored `updated_at` and rejection of far-future timestamps; verify with tests matching "Newer snapshot overwrites...", "Older snapshot is rejected...", and "Snapshot with a far-future timestamp is rejected" scenarios
- [x] 9.2 Implement `GET /api/v1/sync/stats` returning the current snapshot or a default/empty one; verify with a test matching "Fetching stats on a new or second device"
- [x] 9.3 Enforce authentication + account-scoping on both sync endpoints; verify with a test matching "Unauthenticated sync request is rejected"

## 10. Client API Adapter + Anonymous Bootstrap (`:sharedUI`)

- [x] 10.1 Implement a Ktor-client-based `AuthService`/`SyncService` adapter in `:sharedUI` consuming the `:sharedData` interfaces and route constants; verify with jvmTest unit tests using Ktor `MockEngine` (live-backend check stays manual)
- [x] 10.2 Persist access/refresh tokens via KSafe and wire automatic anonymous account creation as a non-blocking background step (offline first launch plays as today, creation retried on later launch/foreground); verify manually that a fresh install obtains a session without user action and that airplane-mode first launch still plays

## 11. Platform Sign-In Flows (`:sharedUI` — per-platform glue)

- [x] 11.1 Implement native Google sign-in glue on Android/iOS, native Apple sign-in on iOS only, and JVM Desktop loopback-browser Google sign-in flow (Apple option hidden on Android and Desktop); verify manually on each available target

## 12. Client Sync Wiring + Settings UI (`:sharedUI`)

- [x] 12.1 Wire stats/streak upload-on-change and download-on-launch/link using the sync service, enforcing the post-link ordering (after a merge-link, download and adopt the server snapshot before any upload); verify manually that stats appear on a second linked device
- [x] 12.2 Add settings UI entries for logout and account deletion (deletion with confirmation; on success clear tokens and return to fresh-install state); verify manually on one target

## 13. Documentation & Final Pass (docs-only review)

- [x] 13.1 Update `CLAUDE.md` to describe the `:sharedData` / `:backend` / `:sharedUI` module boundaries, replacing the current "everything in `:sharedUI`" rule
- [x] 13.2 Add a `README` section (or `docs/`) covering local dev setup: `docker compose up`, running `:backend`, and pointing `:sharedUI` at it
- [x] 13.3 Run the full backend test suite (`./gradlew :backend:test`) and confirm all scenarios from `account-auth` and `stats-sync` specs are covered and passing
