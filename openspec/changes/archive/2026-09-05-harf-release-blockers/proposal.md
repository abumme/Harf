## Why

The release-readiness audit ([`docs/release-readiness-audit.md`](../../../docs/release-readiness-audit.md), commit `a8b62d5`) found the app not shippable: the Android release build crashes on start, the full iOS app does not link, and the client↔server binding has correctness bugs in sessions, sync, and account deletion. Several of these are **implementation gaps against behavior that `account-auth`, `stats-sync`, and `round-persistence` already specify**, plus a few behaviors no spec covers yet (Apple audience/nonce, durable pending uploads, purchase-key safety, per-environment API base URL).

This change fixes the blockers that can be built and verified **without any external secrets or store/OAuth-console setup** — audit stages 1–4 plus the local (scaffold + server-side) portion of stage 5. Store signing, hosting/deploy, real OAuth credentials, and word-pack content are out of scope and tracked elsewhere (`harf-play-release`, stage 6+).

## What Changes

- **Purchases can't crash a release build** (B1, B2): the build **rejects Test Store / placeholder RevenueCat keys in a release build**, and a misconfigured/blank key degrades to *purchases Unavailable* instead of throwing during Koin/`Purchases.configure`. Restore the iOS native RevenueCat linking so the full iOS app builds. **BREAKING** for release builds that currently carry Test Store keys — they will fail the build until real public SDK keys (or an intentional blank) are supplied.
- **Per-environment API base URL** (B4): the client resolves its backend base URL from configuration (dev/staging/prod) instead of the hardcoded `http://localhost:8080`. No production hosting is provisioned here — only the configuration seam and Android-emulator/dev mapping.
- **Sessions survive a real 401** (B5): the server returns a decodable JSON challenge on unauthorized, and the client detects a bare/empty `401` *before* JSON-decoding, so automatic refresh actually runs on normal access-token expiry; concurrent refreshes are serialized and reused across protected calls (link/delete/logout).
- **Account deletion is honest** (B6): the client keeps its session and local data until the server confirms deletion, surfaces a retryable error on failure, and only then clears local state (rounds included). Apple token revocation is contracted (implemented when the Apple key exists).
- **Offline results reach the server** (B7): completed rounds queue a durable pending upload that retries on connectivity/foreground, so a result recorded offline syncs without playing another round.
- **Merge-link respects server-wins and protects linked accounts** (B8): the client adopts the server snapshot after a merge-link before any upload; the server discards an account on identity collision **only when that account is actually anonymous**, never an already-linked one.
- **Apple token verification checks audience and nonce** (B9): the server requires `aud` against an App/Services-ID allowlist and binds the request to a verified nonce; a token minted for another app's audience is rejected. Negative tests included.
- **Server refuses unsafe production config** (B10): startup fails fast if `JWT_SECRET` is absent outside an explicit dev mode; the grace-window replacement refresh token is protected and cleared by TTL; internal error details are not returned to clients.
- **Native sign-in and logout are actionable** (B3): the `NoOpOAuthClient` stubs are replaced with real platform `OAuthClient` scaffolding that returns a real ID token or a typed cancel/error (public client IDs left blank, filled when consoles are configured), and Settings exposes a logout action for the existing endpoint.
- **Finished daily round reopens as a result, not a blank board** (B11): fix the `round-persistence` implementation to honor its already-specified "Finished round is not re-entered" behavior. Spec unchanged; implementation + regression test only.

Non-goals (tracked elsewhere / need external data): store signing, `versionCode`, R8, CI artifacts; production hosting, DB, domain, migrations, backup; real Google/Apple credentials and consent consoles; RevenueCat store products/prices/entitlement mapping; word-pack content and native review; full UI localization and small-screen/large-font polish.

## Capabilities

### New Capabilities
- `api-configuration`: the client resolves its backend API base URL from a per-environment configuration (dev/staging/prod) rather than a hardcoded host.

### Modified Capabilities
- `account-auth`: add Apple audience/nonce validation; resilient account deletion (failure preserves session + local data, retryable error, Apple revoke contract); merge-link discards an account only when it is actually anonymous; secure production session config (mandatory `JWT_SECRET`, protected + TTL-cleared grace token, no leaked internal error detail); automatic client refresh on unauthorized responses with a decodable server challenge; actionable native sign-in (real token or typed cancel/error) and a logout action in the client UI.
- `stats-sync`: add durable pending stats upload that retries on connectivity/foreground so an offline result syncs without another round.
- `purchases`: add that release builds reject non-production (Test Store/placeholder) keys and that purchase initialization never crashes the app — a misconfigured key degrades to Unavailable.

## Impact

- Client: `sharedUI/.../data/network/KtorServices.kt` (401 handling, refresh serialization, delete result handling, base URL injection), `SyncManager.kt` (pending upload queue, retry, merge-link adoption order), `data/auth/OAuthClient.kt` + `di/PlatformModule.*.kt` (real scaffolds), `feature/game/GameScreen.kt` (finished-round persistence), Settings UI (logout action, delete error surface), `di/Koin.kt` (base URL config), `sharedUI/build.gradle.kts` (release key guard), iOS Xcode/`purchases-kmp` native linking.
- Server: `backend/Application.kt` (JSON 401 challenge), `JwtService.kt` (mandatory secret), `AuthServerService.kt` (anonymous-only merge discard, grace token protection/TTL, Apple revoke hook), `AppleOAuthVerifier.kt` (audience allowlist + nonce), `StatusPages` (no internal detail leak). New env/config: client API base URL contract, `APPLE_AUDIENCES`, nonce flow, dev-mode flag.
- Contracts (`sharedData`): possible auth DTO extension for nonce; error envelope consistency on 401.
- Tests: real-Ktor client tests for expired access / concurrent refresh / logout / delete success+failure; Apple wrong-audience and nonce negative tests; pending-upload retry; finished-round regression.
