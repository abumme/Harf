## Why

Harf is currently fully offline (KSafe local cache only), so progress, streaks, and stats are trapped on a single device and are lost on reinstall. Introducing a backend with account-based cross-device sync lets players keep their streak/stats history across devices and reinstalls, which is the MVP driver for this change.

## What Changes

- Add a new `:backend` module (Ktor server) with Exposed ORM against PostgreSQL (Docker for all environments, including local dev).
- Add a new `:sharedData` KMP module holding `@Serializable` DTOs and service interfaces shared between `:sharedUI` and `:backend`; server-specific implementations/models live in `:backend`, client-specific implementations live in `:sharedUI`.
- Support anonymous accounts by default: every install gets a server-issued anonymous account/identity immediately, playable without sign-in. Account creation happens opportunistically in the background (retried later if offline) and never blocks gameplay. An anonymous account's only credential is its token pair: if the tokens are lost (reinstall, storage wipe), the account is unrecoverable — accepted v1 trade-off, users are encouraged to link an OAuth identity to make the account permanent.
- Support linking an anonymous account to Google or Apple OAuth (account "claim" flow), turning it into a permanent, cross-device account.
- Verify Google ID tokens (via Google's Java token verifier) and Apple identity tokens (via JWKS-based JWT verification) on the backend. Provider availability by platform: Google on Android/iOS (native SDK) and JVM Desktop (loopback-browser OAuth flow); **Apple on iOS only** — Android has no native Apple SDK (only a web flow requiring server-side code exchange, out of scope for v1) and Desktop has no supported flow at all.
- Issue Harf-specific access + refresh JWTs; add a `refresh_tokens` table (rotating, revocable, server-side tracked) rather than stateless-only refresh tokens. Rotation includes a short grace window so a network-level retry of a refresh request does not trigger reuse-detection revocation.
- Add `POST /api/v1/auth/logout` (revoke the account's refresh tokens / sign out of all devices) and `DELETE /api/v1/account` (full account + data deletion, required by Apple App Store guideline 5.1.1(v) and Google Play account-deletion policy) with a corresponding settings UI entry.
- Add stats/streak sync endpoints with **last-write-wins** conflict resolution: the most recently-synced snapshot (by client timestamp) overwrites server state, no field-level merge in v1.
- Deployment target: a generic VPS running Docker Compose (Postgres + backend container); Cloud Run/Fly.io remain acceptable alternatives later, no code impact for v1.
- **Web (JS/Wasm) clients are out of scope for v1**: the browser targets stay fully offline (no accounts, no sync, no CORS needed on the backend). `:sharedData` still compiles for JS/Wasm so `:sharedUI`'s common code keeps building.

## Capabilities

### New Capabilities
- `account-auth`: Anonymous account creation, Google/Apple OAuth linking, JWT issuance and verification, refresh token rotation/revocation, logout, and account deletion.
- `stats-sync`: Cross-device sync of player stats and streaks between client and backend using last-write-wins conflict resolution.

### Modified Capabilities
(none — existing gameplay/local-persistence capabilities such as `player-stats` and `streaks` remain unchanged; sync is an additive backend concern consumed by `:sharedUI`, not a change to their existing requirements)

## Impact

- New modules: `:backend` (Ktor/Exposed/Postgres/JWT), `:sharedData` (DTOs + service interfaces).
- `settings.gradle.kts` and `gradle/libs.versions.toml` gain new includes/dependencies (Ktor server, Exposed, PostgreSQL driver, HikariCP, JWT libs, Google token verifier, JOSE/JWKS library for Apple).
- `:sharedUI` gains a dependency on `:sharedData` and a typed Ktor client implementing the shared service interfaces, plus platform-specific OAuth sign-in flows (Android/iOS: native Google SDK, iOS additionally native Apple sign-in; Desktop: loopback browser flow for Google; Apple is iOS-only).
- New local dev workflow: `docker-compose.yml` running PostgreSQL for both local development and CI/tests (no H2/SQLite fallback).
- `CLAUDE.md` needs updating to reflect the new module boundaries (`:sharedData`, `:backend` vs. the current "everything lives in `:sharedUI`" rule).
