## Context

See `proposal.md` - Why / What Changes for motivation and scope. Today the client (`:sharedUI`) is fully offline (KSafe local cache), with no server, no accounts, and no notion of a user identity. This design covers the module layout, auth/token model, sync protocol, and deployment shape needed to introduce `:backend` and `:sharedData` without disrupting existing offline gameplay.

## Goals / Non-Goals

**Goals:**
- Define the `:sharedData` / `:backend` / `:sharedUI` boundary precisely enough to start implementation.
- Define the anonymous-account-first auth flow, OAuth linking, and the JWT/refresh-token model.
- Define the stats/streak sync data model and the last-write-wins resolution algorithm.
- Define the local/dev/deploy database and runtime story (Postgres in Docker everywhere, generic VPS in prod).

**Non-Goals:**
- Leaderboards, social/friends features, or server-authoritative daily puzzle delivery (explicitly deferred past this MVP).
- Field-level/CRDT-style merge of divergent stats (last-write-wins only, v1).
- Apple Sign-In outside iOS (Android has no native Apple SDK — only a web flow requiring server-side authorization-code exchange with an ES256 client-secret JWT; Desktop has no supported flow at all). Apple is iOS-only in v1.
- Web (JS/Wasm) clients: browser targets stay fully offline — no accounts, no sync, no CORS on the backend. `:sharedData` still compiles for JS/Wasm.
- Recovery of an anonymous account after token loss (reinstall/storage wipe): the token pair is the account's only credential; loss means a fresh anonymous account. Accepted v1 trade-off — linking an OAuth identity is the durability story.
- Production infra automation (Terraform/K8s) - the Docker Compose + VPS story is intentionally basic for v1.

## Decisions

### 1. Module boundary: `:sharedData` vs `:backend` vs `:sharedUI`
`:sharedData` is a pure KMP library: `@Serializable` DTOs, API route path constants, `ApiResult<T>`/error envelope types, and **service interfaces** (e.g. `AuthService`, `SyncService`) with no implementation. `:backend` implements those interfaces server-side (Ktor routes + Exposed repositories) and owns all server-only models (DB entities, JWT signing keys, provider client secrets). `:sharedUI` implements the same interfaces client-side via a Ktor `HttpClient` adapter, and owns all client-only concerns (token storage via KSafe, platform sign-in SDK glue).
- Alternative considered: put the Ktor client itself inside `:sharedData` as a ready-made typed client. Rejected for v1 to keep `:sharedData` free of Ktor-client as a hard dependency and avoid coupling client HTTP concerns (interceptors, auth header injection, retry) to the shared contract module; `:sharedUI` implements the interface using its own Ktor client instead.

### 2. Anonymous-first identity model
On first launch (Android/iOS/Desktop), the client calls `POST /api/v1/auth/anonymous` with no credentials; the backend creates a `users` row and returns an access+refresh token pair immediately. The client stores this token pair via KSafe and is "signed in" without any user action. Account creation is opportunistic and non-blocking: if the first launch is offline, gameplay proceeds as today and the client retries creation on a later launch/foreground with connectivity. The token pair is the anonymous account's only credential — there is no recovery mechanism (no device secret); losing the tokens means a fresh anonymous account (see Non-Goals).
- Linking: `POST /api/v1/auth/link` (authenticated as the anonymous user) with `{provider, idToken}` verifies the provider token and either (a) attaches the OAuth identity to the existing anonymous user, or (b) if that OAuth identity already belongs to a different existing user, merges by re-pointing the current device's session to the pre-existing account (existing account wins; anonymous data on this device is discarded after merge - acceptable for v1 given last-write-wins semantics already apply to stats). The orphaned anonymous `users` row and its dependent rows are deleted as part of the merge.
- **Post-link ordering (required):** after a link response that switched the session to a pre-existing account, the client MUST first `GET /api/v1/sync/stats` and adopt the server snapshot before performing any stats upload. Otherwise upload-on-change with a newer local `updatedAt` would clobber the existing account's stats under last-write-wins — the opposite of "existing account wins."
- Alternative considered: require sign-in before any play. Rejected - breaks the "install and play immediately" experience that's standard for casual word games.

### 3. Token model: access + rotating refresh tokens
- Access token: short-lived JWT (e.g. 15 min), signed HS256/RS256 by backend, contains `sub` (user id), `exp`, `iat`. Stateless verification, no DB lookup needed per request.
- Refresh token: opaque random string (not a JWT), stored **hashed** in a new `refresh_tokens` table (`id`, `user_id`, `token_hash`, `created_at`, `expires_at`, `revoked_at`, `replaced_by`, `rotated_at`). `POST /api/v1/auth/refresh` validates + rotates: issues a new refresh token, marks the old one `replaced_by = new.id` with `rotated_at = now()`.
- **Rotation grace window:** a replaced token presented again within a short window (~60s of `rotated_at`) is treated as a network-level retry, not theft: the server responds with the already-issued replacement pair (no additional rotation). A replaced/revoked token presented outside the window is reuse → theft detection: revoke the entire chain for that account, requiring re-authentication. Without the grace window, an ordinary retry after a response lost in transit would nuke the chain — fatal for anonymous accounts, which have no way to re-authenticate.
- `POST /api/v1/auth/logout` (authenticated): revokes all of the account's refresh tokens ("sign out of all devices"). Access tokens remain valid until their short expiry — accepted.
- Alternative considered: stateless-only JWT refresh (no DB table). Rejected per explicit decision - rotation + server-side revocation is worth the extra table for account security, and it's needed anyway to support logout/"sign out of all devices."

### 4. OAuth verification
- Google: verify ID tokens server-side using Google's Java token verifier (`GoogleIdTokenVerifier`, part of `google-api-client`), checking `aud`, `iss`, `exp`, signature against Google's cached public keys. The verifier MUST accept the full list of Google OAuth client IDs as audiences (Android, iOS, Desktop clients all hit the same backend with different `aud` values). Works uniformly on the JVM backend regardless of client platform.
- Apple (iOS only): verify identity tokens server-side by fetching Apple's JWKS (`https://appleid.apple.com/auth/keys`) and validating the JWT signature/claims via a JOSE/JWT library (e.g. `nimbus-jose-jwt` or `jose4j`) rather than a third-party "sign in with Apple" wrapper library, since backend verification is just generic JWKS-based JWT validation and doesn't need a specialized library.
- Client-side acquisition of the provider token differs by platform: Android uses the native Google sign-in SDK (Google only); iOS uses native Google and Apple sign-in SDKs; JVM Desktop uses a loopback-HTTP-server + system-browser OAuth Authorization Code flow for Google only (an "installed app" OAuth client performing the code-for-token exchange locally). Apple is excluded outside iOS: Android would need Apple's web flow with a server-side code exchange signed by an ES256 client-secret JWT, and Desktop has no supported flow — both out of scope per decision.

### 5. Sync model: last-write-wins
- `UserStatsDto` (in `:sharedData`) carries a single `updatedAt: Instant` alongside stats/streak fields. `POST /api/v1/sync/stats` is a full snapshot upload: the backend compares incoming `updatedAt` to the stored row's `updatedAt` and overwrites only if incoming is newer (or if no row exists yet). `GET /api/v1/sync/stats` returns the current server snapshot so the client can adopt it (e.g. after linking on a second device, or app relaunch).
- No per-field version vectors in v1 - accepted trade-off: a device that plays offline for a long time and syncs late can clobber other devices' newer partial progress if clocks are skewed, but this is explicitly acceptable per the last-write-wins decision and keeps the schema/API simple.
- **Future-timestamp guard:** the server rejects an uploaded snapshot whose `updatedAt` is more than a few minutes ahead of server time (400-level error). Without this, one device with a badly wrong clock (e.g. year 2099) would win once and then block every legitimate write forever under last-write-wins.

### 6. Account deletion
`DELETE /api/v1/account` (authenticated): permanently deletes the account's `users` row and all dependent rows (`oauth_identities`, `refresh_tokens`, `user_stats`) in one transaction. Required for store compliance — Apple App Store guideline 5.1.1(v) and Google Play's account-deletion policy mandate an in-app deletion path when the app offers account creation. The client exposes a "Delete account" entry in settings (with confirmation), then clears local tokens and returns to a fresh anonymous state on next connectivity.

### 7. Database & environments
- Single database engine everywhere: PostgreSQL, run via Docker Compose (`docker-compose.yml` at repo root) for local dev, CI, and (later) prod - no H2/SQLite fallback, avoiding dialect-divergence bugs.
- Exposed (`exposed-core`, `exposed-jdbc`, `exposed-dao`) + HikariCP connection pool; schema managed via Exposed's `SchemaUtils` at startup for v1 (Flyway can be introduced later if migrations get complex enough to need history/rollback).

### 8. Deployment shape
- `:backend` ships as a single Docker image (Ktor + Netty), deployed to a generic VPS (or Cloud Run later, no code changes needed) alongside a Postgres container via Docker Compose. HTTPS/cert management (e.g. Caddy/Traefik reverse proxy) is deployment configuration, not addressed by code changes in this proposal's scope.

## Risks / Trade-offs

- [Anonymous-account merge on link collision discards local device data] → Acceptable for v1 given last-write-wins already accepts data loss on conflicting syncs; document this behavior clearly for the user (e.g. "signing in on this device will use your existing account's data").
- [Anonymous account is permanently lost if its tokens are lost (reinstall, storage wipe)] → Accepted for v1 (no device-secret/recovery mechanism); the rotation grace window prevents the *self-inflicted* variant (retry-triggered chain revocation), and OAuth linking is the durability path — surface it in UI.
- [Clock skew between devices could cause a stale write to win under last-write-wins] → Mitigated at the extreme by the future-timestamp guard; for ordinary skew, use server-assigned `updatedAt` on write acceptance in a later iteration if this proves problematic; out of scope for v1.
- [No Apple Sign-In outside iOS] → Explicitly accepted; Android/Desktop users can still use Google or stay anonymous.
- [Refresh token table adds a bit of operational surface (cleanup of expired/revoked rows)] → Add a periodic cleanup job or rely on `expires_at` filtering in queries; not a blocker for v1.
- [Exposed `SchemaUtils` auto-create instead of real migrations] → Fine for a pre-launch MVP with no production data yet; revisit with Flyway before any schema change touches live user data.

## Open Questions

- Exact access-token lifetime and refresh-token expiry window (e.g. 15 min / 30 days) - can be tuned during implementation without affecting the design or specs.
- Whether to rate-limit `/auth/anonymous` to deter abuse (fake account farming) - an operational hardening detail, not a spec-level concern for v1.
