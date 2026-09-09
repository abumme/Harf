## Context

See proposal.md — Why. The app already has the pieces this builds on:
- Round finish funnels through `GameViewModel`'s `onFinish` callback producing a `ResultRecord`; `SyncManager.pushStats()` is already called there.
- `Streaks.streak(records, lang, today)` → `StreakStats(current, best)` and `Streaks.stats(records, lang)` → `PlayerStats` give the numbers, per language, from the result log (`ResultLog`).
- Android-only integrations already follow a pattern: an interface in `commonMain`, a `NoOp` default, a real Android impl wired in `PlatformModule.android.kt`; `CurrentActivityProvider` exposes the current `Activity` (used by `AndroidGoogleOAuthClient`). Billing (RevenueCat) uses the same blank-key ⇒ unavailable degrade.

## Goals / Non-Goals

- **Goals**: PGS v2 sign-in on Android; submit best-streak + total-wins leaderboards and unlock achievements on round finish; open native leaderboard/achievement UIs; full isolation from the app's account/sync; no-op elsewhere.
- **Non-Goals**: linking the PGS player to the backend account; PGS Saved Games / cloud saves (the backend stats-sync already owns durable state); iOS/desktop/web games features; a custom in-app leaderboard UI (use the native PGS screens).

## Decisions

- **`expect`/`actual` `GamesServices` interface, Android-real + no-op elsewhere.** Mirrors `OAuthClient`: `commonMain` declares the interface + a `NoOpGamesServices`; `androidMain` implements it with `PlayGamesSdk` + `GamesSignInClient`/`LeaderboardsClient`/`AchievementsClient`, resolving the `Activity` via `CurrentActivityProvider`. DI binds the real one only in `PlatformModule.android.kt`; others bind the no-op. *Alternative rejected*: `mobileMain` (android+ios shared) — PGS has no iOS SDK, so it belongs in `androidMain`, not `mobileMain`.

- **Isolation is structural, not just convention.** `GamesServices` depends on nothing from `data/auth` or `SyncManager`; it reads only the result log / `Streaks` output passed to it. It never calls the backend and never touches `SessionStore`. This is the load-bearing safety property (answers "won't it break auth?": no).

- **IDs come from Android string resources, blank ⇒ no-op.** Play Console generates `games-ids.xml` (app id + per-leaderboard/achievement ids). The Android impl reads them; a blank id skips that submission. No IDs in `commonMain`. Keeps shared code platform-clean and lets the app ship before the Play Console definitions are final.

- **Submit on round finish, from existing aggregates.** Hook the same place `ResultRecord` is produced. Leaderboards are cross-language aggregates: best streak = max of per-language best; total wins = sum of wins across languages. Achievements checked against the post-round history. All best-effort: wrapped so any failure or signed-out state is swallowed and never blocks the result.

- **Sign-in: standard v2 at startup, tolerate signed-out.** `PlayGamesSdk.initialize()` in `HarfApplication`; `GamesSignInClient.isAuthenticated`/`signIn` per the v2 flow. A signed-out player just skips submissions.

## Risks / Trade-offs

- **Two Google surfaces** (our Credential Manager account-link + PGS sign-in) → possible double prompt/confusion. Mitigation: they're separate flows; PGS auto sign-in is silent when already authorized. UX note, not a bug.
- **Cross-language aggregate semantics** → "best streak" across languages may surprise a multi-language player. Accepted; simplest and matches a single global leaderboard. Revisit with per-language boards if wanted.
- **ID drift** → leaderboard/achievement ids in Play Console must match the resource. Mitigation: blank/absent ⇒ no-op (no crash); document the mapping in the resource file.
- **GMS dependency size** on Android only; other targets unaffected.

## Migration Plan

1. Add the catalog dep + Android manifest `APP_ID` meta-data + `PlayGamesSdk.initialize()`.
2. Land the `expect`/`actual` `GamesServices` with the no-op default first (compiles everywhere), then the Android impl.
3. Wire round-finish submissions and Settings entries.
4. Create leaderboards/achievements in Play Console; paste ids into `games-ids.xml`. Until then the feature no-ops.
5. Rollback: unbind the Android impl (bind no-op) or blank the ids; nothing else depends on it.

## Open Questions

- Final leaderboard/achievement set and their Play Console IDs — adjustable at implementation without changing the approach or specs (the spec names them only as examples).
