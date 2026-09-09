## 1. Dependency & Android setup

- [ ] 1.1 Add `play-services-games-v2` to `gradle/libs.versions.toml`; add it to `androidApp` (and `sharedUI` androidMain if the impl lives there). Verify Android compiles.
- [ ] 1.2 Add `com.google.android.gms.games.APP_ID` meta-data to `androidApp` manifest referencing a games-ids string resource; add `res/values/games_ids.xml` (app id + placeholder leaderboard/achievement ids from Play Console).
- [ ] 1.3 `PlayGamesSdk.initialize(this)` in `HarfApplication.onCreate`.

## 2. Shared interface (:sharedUI)

- [ ] 2.1 Declare `GamesServices` interface in `commonMain` (sign-in state, `submitResults(records, today)`, `showLeaderboards()`, `showAchievements()`, `isAvailable`) + a `NoOpGamesServices` default; verify `:sharedUI:jvmTest` compiles.
- [ ] 2.2 Bind `NoOpGamesServices` in the non-Android platform modules (jvm/js/wasm/ios); verify those targets compile.

## 3. Android implementation (:sharedUI androidMain)

- [ ] 3.1 `AndroidGamesServices` using `GamesSignInClient` (v2 sign-in / isAuthenticated), reading ids from resources; blank id ⇒ skip. Resolve `Activity` via `CurrentActivityProvider`.
- [ ] 3.2 Implement `submitResults`: compute best streak (max per-language `Streaks.streak().best`) and total wins (sum of wins) and submit to `LeaderboardsClient`; unlock achievements via `AchievementsClient` for the thresholds (first win, 7-day, 30-day, hole-in-one, 100 wins). All best-effort, swallow failures, skip when signed out.
- [ ] 3.3 Implement `showLeaderboards()`/`showAchievements()` launching the native PGS UIs; bind `AndroidGamesServices` in `PlatformModule.android.kt`.

## 4. Wire into gameplay & settings (:sharedUI)

- [ ] 4.1 Call `gamesServices.submitResults(...)` on round finish (where `ResultRecord` is produced / alongside `pushStats`), passing the current result log + today; ensure isolation — no coupling to `SessionStore`/`SyncManager` auth.
- [ ] 4.2 Add Settings entries (Android-only visibility via `isAvailable`) to open leaderboards and achievements; add string resources (en/ru/uz).

## 5. Verification

- [ ] 5.1 Isolation check: a unit/inspection confirming `GamesServices`/its wiring does not reference `SessionStore`, JWT, backend, or `SyncManager` auth.
- [ ] 5.2 Compile all targets (JVM → Android → Wasm/JS → iOS); non-Android use the no-op.
- [ ] 5.3 `:sharedUI:jvmTest` + `:backend:test` green (no regressions).
- [ ] 5.4 Manual Android smoke: sign-in prompt appears, finishing a round submits without crashing when signed out and when signed in; native UIs open. (Requires a device + Play Console setup.)
- [ ] 5.5 `openspec validate play-games-services --strict` passes.
