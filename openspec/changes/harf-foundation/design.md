## Context

Greenfield KMP scaffold (see proposal.md — Why). Present in the version catalog: Compose MP 1.12, Koin 4.2, navigation-compose (JB) 2.10-alpha, lifecycle-viewmodel, coroutines, serialization, ktor, buildConfig. Package `uz.abumme.harfgame`. `sharedUI` holds all code; `*App` modules are thin entry points. `docs/mockup.html` is the source of truth for the visual system (cold newspaper palettes, pencil-scribble marks, PT Serif + Golos). Bundled fonts already exist under `sharedUI/.../composeResources/font` (currently IndieFlower; PT Serif + Golos to be added as resources).

## Goals / Non-Goals

**Goals:**
- One shared wiring layer (DI, navigation, theme, settings, MVI) every feature reuses.
- Compiles and launches to Home on all four targets; iosMain stays thin.
- Theme token API rich enough for the gameplay board/keyboard and the later cell-styles work without rework.

**Non-Goals:**
- Any game logic, screens beyond a placeholder Home, or real feature content.
- Backend, network, analytics, purchases.
- Final visual polish — porting tokens, not pixel-finishing screens.

## Decisions

**Persistence: KSafe (`eu.anifantakis:ksafe`), not multiplatform-settings/Room/SQLDelight.**
Foundation needs small typed prefs (theme, first-run) with reactive reads. KSafe is a secure-by-default (AES-256-GCM) KMP key/value store that persists `@Serializable` objects and exposes Flow/StateFlow across all targets (Android/iOS/JVM/wasm/js), with a hot in-memory cache. Chosen over `multiplatform-settings` for built-in serializable-object + reactive support and encryption without extra modules. Expose an `AppSettings` wrapper over KSafe so callers never touch it directly (swap-friendly); non-sensitive keys use `KSafeWriteMode.Plain`. streak-stats (later proposal) reuses KSafe rather than adding a DB for its small log.

**DI: Koin, shared module list initialized per platform.**
A single `appModule` (plus feature modules appended later) assembled in a shared `initKoin(appDeclaration)` in commonMain; each platform entry point calls it (`Application.onCreate`, desktop `main`, `MainViewController`, web `main`). Screens obtain ViewModels via `koinViewModel()`. Alternative: manual wiring / Hilt — rejected (Hilt is Android-only; manual wiring doesn't scale across 6+ feature modules).

**Navigation: JB navigation-compose with a sealed/`@Serializable` route model.**
Single `NavHost` in `App.kt`; routes are `@Serializable` objects/data classes for type safety. Placeholder `Home` route ships now; feature graphs register destinations later via extension functions on `NavGraphBuilder`. Alternative: Voyager/Decompose — rejected to stay on the catalog's official artifact.

**Theme: token-object design system ported from the mockup.**
A `HarfTheme` composable provides a `HarfColors` (paper, paper2, card, ink, muted, rule, accent, correct, present, absent, key roles) plus `HarfMarks` (the pencil-scribble mark assets for correct=loop / present=underline / absent=strike) and typography (PT Serif display, Golos body) via CompositionLocals. Palettes are data (a `HarfPalette` enum/list mirroring the mockup's editions) so cell-styles/theme selection just switches the active palette. Marks are shape assets (vector/path) so state is distinguishable without color. Feedback marks live in the token layer even though the board is a later proposal — gameplay consumes them, doesn't redefine them.

**MVI base: minimal shared contracts.**
`interface UiState`, `interface UiAction`, `interface UiEvent`, and a `BaseViewModel<S,A,E>` exposing `StateFlow<S>` and a `Flow<E>` for one-off events (channel-backed), with `onAction(A)`. Kept tiny; no framework. Matches the docs' event-sourced/one-off-event intent.

## Risks / Trade-offs

- **navigation-compose is an alpha (2.10.0-alpha02).** → Isolate usage behind the app's route model + graph extensions so an API break is a contained edit, not a cross-feature churn.
- **KSafe web (wasm/js) storage caveats.** → Wrap behind `AppSettings`; web feature investment is post-freeze anyway (mobile is the launch target), so a degraded web store is acceptable short-term.
- **Theme token surface guessed ahead of the board UI.** → Tokens are additive; missing roles can be appended when gameplay lands without breaking callers.
- **iosMain must stay thin (CI-only debugging).** → Only Koin init + entry glue in iosMain; everything else in commonMain.

## Migration Plan

Additive, greenfield — no data or API migration. Rollout is ordinary compile-and-run verification in catalog order (JVM → Android → Wasm/JS → iOS). Rollback = revert the change; nothing else depends on it yet.

## Open Questions

- None blocking. KSafe is the chosen store; the `AppSettings` contract is stable regardless of KSafe's internal backend.
