## Context

See proposal.md (Why) for the four causes. Constraints that shape the design:

- **Navigation.** `AppNavHost` is a single `NavHost` using JetBrains `navigation-compose` 2.10.0-beta01 with no transitions set. The library's defaults are platform-specific: Android uses a 700 ms crossfade plus `scaleOut(0.7f)` on predictive back; iOS uses a 500 ms slide with `veilOut`/`unveilIn`; desktop and web use `EnterTransition.None`. If only `enterTransition`/`exitTransition` are overridden, the iOS defaults for `popEnter`/`popExit` take over those overrides, so all six parameters must be set.
- **Theme.** The only opaque layer is the theme `Surface(color = colors.paper)` in `HarfTheme`, under the whole `NavHost`. Screen roots are `fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)` with no background. The screenshot tests render the screen composables (`StatsContent`, `PaywallContent`, …) directly, not through `AppNavHost`.
- **Word packs.** `WordPackRepository.load` is `suspend` but never switches dispatcher, so it runs on its caller's dispatcher. `GameScreen` calls it from a `LaunchedEffect` (main). Archive (via `ArchiveDayBrowser`) and Stats call it too. The cache is a plain `HashMap` read outside the mutex. `isAdoptable` builds the full pack and discards it, and then `invalidate` evicts the resolved one.
- **Game screen.** `GameViewModel` needs `(puzzle, pack, restore)` at construction, so it can only be built after loading. Today that happens inside the composable: `loaded` is held in `remember`, the VM is created with `viewModel(key = script)` after `loaded ?: return`, and switching the Uzbek script sets `script`, which first resets `loaded = null`.
- **Platforms.** On JS/Wasm, `Dispatchers.Default` is the UI thread, so moving work there does not unblock the UI on web. Web has no navigation animation to protect.
- **Dependencies.** `lifecycle-runtime-compose` 2.11.0 is already a `sharedUI` dependency and provides `dropUnlessResumed`.

## Goals / Non-Goals

**Goals:**
- No navigation transition on Android or iOS overlaps translucent pages, darkens, or snaps on its last frame.
- No pack resolution, integrity check or fetched-pack adoption runs on the UI thread during a transition (Android, iOS, desktop).
- The Game destination is a real page from its first frame and keeps its round while it is in the back stack.

**Non-Goals:**
- Shared-element or per-destination custom transitions (e.g. a modal-style paywall). One motion for all destinations.
- Reworking other screens' first-frame states: Paywall's "Loading" row, the one-frame placeholders in Stats/Settings/Archive. These were checked and are cosmetic. They may get follow-up changes.
- Making web pack resolution non-blocking: no background thread there. Chunking `WordPackIntegrity.check` with `yield()` is possible later.
- Handling a midnight rollover while Game sits in the back stack. That behavior is unchanged: the round loaded for the entry stays.
- Debug-build performance. Measurements and acceptance use release-like builds (see Risks).

## Decisions

### D1. Opaque page wrapper lives in `AppNavHost`, not in each screen
A private `NavGraphBuilder.screen<T>()` helper wraps every `composable<T>` body in `Box(Modifier.fillMaxSize().background(LocalHarfColors.current.paper))`. The box sits outside the screen's `windowInsetsPadding`, so the inset strips are painted too.

- **Why here:** one place covers every current and future destination, and the screen composables used by the Roborazzi goldens do not change.
- **Hit target:** the box also carries a non-consuming `pointerInput`. Without it, a tap on an empty part of the top page falls through to the sibling page sliding underneath, because Compose hit-tests the next sibling when the top one has no pointer node.
- **GameScreen:** its own `.background(colors.paper)` after the insets becomes redundant and is removed.
- **Alternative:** add `.background` to each screen root. Rejected: seven edits, easy to forget on the next screen, and it changes the golden-rendered composables.
- **Alternative:** `Surface` per destination. Rejected: it also sets `contentColor` and adds tonal-elevation machinery for no benefit. `HarfTheme` already provides `contentColor`.

### D2. Input guard: lifecycle-based, per entry
Navigation lambdas are wrapped so they run only while their back-stack entry is `RESUMED`:
- no-arg lambdas use `dropUnlessResumed { … }`;
- Home's `onPlay(languageId)` and Archive's `onOpenPuzzle(…)` take arguments, so they use a small `NavBackStackEntry.ifResumed { … }` check on `entry.lifecycle.currentState`.

Back goes through one `back()` that pops only when `navController.previousBackStackEntry != null`.

- **Why lifecycle:** NavHost moves an entry to `RESUMED` only after its enter transition settles and drops it below `RESUMED` as soon as it starts leaving. That is exactly the "settled, current destination" in the spec, with no timers.
- **Alternative:** debounce by time. Rejected: it guesses the duration and still lets a tap through on a leaving page.
- **Alternative:** `launchSingleTop` alone. Rejected: it does not stop double pops.

### D3. Transitions: one common definition, gated by an `expect val`
`navigation/NavTransitions.kt` (commonMain) declares:
- `internal expect val animatesNavigation: Boolean`, with actuals `true` in androidMain and iosMain and `false` in jvmMain, jsMain and wasmJsMain;
- the six transition lambdas.

When `animatesNavigation` is false, every lambda returns `EnterTransition.None`/`ExitTransition.None`, preserving today's instant switch on desktop and web (user decision). When true:

| Transition | Motion |
|---|---|
| `enter` | `slideIntoContainer(Start)` |
| `exit` | `slideOutOfContainer(Start, targetOffset = width / 4)` |
| `popEnter` | `slideIntoContainer(End, initialOffset = width / 4)` |
| `popExit` | `slideOutOfContainer(End)` |
| `predictivePopEnter` / `predictivePopExit` | same geometry as `popEnter` / `popExit`, but with `LinearEasing` so the gesture maps 1:1 to position, and the direction taken from the swipe edge: `EDGE_LEFT` → right, `EDGE_RIGHT` → left, no edge → `End`. Android accepts a back swipe from either edge, and the page must move with the finger. The constants come from `navigationevent-compose`, declared explicitly at the version navigation-compose already pulls in. |

- All use `tween(300)` with `FastOutSlowInEasing`, except the predictive pair.
- `slideIntoContainer`/`slideOutOfContainer` use the container's layout direction, so RTL is handled by `Start`/`End`.
- **Why a Boolean `expect` instead of `expect fun transitions()`:** the motion is identical on both mobile platforms. Only "animate or not" differs, so the platform files stay one line each.
- **Alternative:** keep the native iOS veil push and only fix opacity. Rejected by the user in favor of one consistent slide. With D1 the veil would also be correct, so this can be revisited cheaply.
- **Alternative:** Material fade-through on Android. Rejected: the user chose slide, and two platforms with one motion keeps it simpler.

### D4. `WordPackRepository` builds off the UI thread and publishes immutably
- `load` keeps its fast path, but reads from a `@Volatile private var cached: Map<String, WordPack>` (immutable map, replaced on write). That fixes the unsynchronized `HashMap` read.
- The slow path stays under the mutex and does the build inside `withContext(Dispatchers.Default)`.
- New API:
  - `suspend fun build(dto): WordPack?` runs the integrity check and build on `Default`;
  - `suspend fun adopt(pack)` puts an already-built pack into the cache under the mutex.
- `isAdoptable` is removed: it has one caller and no test uses it. `invalidate` stays, because `FirstSyncWaitTest` uses it.
- **Why inside the repository and not at call sites:** every caller (Game, Archive, Stats, `DailyPuzzleProvider`, sync) gets the guarantee, and future callers cannot forget it.
- **Dispatcher injection:** the dispatcher is a constructor parameter, defaulting to `Dispatchers.Default`, so `runTest` can pass a test dispatcher. It is added last, so the existing positional and named calls (`LanguageModule`, `WordPackClientTest`) compile unchanged.

### D5. Sync adopts the pack it built
`WordPackSyncManager.syncOne` on `200`:
1. `repository.build(dto)?.let { pack -> cache.put(dto); repository.adopt(pack) }`.
2. Order: persist first, then publish. If `cache.put` throws, the in-memory pack is not swapped, so memory and disk cannot disagree.

`hasCachedPack` short-circuits to `true` once this session's first sync for that language has completed (`firstSyncs[lang]?.isCompleted == true`). After that, `awaitFirstSync` returns immediately anyway, so decoding the whole cached DTO just to null-check it is pure waste.

### D6. Warm-up: played languages only
In `App.kt`'s existing launch `LaunchedEffect`, add a `launch` that:
1. reads `resultLog.all()`;
2. takes the distinct `ResultRecord.language` values, most recently played first (by `puzzleDay`);
3. calls `packs.load(id)` for each, sequentially, wrapped in `runCatching`.

The build already runs on `Default` per D4.

- **Why not all five languages:** together they are ~75k tokenized guesses resident in memory, a real cost on low-end Android. A first-time player has played nothing, so their first Game open builds off-thread behind the skeleton (D7), which is still smooth.
- **Why sequential:** it avoids contending with the first frames and with sync for CPU.
- **Ordering:** warm-up and sync may race. Both go through the repository mutex, and `adopt` overwrites whatever the warm-up built, so the newest valid pack wins.

### D7. Game loading moves into an entry-scoped `GameLoadViewModel`
New `feature/game/GameLoadViewModel.kt`:
- created with `viewModel { … }` inside `GameScreen`. The default owner is the `Game` back-stack entry, so it survives a Paywall round-trip and is cleared on pop.
- holds `state: StateFlow<Loaded?>` and `fun select(script)`, which loads `provider.daily`, `packs.load`, the config and `roundStore.load` in `viewModelScope`, then publishes `Loaded`.

What changes in `GameScreen`:
- **Skeleton instead of an empty return.** `loaded ?: return` becomes a skeleton: the same root box (insets and padding) and the same `helpRow` back control, with an empty play area.
- **No reset on script switch.** `switchScript` keeps the current `Loaded` until the new script's one is published. The `loaded = null` reset is gone.
- **VM keyed by round.** `GameViewModel` is created with `viewModel(key = "$script#$generation")`, where `generation` is stamped on `Loaded` by each `select`. Keying by script alone reused the first visit's VM after switching there and back, which hid the converted round. A paywall round trip keeps the same `Loaded`, so it keeps the same VM.
- **Input frozen during a switch.** While a script switch converts the round, game input is ignored, because anything typed then would be lost with the board it was typed on.

- **Why a separate small VM instead of folding loading into `GameViewModel`:** `GameViewModel` is constructed with an immutable puzzle and pack and is well covered by tests. Making it nullable or async would touch every test. The loader is a thin, separately testable step.
- **Why not `rememberSaveable`:** `Loaded` holds a `WordPack` (thousands of entries) and is not saveable.
- **MVI:** the loader is a data-loading holder, not a feature screen, so it does not need `BaseViewModel<S, A, E>`. If reviewers prefer uniformity, it can extend `BaseViewModel` with a single `Select` action at no extra cost.
- **Skeleton layout:** the header row must lay out the same with and without data, so that `Loaded` arriving does not shift it (spec: no header shift). The hard-mode toggle and the script chips appear inside the row's existing slots.

## Risks / Trade-offs

- **[Risk] Slide motion looks wrong in RTL or in a split/desktop-size window on Android/iOS** → `Start`/`End` follow layout direction. Offsets are proportional to container width. Check on a tablet emulator.
- **[Risk] A Roborazzi golden changes** → goldens render screen composables, not `AppNavHost`, so D1 should not touch them. The skeleton path only renders before data is loaded, which the goldens do not capture. If CI shows a diff, take the `roborazzi-goldens` artifact per CLAUDE.md after review.
- **[Risk] `dropUnlessResumed` swallows a legitimate tap right after a transition** → the guard is false only while a transition is in progress (300 ms on mobile, 0 on desktop/web). This is the intended behavior.
- **[Trade-off] Web still blocks the UI while resolving a pack** → web has no transition to stall. The cost moves from Game-open to Home for played languages. Chunking can come later.
- **[Trade-off] Played-language packs stay resident from launch** → bounded by the languages the player actually plays, and it matches what happens after the first Game open today.
- **[Risk] Jank remains in debug builds** → Compose debug and Kotlin/Native Debug builds are several times slower. Acceptance and measurement use release-like builds:
  - **Android:** a non-debuggable build, e.g. a `benchmark` build type that inherits `release` and is signed with the debug key. Run `dumpsys gfxinfo framestats` before and after.
  - **iOS:** Release scheme, Instruments → Animation Hitches.
- **[Risk] Race between warm-up, sync and Game open** → all three are serialized on the repository mutex. `adopt` only installs a pack that passed integrity. The spec's "rejected pack changes nothing" stays covered by a test.

## Migration Plan

Client-only change with no data or API migration. It ships with the next app release. Rollback is a code revert: no persisted format changes, and `WordPackCache` contents are untouched.
