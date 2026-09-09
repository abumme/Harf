## Why

The game already computes rich per-player history (streaks, wins, attempt distribution) but it lives only inside the app. Google Play Games Services (already configured in Play Console) turns that into social proof and retention hooks — leaderboards to compete on and achievements to chase — at low cost, since the underlying numbers already exist client-side.

## What Changes

- Integrate Play Games Services v2 (`com.google.android.gms:play-services-games-v2`) on **Android only**. iOS/desktop/web get a no-op implementation via `expect`/`actual`, exactly like billing and OAuth.
- Initialize the PGS SDK in `HarfApplication` and attempt the standard v2 sign-in so the player is authenticated for games features.
- Submit leaderboard scores and unlock achievements when a daily round finishes, derived from the existing `Streaks`/result-log data. Values are cross-language aggregates.
- Add a way to open the native Leaderboards and Achievements UIs (e.g. from Settings).
- **Isolation is a hard requirement:** PGS is a separate Android-only identity. It MUST NOT touch the app's account system (`SessionStore`, JWT, backend auth) or stats-sync. No server auth code, no backend linking. A player signed in (or not) to PGS behaves identically everywhere else.
- Play Console leaderboard/achievement IDs are injected as Android string resources (from the Play Console `games-ids.xml`), not hard-coded in shared logic. Missing/blank IDs ⇒ the feature no-ops without crashing (mirrors the blank-key billing pattern).

### Proposed leaderboards (2)
- **Best streak** — the player's best daily-solve streak (max across languages).
- **Total wins** — total solved dailies across languages.

### Proposed achievements (5)
- **First win** — first solved daily.
- **Week streak** — a 7-day streak.
- **Month streak** — a 30-day streak.
- **Hole in one** — solve a daily in the first guess.
- **Centurion** — 100 total wins.

(Exact set/IDs adjustable — they must be created in Play Console and their IDs pasted into the resource.)

## Capabilities

### New Capabilities
- `play-games`: Android Play Games Services integration — sign-in, leaderboard score submission, achievement unlocks derived from existing stats, and native leaderboard/achievement UIs, isolated from the app's own account and sync.

### Modified Capabilities
<!-- none — account-auth and stats-sync are untouched by design (PGS is isolated). -->

## Impact

- **Version catalog** (`gradle/libs.versions.toml`): add `play-services-games-v2`.
- **androidApp**: `PlayGamesSdk.initialize()` in `HarfApplication`; `com.google.android.gms.games.APP_ID` meta-data in the manifest; games-ids string resource.
- **sharedUI**: `expect`/`actual` `GamesServices` (interface + `NoOp` for non-Android, real Android impl using `GamesSignInClient`/`LeaderboardsClient`/`AchievementsClient` + `CurrentActivityProvider`); DI binding in `PlatformModule`; hook round-finish (where `ResultRecord` is produced) to submit scores/unlock; Settings entries to open native UIs.
- **No** changes to `account-auth`, `SessionStore`, backend, or `stats-sync`.
- **Config/Play Console**: leaderboard + achievement definitions and their IDs must be created and pasted into the resource; blank ⇒ no-op.
- Android-only footprint; other platforms compile against the no-op actual.
