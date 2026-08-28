# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Harf is a Wordle-style daily word game built as a Compose Multiplatform app (Android, iOS, desktop/JVM, JS + Wasm browsers). It works offline: the daily word is a deterministic function of the local day number, so every device gets the same puzzle with no network call.

## Where code goes

The project is structured into three primary modules plus thin platform wrappers:

- **`:sharedData`** — pure Kotlin Multiplatform library shared by client and server. Holds `@Serializable` DTOs (auth, sync), API route path constants (`ApiRoutes`), error envelopes (`ApiResult<T>`, `ApiErrorResponse`), and service interfaces (`AuthService`, `SyncService`). Contains no UI or platform-specific dependencies.
- **`:backend`** — JVM Ktor server application. Implements server endpoints with Exposed ORM against PostgreSQL, JWT access token issuance/verification, rotating refresh tokens with grace-window reuse detection, Google & Apple OAuth token verification, and last-write-wins stats sync.
- **`:sharedUI`** — Compose Multiplatform client codebase. Holds all client UI, game engine, navigation, local storage (KSafe), client Ktor adapters (`KtorAuthService`, `KtorSyncService`, `SyncManager`), and platform-specific OAuth glue.
- **Platform apps (`androidApp`, `desktopApp`, `iosApp`, `webApp`)** — thin wrappers holding only platform entry points (`main()` / `Application` / `Activity`).

Source sets under `sharedUI/src/`:
- `commonMain` — everything by default.
- `mobileMain` — shared by `androidMain` + `iosMain` only. Holds the **real RevenueCat** billing integration (`purchases-kmp`). Desktop/web get a no-op billing impl instead.
- `webMain` is written as `jsMain` + `wasmJsMain` (both browser targets).
- Platform code uses `expect`/`actual`: `expect` in `commonMain`, `actual` in `androidMain`/`jvmMain`/`iosMain`/`jsMain`/`wasmJsMain`. See `di/PlatformModule.*.kt` for the pattern.

Package root: `uz.abumme.harfgame`.

## Commands

Build/run:
```
./gradlew :androidApp:assembleDebug                    # Android debug APK
./gradlew :desktopApp:run                              # Desktop
./gradlew :desktopApp:hotRun --auto                    # Desktop with Compose Hot Reload
./gradlew :webApp:jsBrowserDevelopmentRun              # JS browser
./gradlew :webApp:wasmJsBrowserDevelopmentRun          # Wasm browser
./gradlew :webApp:composeCompatibilityBrowserDistribution   # Web dist bundle
./gradlew :backend:run                                 # Backend Ktor server
```

Tests:
```
./gradlew :backend:test                                # Backend unit & integration tests
./gradlew :sharedUI:jvmTest                            # all sharedUI unit tests
./gradlew :sharedUI:jvmTest --tests "*ScorerTest"      # a single test class
./gradlew :sharedUI:verifyRoborazziJvm                 # screenshot tests (compare vs golden)
./gradlew :sharedUI:recordRoborazziJvm                 # regenerate golden screenshots after intended UI changes
```
Screenshot tests (`*ScreenshotTest`, `SemanticsDumpTest`) use Roborazzi on Compose Desktop. When a UI change is intentional, run `recordRoborazziJvm` to update goldens and commit them.

**Compile-check order** (fastest → slowest, skip absent targets): JVM → Android → Wasm/JS → iOS.

## Architecture

**MVI.** Core primitives in `core/mvi/Mvi.kt`: `UiState`, `UiAction`, `UiEvent`, and `BaseViewModel<S, A, E>`. Each feature ViewModel extends `BaseViewModel`, exposes `state: StateFlow<S>` + `events: Flow<E>` (one-off, channel-backed), and handles input through the single `onAction(action)` entry point. Mutate state only via `setState { copy(...) }`; emit one-shot signals via `sendEvent(...)`. Features live under `feature/<name>/` as `<Name>Screen.kt` + `<Name>ViewModel.kt`. **Use the `mk-screen` skill to scaffold any new screen/ViewModel** — it encodes the exact file layout and conventions.

**Navigation.** Single `navigation/AppNavHost.kt` with type-safe `@Serializable` route objects/classes. Screens receive navigation as lambdas (`onPlay`, `onPaywall`, …); they don't hold the `NavController`.

**DI (Koin).** `di/Koin.kt`: `initKoin()` is idempotent and called once per platform entry point. `appModule` holds shared singletons; `expect val platformModule` is `actual`-ized per platform (Android needs a `Context` for KSafe). Language bindings in `di/LanguageModule.kt`.

**Game engine** (`engine/`, pure, no UI/IO) — the tricky domain logic:
- Words are decomposed into **graphemes** (a "letter" as a speaker counts it, e.g. Uzbek `sh`, `oʻ` — may be multiple chars). Scoring, validation, and tile counts are all grapheme-based, not char-based.
- `Tokenizer` — greedy longest-match grapheme split per language (digraphs beat their prefixes), with normalization (apostrophe folding, replacements, lowercasing) in `Normalizer`.
- `Scorer` — two-pass Wordle scoring (`CORRECT`/`PRESENT`/`ABSENT`) at grapheme granularity with the duplicate-count rule.
- `WordPack` / `WordPackRepository` — loads bundled offline vocab from Compose resources; O(1) guess-membership via a Set; answers ⊆ guesses.

**Languages** (`lang/`). `LanguageRegistry` maps language id → `LanguageConfig` (graphemes, normalization rules) → `Tokenizer`. Uzbek is special: it has paired Latin/Cyrillic scripts, and script switching starts a new round.

**Daily puzzle** (`feature/daily/DailyPuzzleProvider`). Deterministic word per (language, local day). Rolls over at local midnight in each language's fixed timezone.

**Persistence** (`settings/AppSettings`, `data/stats/`). All durable prefs/stats go through **KSafe** (`getDirect`/`putDirect`, all platforms), wrapped so KSafe stays swappable. `AppSettings` exposes reactive `StateFlow`s seeded from persisted values. `data/stats/` holds streaks, result log, and in-progress round save/restore (`GameViewModel.snapshot()` restores an interrupted round).

**Billing** (`billing/`). RevenueCat via `purchases-kmp`, real only on `mobileMain`; desktop/web use a no-op controller so purchases report Unavailable (never crash). `EntitlementGate` is the single "is this unlocked?" authority — **the daily round is free by construction and never calls it**; gating covers only cosmetic palettes (theme entitlements) and lifetime extras. Public RC SDK keys come from `local.properties` (gitignored) or a `-P` gradle prop, injected via `BuildConfig` (`REVENUECAT_ANDROID_KEY`/`_IOS_KEY`); blank key ⇒ purchases Unavailable, no crash. Never commit keys.

## Conventions

- **Dependencies**: always via the version catalog `gradle/libs.versions.toml`. When choosing a new KMP lib, prefer checking klibs.io / kmp-awesome for target support first (see `AGENTS.MD`).
- **KMP-first**: keep `commonMain` platform-agnostic; reach for `expect`/`actual` only when no KMP lib exists.
- Coroutines + Flow for all async/reactive work.
- Android: `compileSdk = 37`, `minSdk = 24`, JVM target 17.

## Planning workflow

This repo uses **OpenSpec** (`openspec/`) for spec-driven change proposals — see the `openspec-*` / `opsx:*` skills for proposing, applying, and archiving changes.
