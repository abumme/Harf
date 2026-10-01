# Tasks

The order follows the design: fix opacity and input first (it removes the visible defect on its own), then take pack building off the UI thread, then change the motion (a slide shows dropped frames more than a fade, so it lands after the threading fix), then the Game skeleton. Each group is a buildable, separately committable step (`sharedUI: …`).

## 1. Opaque destinations and input guard (`AppNavHost`)

- [ ] 1.1 Add the private `NavGraphBuilder.screen<T>()` helper that wraps each destination in `Box(Modifier.fillMaxSize().background(LocalHarfColors.current.paper))`, and switch all seven `composable<…>` calls to it. Verify with `./gradlew :sharedUI:jvmTest`, and on an Android/iOS run confirm the status-bar and home-indicator strips stay paper-colored during Home → Stats → back.
- [ ] 1.2 Remove the now-redundant `.background(colors.paper)` after `windowInsetsPadding` in `GameScreen`'s root `BoxWithConstraints`. Verify the Game page is paper edge to edge (including the inset strips) and `BoardScreenshotTest` still passes locally (advisory).
- [ ] 1.3 Add one `back()` that pops only when `navController.previousBackStackEntry != null`, wrap the no-arg navigation lambdas in `dropUnlessResumed { … }`, and guard `onPlay(languageId)` and `onOpenPuzzle(…)` with an `entry.lifecycle.currentState == RESUMED` check. Verify on Android/iOS:
  - a fast double tap on Stats' back lands on Home, never on an empty screen;
  - a fast double tap on a Home language button opens one Game, and one back returns to Home.

## 2. Word packs off the UI thread

- [ ] 2.1 In `WordPackRepository`:
  - replace the `HashMap` with a `@Volatile` immutable `Map` (fast path outside the mutex, writes inside it);
  - add a trailing constructor parameter `buildDispatcher: CoroutineDispatcher = Dispatchers.Default` and run the build (`buildFromDto`/`buildBundled`) inside `withContext(buildDispatcher)`.

  Verify that `WordPackClientTest` and `FirstSyncWaitTest` pass unchanged (`./gradlew :sharedUI:jvmTest --tests "*WordPackClientTest" --tests "*FirstSyncWaitTest"`).
- [ ] 2.2 Add `suspend fun build(dto): WordPack?` (integrity check plus build on `buildDispatcher`) and `suspend fun adopt(pack)`, and remove `isAdoptable`. Add tests:
  - `load` after `adopt` returns the adopted instance (same reference, no rebuild);
  - `build` returns null for a pack failing `WordPackIntegrity`, and the previously resolved pack stays in use.
- [ ] 2.3 Change `WordPackSyncManager.syncOne` to `build`, then `cache.put`, then `adopt` on `200`, and make `hasCachedPack` return true once that language's first sync of the session has completed. Extend the sync tests with a fake HTTP engine:
  - a valid `200` makes the next `repository.load` return the adopted pack without rebuilding;
  - an invalid `200` caches nothing and keeps the current pack;
  - `FirstSyncWaitTest` still passes.
- [ ] 2.4 In `App.kt`'s launch `LaunchedEffect`, add a `launch` that warms `packs.load(lang)` sequentially for the distinct `ResultRecord.language` values in `resultLog.all()`, most recent `puzzleDay` first, each in `runCatching`. Verify:
  - with a result log containing `ru`, a cold start resolves `ru` before Game is opened (log or debugger), and an unplayed language (e.g. `kk`) is not resolved until it is opened;
  - a fresh install (empty log) resolves nothing at launch.
- [ ] 2.5 Measure the Game-open stall before and after groups 1–2 on a release-like build (Android: non-debuggable build plus `adb shell dumpsys gfxinfo uz.abumme.harfgame framestats`, first Game open for `en` after a cold start; iOS: Release scheme with Instruments → Animation Hitches). Verify no frame during the Home → Game transition shows `WordPackIntegrity.check`/`Tokenizer.tokenize` on the main thread, and record the numbers in this task.

## 3. Explicit transitions (Android/iOS slide, desktop/web instant)

- [ ] 3.1 Add `navigation/NavTransitions.kt` with `internal expect val animatesNavigation: Boolean` and the actuals: `true` in androidMain and iosMain, `false` in jvmMain, jsMain and wasmJsMain. Verify JVM → Android → Wasm/JS → iOS compile, in that order (`:sharedUI:compileKotlinJvm`, `:androidApp:assembleDebug`, `:webApp:wasmJsBrowserDistribution`/`jsBrowserDistribution`, iOS framework link).
- [ ] 3.2 Define the six transitions per design D3 (`tween(300, FastOutSlowInEasing)` slide with a quarter-width parallax; the predictive pair with `LinearEasing`; `None` when `animatesNavigation` is false) and pass all six to `NavHost`. Verify:
  - Android: button back and predictive back gesture move the same way, and the gesture tracks the finger;
  - iOS: push and edge-swipe show no darkening and no final-frame snap;
  - desktop (`:desktopApp:run`) and web still switch instantly;
  - with `Animator duration scale` at 5× on Android, no frame shows two pages through each other.

## 4. Game page from the first frame

- [ ] 4.1 Add `feature/game/GameLoadViewModel` (entry-scoped via `viewModel { … }`) that exposes `StateFlow<Loaded?>` and `select(script)`, loading the puzzle, pack, config and saved round in `viewModelScope`, and keeping the previous `Loaded` until the new one is published. Add a commonTest with fake provider/repository/round store:
  - `select` publishes the loaded round;
  - selecting a second script keeps the first `Loaded` visible until the second completes.
- [ ] 4.2 Switch `GameScreen` to the loader:
  - remove the `remember`-held `loaded` and the `loaded = null` reset;
  - replace `loaded ?: return` with a skeleton that draws the same root box (insets, padding) and the header row with the working back control;
  - make `switchScript` call `loader.select(targetScript)`.

  Verify:
  - opening Game shows the header and background in the first transition frame;
  - back works before loading finishes;
  - Game → Paywall → back shows the board at once with the current input intact;
  - switching Uzbek Lotin ↔ Кирилл never blanks the board.
- [ ] 4.3 Make the header row lay out the same with and without data (hard-mode toggle and script chips in fixed slots), so `Loaded` arriving does not shift the back control. Verify by comparing the back control's position in the skeleton and the loaded frame (Layout Inspector or a `SemanticsDumpTest` bounds check).

## 5. Integration

- [ ] 5.1 Run `./gradlew :sharedUI:jvmTest` and `:sharedUI:verifyRoborazziJvm`, and push to a branch so CI runs the canonical goldens. Verify CI is green. If a golden changed, review the diff and take it from the `roborazzi-goldens` artifact per CLAUDE.md.
- [ ] 5.2 Run `openspec validate smooth-screen-transitions --strict` and walk every scenario in the three delta specs on Android and iOS release-like builds (plus the desktop/web instant-switch scenario). Verify each scenario passes and note any that do not.
