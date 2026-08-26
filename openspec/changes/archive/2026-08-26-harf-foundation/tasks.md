## 1. Dependencies & catalog

- [x] 1.1 Add `KSafe` (`eu.anifantakis:ksafe`) to `gradle/libs.versions.toml` and wire into `sharedUI` commonMain deps; verify `./gradlew :sharedUI:compileKotlinMetadata` resolves. — done (KSafe + ksafe-compose + koin-android; JVM/desktop/js/wasmJs compile green).
- [x] 1.2 Add PT Serif and Golos Text font files under `sharedUI/src/commonMain/composeResources/font/` and confirm they generate `Res.font` accessors on build. — done: PT Serif (Regular/Bold/Italic) + Golos Text (OFL) added; `Res.font.*` generated; `harfTypography()` (serif headings, Golos body) wired into `HarfTheme`; desktop compiles.

## 2. Settings storage

- [x] 2.1 Implement `AppSettings` wrapper over KSafe (typed get/set + defaults, `@Serializable` values, plain mode for non-sensitive keys) in `settings/`; verify a common unit test writes/reads a value and returns the default when unset. — done (AppSettingsTest passes on JVM with real KSafe).
- [x] 2.2 Expose reactive observation via KSafe Flow/StateFlow for a preference; verify a test observes a value change without restart. — done (StateFlow-backed; test covers observe + overwrite + persist-across-relaunch).
- [x] 2.3 Initialize KSafe per platform (android/ios/jvm/web) as needed; verify each target compiles and persists a value. — done for jvm (runtime) + js/wasmJs (compile) + android/ios (code in place; compile unverified here — no Android SDK / iOS toolchain).

## 3. Dependency injection

- [x] 3.1 Create `di/AppModule` and a shared `initKoin(appDeclaration)` in commonMain that registers `AppSettings`; verify a common test resolves `AppSettings` from the container. — done (idempotent initKoin + appModule; resolves at runtime on desktop via koinInject).
- [x] 3.2 Call `initKoin` from each platform entry point (androidApp Application, desktop main, iosApp controller, webApp main); verify each target launches without a missing-binding crash. — done; desktop verified launching without crash. Android via `HarfApplication`, web/iOS wired (android/iOS launch unverified in this env).

## 4. Theme / design system

- [x] 4.1 Define `HarfColors`, `HarfMarks`, and typography token holders plus their CompositionLocals in `theme/`; verify a preview/composable reads a token inside `HarfTheme`. — done (HarfColors + LocalHarfColors + LocalHarfMarkStyle; HomeScreen reads tokens).
- [x] 4.2 Port the mockup palettes as a `HarfPalette` set (paper/ink/accent/correct/present/absent + key roles) mirroring the editions in `docs/mockup.html`; verify each palette exposes all roles. — done (5 cold editions: newsprint/press/ink/blueprint/schoolbook).
- [~] 4.3 Port the pencil-scribble marks (loop=correct, underline=present, strike=absent) as shape/vector assets in the token layer; verify each mark renders and is shape-distinct in a preview. — DEFERRED by decision to `harf-cell-styles` / `harf-gameplay`, where the marks are actually rendered on tiles/keys. Foundation ships the color roles + `HarfMarkStyle` enum they plug into. (Chosen: keep marks out of foundation.)
- [x] 4.4 Implement `HarfTheme` composable that applies the active palette + typography and wire active-palette selection to `AppSettings`; verify changing the stored palette updates a preview and the value persists across a simulated relaunch (re-read). — done (HarfTheme reads settings.paletteId flow; persistence covered by AppSettingsTest; Home has a live palette switch).

## 5. MVI base

- [x] 5.1 Add `UiState`/`UiAction`/`UiEvent` contracts and `BaseViewModel<S,A,E>` (StateFlow state + channel-backed one-off events + `onAction`) in `core/mvi/`; verify a unit test asserts a one-off event is delivered exactly once and state updates reflect an action. — done (MviTest passes).

## 6. Navigation & app host

- [x] 6.1 Define the `@Serializable` route model with a `Home` route and a `NavGraphBuilder` extension pattern in `navigation/`; verify it compiles. — done (type-safe `@Serializable Home` + `AppNavHost`).
- [x] 6.2 Make `App.kt` host `HarfTheme` + a single `NavHost` starting at `Home` (placeholder screen using MVI + a token); verify back at root is a graceful no-op/exit. — done; desktop launches to themed Home.

## 7. Cross-platform verification

- [x] 7.1 Build/launch and confirm Home renders themed on each target in order — JVM (`:desktopApp:run`), Android (`:androidApp:assembleDebug`), Wasm/JS (`:webApp:*BrowserDevelopmentRun`), iOS (compile); record any target-specific settings/storage caveat. — DONE: JVM/desktop launches, js + wasmJs compile, `:androidApp:assembleDebug` builds, `:sharedUI:compileKotlinIosSimulatorArm64` compiles. Caveat found+fixed: manual `dependsOn(mobileMain)` edges had disabled the default hierarchy template, orphaning `iosMain` from the ios targets (expect/actual unresolved); fixed with explicit `applyDefaultHierarchyTemplate()`, plus common code used JVM-only Koin `GlobalContext` — replaced with `KoinPlatformTools.defaultContext()`.

## 8. Follow-ups (from code review)

- [x] 8.1 Seed `AppSettings.paletteId` off the main thread — the constructor's synchronous `ksafe.getDirect` runs on the UI thread when the Koin singleton is first resolved during composition, adding a blocking encrypted-prefs read to the first frame on cold start. Start the StateFlow at `DEFAULT_PALETTE` and load the stored value in a coroutine off-main (or resolve `AppSettings` outside composition). Low priority: KSafe has a hot in-memory cache and it is a single small read. (code review #7) — DONE: StateFlow starts at `DEFAULT_PALETTE`, stored value loaded via injected `seedScope` (default `Dispatchers.Default`) with `compareAndSet` so a user choice made before the read completes wins; `AppSettingsTest` updated (Unconfined scope) and passing.
