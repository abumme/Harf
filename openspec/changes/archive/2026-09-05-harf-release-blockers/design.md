## Context

See `proposal.md - Why`. Constraints that shape the approach:

- The audit fixes span client (`sharedUI`), server (`backend`), shared contracts (`sharedData`), and build config — but must ship as **small independent commits**, not one patch (per `docs/release-fix-plan.md`).
- `account-auth` / `stats-sync` specs exist only in the completed-but-unarchived `harf-backend-foundation` change; `round-persistence` is already in main specs. Several blockers are **implementation gaps against these existing specs**, so the fix is code + tests, not new behavior.
- No external secrets are available in this change: real RevenueCat/Google/Apple keys, hosting, and store consoles are out. Everything here must be buildable and testable locally.
- Current concrete defects (audit §3): `KtorServices.kt` decodes the error body before checking status; delete clears session regardless of result; `SyncManager.bootstrap()` only pulls; `linkAccount()` merges instead of adopting server data; `AppleOAuthVerifier` ignores `aud`; `JwtService` silently uses a dev secret; `NoOpOAuthClient` returns null; `GameScreen.kt` deletes the finished round.

## Goals / Non-Goals

**Goals:**
- Make the release build safe (no crash from purchase misconfig, no Test Store key leak, iOS links).
- Make the client↔server session lifecycle correct against a **real** Ktor server, not just MockEngine.
- Introduce the seams (API base URL config, OAuth client scaffold, Apple audience/nonce contract, Apple revoke hook) so wiring real providers/hosting later needs credentials, not redesign.

**Non-Goals (design-level):**
- No production hosting, migration tooling, rate limiting, or backup design — that is stage 6 (`harf-play-release` + infra).
- No word-pack content, full localization, or small-screen layout rework — polish, out of the blocker set.
- No RevenueCat 3.x migration decision beyond what is needed to make the current `2.2.2+17.10.0` iOS target link; if linking cannot be fixed on 2.x, that is an Open Question, not a silent upgrade.

## Decisions

**1. 401 handling: check status before decoding.** The client's `executeWithAuthRetry` will branch on HTTP status first — a `401`/`403` routes straight to the refresh path, never `body<ApiErrorResponse>()`. The server adds a JSON challenge (`Application.kt` auth `challenge { }`) so a decodable body also exists, but the client no longer depends on it. *Alternative rejected:* only fixing the server challenge — leaves the client brittle to any bodyless 401 (proxies, gateways) and API-37 network stacks.

**2. Refresh serialization via a single-flight primitive.** Concurrent protected calls hitting an expired token await one shared refresh (Mutex + cached in-flight `Deferred`), so the refresh token rotates once. *Alternative rejected:* per-call refresh — races rotate the token repeatedly and trip reuse-detection revocation (audit's grace window exists precisely because of this).

**3. Delete is result-gated on the client.** `KtorSyncService.delete` returns the real result; `SyncManager.deleteAccount()` clears session/stats/rounds **only** on confirmed success, else returns a retryable error. *Alternative rejected:* optimistic local clear — the audit showed it hides server failures and orphans data.

**4. Pending upload = one durable marker in KSafe, not a full outbox.** Stats sync is last-write-wins on a whole snapshot, so a boolean/`updatedAt` "dirty" marker is enough: on reconnect/foreground, if dirty, upload the current snapshot. No per-event queue. *Alternative rejected:* an event log — needless; the snapshot already carries all state and the server reconciles by timestamp. `ponytail:` single dirty flag; revisit only if sync becomes per-event.

**5. Merge-link guard lives on the server.** `AuthServerService` deletes the caller account on identity collision only when it is purely anonymous (no linked identity); otherwise reject. Client-side, `linkAccount()` calls `ResultLog.replace()` (adopt server snapshot) instead of `merge()` after the session switches, before any upload. Both sides needed: server protects data integrity, client honors server-wins.

**6. Apple audience/nonce as explicit config + contract extension.** `AppleOAuthVerifier` gains a required `aud` allowlist (new `APPLE_AUDIENCES` env) and nonce verification; the auth DTO carries a nonce the server bound to the request. Env-only would not work — the verifier has no audience parameter today (audit §200). *Alternative rejected:* reusing a Services ID as audience for native iOS — wrong per Apple docs; native uses the bundle ID.

**7. Fail-fast config.** `JwtService` requires `JWT_SECRET` unless an explicit dev flag is set; the client requires a base URL for release builds. Both surface at startup/build, never a silent localhost/dev-secret fallback.

**8. OAuth client scaffold with blank IDs.** Replace `NoOpOAuthClient` per-platform `actual` with real `OAuthClient` implementations (Credential Manager on Android, `AuthenticationServices`/Google Sign-In on iOS) that return a typed token/cancel/error. Public client IDs are read from config and left blank; a blank ID yields a typed "not configured" error, not a crash. This lets the flow, error handling, and logout UI be built and unit-tested now; real login is verified once consoles exist.

**9. Finished-round fix is implementation-only.** `round-persistence` already specifies "Finished round is not re-entered → finished result shown." `GameScreen.kt` will persist the finished round state and restore the day's result from `ResultLog` instead of deleting the save. No spec change; a regression test locks it.

## Risks / Trade-offs

- **iOS RevenueCat linking may not be fixable on 2.x** → try the native framework wiring first; if it requires the 3.x integration model, stop and raise it (Open Question) rather than bundling an SDK major-bump into a blocker fix.
- **Real-Ktor client tests need a live test server** → use the backend's existing isolated-Postgres test harness (audit ran 17/17 there); gate behind the same setup, never point at a real DB (`UsersTable.deleteAll()` is destructive).
- **Nonce contract touches `sharedData` DTOs** → additive optional field to stay backward-compatible with the unarchived backend-foundation contracts; verify server rejects a missing nonce only where a nonce flow is active.
- **Blank OAuth IDs could mask a real misconfig in release** → a release build SHALL fail fast if sign-in is enabled but its client ID is blank, mirroring the purchase-key guard.
- **Base URL config seam without hosting** → dev/staging/prod values are placeholders except dev; production value is filled at deploy. The guard (fail-fast on missing release URL) prevents shipping a localhost build meanwhile.

## Migration Plan

- Ship as ordered small commits matching the task groups: purchase-safety/iOS-link → base-URL config → 401/refresh → delete → pending-upload → merge-link guard → Apple audience/nonce → server config hardening → OAuth scaffold + logout UI → finished-round.
- Server changes (JSON challenge, mandatory secret, audience/nonce, grace TTL) are backward-compatible with existing clients except mandatory `JWT_SECRET`, which is a deploy-config step, and `APPLE_AUDIENCES`, required only once Apple link is enabled.
- Rollback: each commit is independent; the fail-fast guards are the only behavior that can block a deploy, and they block precisely the unsafe configs the audit flagged.

## Open Questions

- Can the current `purchases-kmp 2.2.2+17.10.0` iOS target be made to link with correct native framework wiring, or does it require the RevenueCat 3.x KMP integration model? Resolve during the iOS-link task; if 3.x is required, that is a separate change, not part of this blocker set.
- Final Android `applicationId` (`uz.abumme.harfgame` vs current `.androidApp`) is an owner decision (fix-plan §2) that gates OAuth client registration — but not the local scaffold, so it does not block this change's tasks.
