## Why

Moving between screens in Harf looks jerky on the platforms that animate it (Android and iOS). An investigation of the navigation path (every hypothesis checked against the app and library sources) found four causes, all in our code and not in the device:

- **Every destination is transparent.** The only opaque layer is the theme `Surface` under the whole `NavHost` (`HarfTheme.kt:68`). No screen root draws a background (`HomeScreen.kt:79`, `StatsScreen.kt:105`, `SettingsScreen.kt:84`, …), and `GameScreen` paints `paper` only inside the safe-drawing insets (`GameScreen.kt:391-395`). The library's default transitions assume opaque pages. On iOS the push puts a 50 % black veil over the outgoing page that the incoming page is meant to cover. Ours does not cover it, so the screen darkens, both pages show through each other, and in the last frame the screen snaps back to paper. On Android the 700 ms crossfade shows two layouts at once, and predictive back shrinks the top page over a visible page below.
- **The word pack is built on the UI thread during the transition.** `GameScreen` loads its pack in a `LaunchedEffect` (main dispatcher). `WordPackRepository.load` tokenizes 9–23 thousand guesses with no `withContext` (`WordPack.kt:52-61`). That is about 40 ms on a desktop JVM and more on a phone in a debug build, and the transition freezes and then jumps. Sync makes this worse: it builds a fetched pack only to check it, throws the result away and invalidates the cache, so the next Game open builds the pack again on the UI thread (`WordPackSyncManager.kt:63-65`).
- **Game draws nothing until it has loaded.** `val data = loaded ?: return` (`GameScreen.kt:140`). An empty page slides in and the board pops in mid-transition or after it. `loaded` lives in a plain `remember`, so coming back from the paywall shows the empty page again.
- **Taps during a transition still navigate.** `AppNavHost` calls `navigate`/`popBackStack` unguarded. A quick double "back" pops Home too and leaves a blank `NavHost`, which breaks the app-shell rule that back at the root never shows an empty screen.

## What Changes

- **Opaque destinations.** Every destination in `AppNavHost` is drawn on its own full-bleed `paper` background, outside the screen's inset padding, so pages never show through each other in any transition, gesture or system-bar strip.
- **Navigation ignores input while a transition runs.** Forward and back actions are dropped unless the source entry is resumed, and the in-app back action never pops the start destination.
- **Explicit transitions, per platform.** On Android and iOS, all six `NavHost` transitions (enter, exit, popEnter, popExit, predictivePopEnter, predictivePopExit) are a short horizontal slide (~300 ms) with parallax on the page underneath, the same for the back button and the back gesture. Desktop and web keep the instant switch they have today.
- **Word packs are never built on the UI thread.** `WordPackRepository` builds packs on `Dispatchers.Default`. Sync hands the pack it already built to the repository instead of discarding it and invalidating. The app warms the packs of the languages the player has played, off the UI thread, while Home is on screen.
- **Game always draws a frame and keeps its round.** Loading moves out of composition into a ViewModel scoped to the Game entry. Until the round is ready, Game draws its own page (background, insets, header) instead of nothing. Returning to Game from the paywall reuses the loaded round. Switching the Uzbek script keeps the current board until the other script's round is ready.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `app-shell`: navigation between destinations is visually continuous (opaque pages, platform-appropriate transitions) and ignores input while a transition is in progress. Rapid back never leaves an empty screen.
- `word-packs`: resolving a language's pack never blocks the UI thread, and a fetched pack that passed integrity is adopted without being built a second time.
- `game-board`: the game screen shows its page from the first frame of navigation and keeps a loaded round when the player returns to it.

## Impact

- **Code (`sharedUI` only):**
  - `navigation/AppNavHost.kt`: background wrapper, input guard, transitions.
  - A new `expect`/`actual` flag for whether navigation transitions animate: `true` on Android/iOS, `false` on desktop and both web targets.
  - `engine/WordPack.kt` (`WordPackRepository`) and `data/wordpack/WordPackSyncManager.kt`.
  - `App.kt`: pack warm-up.
  - `feature/game/GameScreen.kt` plus a new Game load ViewModel.
- **No `sharedData`, `backend` or API change.** Pack integrity rules, the daily-word rules and the sync protocol are unchanged.
- **Tests:**
  - `FirstSyncWaitTest` uses `invalidate`, which is kept.
  - New `jvmTest` coverage for the repository threading and adoption, and for the Game loader.
  - The screen goldens should not change, because the background wrapper is in `AppNavHost` and not in the screen composables. Any golden diff is taken from the CI artifact as usual.
- **Memory:** warming keeps the packs of played languages resident earlier than today. Unplayed languages are not warmed.
- **Overlap:**
  - `result-moment` and `dark-editions` also touch `GameScreen.kt` (tile reveal, on-mark colors), but different parts of the file. Rebase whichever lands second.
  - `dark-editions` changes what `paper` is, and the destination background reads `LocalHarfColors.current.paper`, so it follows the active edition.
