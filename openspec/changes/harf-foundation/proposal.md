## Why

Harf is a KMP/Compose Multiplatform daily word game targeting a Play Market launch. The repo currently holds only an empty scaffold (`sharedUI` + thin platform wrappers) with no DI, navigation, persistence, or design system. Every later feature — tile engine, gameplay, streak/stats, cell styles, monetization — needs a shared foundation to build on. This change establishes that foundation so the feature proposals that follow have a stable, consistent base and don't each reinvent wiring.

## What Changes

- Add **Koin** dependency injection: a root module and per-layer module convention, initialized from each platform entry point (`androidApp`, `desktopApp`, `iosApp`, `webApp`) into shared `startKoin` config in `sharedUI`.
- Add **Compose Multiplatform navigation**: a type-safe route model and a single `NavHost` graph, with a placeholder Home destination so the app launches to a real screen on all four targets.
- Add a **design-system / theme layer**: port the palette tokens and pencil-scribble mark styles explored in `docs/mockup.html` into Compose theme tokens (color roles, mark/scribble assets, typography using the bundled PT Serif + Golos fonts). Expose a `HarfTheme` composable and semantic token accessors.
- Add **settings persistence** via **KSafe** (`eu.anifantakis:ksafe`): a typed `AppSettings` store for user preferences (e.g. selected palette/theme, first-run flags) that survives relaunch on every platform. KSafe is secure-by-default (AES-256-GCM), stores `@Serializable` objects, and exposes reactive Flow/StateFlow across all targets; non-sensitive keys use its plain write mode.
- Add **base MVI scaffolding**: shared `State` / `Action` / `Event` contracts and a base `ViewModel` (lifecycle-viewmodel) pattern that all feature screens follow.
- Add required dependencies to the version catalog: `KSafe` (`eu.anifantakis:ksafe`). (Koin, navigation, lifecycle-viewmodel already present.)

Non-goals (deferred to later proposals): tile engine and scoring, game board/keyboard UI, streak/stats, the 3 cell-style variants + A/B rotation, RevenueCat monetization, any backend/leagues/gifting.

## Capabilities

### New Capabilities
- `app-shell`: application startup, DI initialization, and the navigation graph — the app launches to a Home screen and can navigate between destinations on Android, iOS, desktop, and web.
- `theming`: the design-system token layer (color roles, pencil-mark/scribble styles, typography) and a persisted active-theme selection applied app-wide.
- `settings-storage`: durable typed key–value storage for user preferences that persists across app launches on all platforms.

### Modified Capabilities
<!-- none — greenfield foundation -->

## Impact

- `gradle/libs.versions.toml`: add `KSafe` (`eu.anifantakis:ksafe`).
- `sharedUI/src/commonMain/kotlin/uz/abumme/harfgame/`: new `di/`, `navigation/`, `theme/` (extend existing `theme/Color.kt`, `theme/Theme.kt`), `settings/`, and `core/mvi/` packages; `App.kt` becomes the themed `NavHost` host.
- Platform wrappers (`androidApp`, `iosApp`, `desktopApp`, `webApp`): initialize Koin at entry point; otherwise unchanged.
- No backend, no network, no new stores. Offline-only.
