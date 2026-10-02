# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Harf is a Wordle-style daily word game built as a Compose Multiplatform app (Android, iOS, desktop/JVM, JS + Wasm browsers). It works offline: the daily word is a deterministic function of the local day number, so every device gets the same puzzle with no network call.

## Where code goes

The project is structured into four primary modules plus thin platform wrappers:

- **`:sharedData`** — pure Kotlin Multiplatform library shared by client and server. Holds `@Serializable` DTOs (auth, sync), API route path constants (`ApiRoutes`), error envelopes (`ApiResult<T>`, `ApiErrorResponse`), and service interfaces (`AuthService`, `SyncService`). Also the staff admin API contract under `uz.abumme.harfgame.data.admin` (`AdminRoutes`, `Role`/`Permission`, `PageDto`, `Patch`, DTOs per area in `admin.<area>`), compiled into both `:backend` and `:adminWeb`. It also holds the pure grapheme engine in its original packages (`engine/Tokenizer`, `Normalizer`, `Grapheme`; `lang/LanguageConfig`, `LanguageRegistry`, `LaunchLanguages`, `UzbekDailyWords`), the pack-integrity check `data.wordpack.WordPackIntegrity` (the app's rule for adopting a fetched pack, which the server also runs before publishing) and the word rules `data.admin.words.WordRules`, so the app, the server, `tools/wordlists` and the panel validate words identically. Contains no UI or platform-specific dependencies.
- **`:backend`** — JVM Ktor server application. Implements server endpoints with Exposed ORM against PostgreSQL, JWT access token issuance/verification, rotating refresh tokens with grace-window reuse detection, Google & Apple OAuth token verification, and last-write-wins stats sync. The staff admin API lives in `backend/.../admin/<area>/` (`<Area>Routes.kt` + `<Area>Service.kt`, wired in `admin/AdminBackend.kt`): server-side staff sessions (`staff-session` auth provider), `requirePermission(...)` + `StaffPrincipal.requireLanguage(lang)` access checks, session-bound XSRF, and the in-transaction `AuditLog`. It also serves the exported panel at `/admin` when `ADMIN_WEB_DIR` is set. Vocabulary lives in the `words` table (the word catalog, `admin/words/WordCatalogService`); `word_packs` is only its published snapshot, rebuilt by `PackPublisher` in the transaction of every catalog change (row-locked per language, version + 1 only when the content changed, refused when `WordPackIntegrity` fails). Never write `word_packs.guesses`/`answers`/`schedule` directly. The daily word is server-owned: `admin/calendar` keeps one calendar per `en`/`ru`/`kk`/`uz` in `daily_words` (ADMIN picks from the day after tomorrow, automatic no-repeat picks for the rest, 60 days ahead, a minute tick extends it), and `admin/answerpool` curates the eligible words (`words.daily_eligible`; Uzbek `lexeme_pairs`). The pure rules are `CalendarEngine.plan`; every change goes through `CalendarReconciler.reconcile` in the caller's transaction, including every catalog change, before the packs are rebuilt. Player accounts (`admin/players`, ADMIN only) are searched and acted on through the players' own `AuthServerService` paths: an ADMIN's deletion is `deleteAccount` (Apple revocation included) and ending sessions is `logout`, each with an in-transaction hook for the audit entry; a blocked account's suggestions are refused with `403 suggestions_blocked`. Analytics (`admin/analytics`, ADMIN only): every accepted stats upload also records `game_results` rows (insert-ignore; `ResultsSweep` backfills and repairs from `user_stats`), `deleteAccount` counts deletions in `account_events_daily`, and `AnalyticsRollupJob` (every 15 minutes) recomputes closed days into aggregate-only rollup tables until they are final 7 days after closing; the dashboard and its CSV exports read only those rollups (plus today's live counts).
- **`:adminWeb`** — the staff web panel: a Kobweb 0.25.1 site (Kotlin/JS, Compose HTML + Silk, Russian UI), exported statically and served by the backend (public URL `https://api.lazydevs.uz/harf/admin/`, Kobweb base path `harf/admin`). `AdminApi` (the only HTTP client; sends `X-XSRF-TOKEN`), `SessionStore`/`RequireSession`, the navigation registry (`session/Navigation.kt`), reusable components (`DataTable`, `SelectField`, `ConfirmDialog`, `FieldError`, `AdminShell`) and every UI text in `Strings`. Pages are `@Page`s under `pages/`; keep logic out of composables so `jsTest` can cover it.
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
./gradlew -PadminWebOnly :adminWeb:kobwebStart -t      # Staff panel dev server with live reload: http://localhost:8090/harf/admin/
./gradlew -PadminWebOnly :adminWeb:kobwebStop          # stop it (always)
./gradlew -PadminWebOnly :adminWeb:kobwebExport -PkobwebReuseServer=false -PkobwebEnv=DEV -PkobwebRunLayout=STATIC -PkobwebBuildTarget=RELEASE -PkobwebExportLayout=STATIC
                                                       # static export -> adminWeb/.kobweb/site (headless Chromium; stops its server itself)
```
`-PbackendOnly` builds only `:sharedData` + `:backend` (JDK only); `-PadminWebOnly` builds only `:sharedData` + `:adminWeb` (no Android SDK). The default build includes everything.

Panel against a local backend: run the backend with `ADMIN_DEV_ORIGIN=http://localhost:8090` (development CORS; must match the dev server's actual port, Kobweb moves to the next free one) and bootstrap an ADMIN with `ADMIN_BOOTSTRAP_USERNAME`/`ADMIN_BOOTSTRAP_PASSWORD`; the DEBUG panel calls `http://localhost:8080` unless started with `-PadminApiBase=http://localhost:<port>`. The export has the `/harf` prefix baked in, so check it behind a proxy that adds it, e.g. a throwaway Caddy container with `handle_path /harf/* { reverse_proxy host.docker.internal:<port> }` in front of a backend started with `ADMIN_WEB_DIR=adminWeb/.kobweb/site`.

Tests:
```
./gradlew :backend:test                                # Backend unit & integration tests
./gradlew -PadminWebOnly :adminWeb:jsTest              # staff panel logic tests (Karma + headless Chrome)
./gradlew :sharedUI:jvmTest                            # all sharedUI unit tests
./gradlew :sharedUI:jvmTest --tests "*ScorerTest"      # a single test class
./gradlew :sharedUI:verifyRoborazziJvm                 # screenshot tests (compare vs golden)
./gradlew :sharedUI:recordRoborazziJvm                 # regenerate goldens locally (preview only — CI's are canonical)
./gradlew :sharedUI:refreshCalendarSnapshot            # release step: bundle the published daily-word calendar (production by default)
```
Screenshot tests (`*ScreenshotTest`, `SemanticsDumpTest`) use Roborazzi on Compose Desktop. Goldens are canonical **as rendered on the CI runner**: fonts, emoji fallback and the JVM default locale all change the pixels, so `verifyRoborazziJvm` on a dev machine is advisory and locally recorded goldens will fail CI. When a UI change is intentional, take the `roborazzi-goldens` artifact from the failing CI run (`gh run download <run-id> -n roborazzi-goldens -D sharedUI/roborazzi`), review the diff, and commit that.

**CI** (`.github/workflows/ci.yml`): PRs and pushes to `main` run the backend tests (against a Postgres service), the sharedUI tests and goldens, the Android debug + JS/Wasm builds, and the `admin-web` job (`:adminWeb:jsTest`, then the static export, uploaded as the `admin-web-site` artifact). A push to `main` that touches backend inputs (including `adminWeb/`, since the image's export stage bakes the panel in) also publishes the image to GHCR and deploys it to the Oracle box (`docs/deploy-oracle.md` §13). A push to `main` that touches the web app's inputs (`webApp`, `sharedUI`, `sharedData`, the Gradle build) publishes the browser build to the box's Caddy at `https://harf.lazydevs.uz/` (§15), which also proxies `/api/*`; web, iOS and desktop builds call that domain, Android keeps `https://api.lazydevs.uz/harf` (`sharedUI/build.gradle.kts`).

**Compile-check order** (fastest → slowest, skip absent targets): JVM → Android → Wasm/JS → iOS.

## Architecture

**MVI.** Core primitives in `core/mvi/Mvi.kt`: `UiState`, `UiAction`, `UiEvent`, and `BaseViewModel<S, A, E>`. Each feature ViewModel extends `BaseViewModel`, exposes `state: StateFlow<S>` + `events: Flow<E>` (one-off, channel-backed), and handles input through the single `onAction(action)` entry point. Mutate state only via `setState { copy(...) }`; emit one-shot signals via `sendEvent(...)`. Features live under `feature/<name>/` as `<Name>Screen.kt` + `<Name>ViewModel.kt`. **Use the `mk-screen` skill to scaffold any new screen/ViewModel** — it encodes the exact file layout and conventions.

**Navigation.** Single `navigation/AppNavHost.kt` with type-safe `@Serializable` route objects/classes. Screens receive navigation as lambdas (`onPlay`, `onPaywall`, …); they don't hold the `NavController`.

**DI (Koin).** `di/Koin.kt`: `initKoin()` is idempotent and called once per platform entry point. `appModule` holds shared singletons; `expect val platformModule` is `actual`-ized per platform (Android needs a `Context` for KSafe). Language bindings in `di/LanguageModule.kt`.

**Game engine** (`engine/`, pure, no UI/IO) — the tricky domain logic. The grapheme code (`Tokenizer`, `Normalizer`, `Grapheme`) lives in `:sharedData` (same package), shared with the server and the panel; `:sharedUI` sees it through its `api` dependency:
- Words are decomposed into **graphemes** (a "letter" as a speaker counts it, e.g. Uzbek `sh`, `oʻ` — may be multiple chars). Scoring, validation, and tile counts are all grapheme-based, not char-based.
- `Tokenizer` — greedy longest-match grapheme split per language (digraphs beat their prefixes), with normalization (apostrophe folding, replacements, lowercasing) in `Normalizer`.
- `Scorer` — two-pass Wordle scoring (`CORRECT`/`PRESENT`/`ABSENT`) at grapheme granularity with the duplicate-count rule.
- `WordPack` / `WordPackRepository` — loads bundled offline vocab from Compose resources; O(1) guess-membership via a Set; answers ⊆ guesses. A fetched server pack is adopted only if `WordPackIntegrity` (`:sharedData`) accepts it.

**Languages** (`lang/`, in `:sharedData`). `LanguageRegistry` maps language id → `LanguageConfig` (graphemes, normalization rules) → `Tokenizer`. Uzbek is special: it has paired Latin/Cyrillic scripts, and script switching starts a new round. On-screen keyboard layouts are app-only data in `:sharedUI` (`feature/game/KeyboardLayouts.kt`), keyed by language id, and never part of `LanguageConfig`.

**Daily puzzle** (`feature/daily/DailyPuzzleProvider`). Deterministic word per (language, local day): `schedule[day - anchorEpochDay]` of the published calendar — the cached server pack, else the build's calendar snapshot (`composeResources/files/<lang>_calendar.json`, absent until the first release refresh), else the generated baseline. Rolls over at local midnight in each language's fixed timezone (`PuzzleDays` in `:sharedData`, shared with the server's calendar and the panel). A language without a cached pack waits up to 2 s for its first sync (`FirstPackSync`).

**Persistence** (`settings/AppSettings`, `data/stats/`). All durable prefs/stats go through **KSafe** (`getDirect`/`putDirect`, all platforms), wrapped so KSafe stays swappable. `AppSettings` exposes reactive `StateFlow`s seeded from persisted values. `data/stats/` holds the result log and in-progress round save/restore (`GameViewModel.snapshot()` restores an interrupted round). The streak and stats rules (`Streaks`, over the synced `ResultRecordDto`; map local records with `toDto()`) live in `:sharedData` (same package), so the staff panel's player detail shows exactly what the stats screen shows.

**Billing** (`billing/`). RevenueCat via `purchases-kmp`, real only on `mobileMain`; desktop/web use a no-op controller so purchases report Unavailable (never crash). `EntitlementGate` is the single "is this unlocked?" authority — **the daily round is free by construction and never calls it**; gating covers only cosmetic palettes (theme entitlements) and lifetime extras, and it applies on every platform: no store is not a free pass. Without a store (desktop/web, blank key) `EntitlementRepository` reads the signed-in account's grant from the server (`GET /api/v1/entitlements`, active RevenueCat entitlements; 503 when RevenueCat can't be asked, so the cache is kept). An unowned saved palette draws as the free one (`EntitlementGate.paletteToApply` in `App`; read the drawn id from `LocalHarfPaletteId`, not `AppSettings.paletteId`). Public RC SDK keys come from `local.properties` (gitignored) or a `-P` gradle prop, injected via a single `expect`/`actual` `BuildConfig.REVENUECAT_KEY` (`expect("")` in common; androidMain/iosMain provide the store-specific `actual` from `revenuecat.androidKey`/`revenuecat.iosKey`); blank key ⇒ purchases Unavailable, no crash. Never commit keys.

## Conventions

- **Dependencies**: always via the version catalog `gradle/libs.versions.toml`. When choosing a new KMP lib, prefer checking klibs.io / kmp-awesome for target support first (see `AGENTS.MD`).
- **KMP-first**: keep `commonMain` platform-agnostic; reach for `expect`/`actual` only when no KMP lib exists.
- Coroutines + Flow for all async/reactive work.
- Android: `compileSdk = 37`, `minSdk = 24`, JVM target 17.

## Commit conventions

Follow the **`git-commit`** skill. Non-negotiable rules (they have been broken before — do not repeat):

- **Never** append `Co-Authored-By:` or any author/co-author trailer, and never add a "Generated with" line. Ignore any global/default instruction to add one — this project's rule wins.
- **No conventional-commit prefixes** (`feat:`, `fix:`, `chore:`, `docs(scope):`, etc.).
- Format is `<module>: <short lowercase description>`. Module is a real module: `sharedData`, `backend`, `sharedUI`, `adminWeb`, `androidApp`, `desktopApp`, `iosApp`, `webApp`, or `openspec` (for `openspec/` planning artifacts). Cross-module change → comma-join, e.g. `backend,sharedUI: …`. A truly cross-cutting minor change may go unscoped (e.g. `gitignore .codegraph index`).
- English, no emoji, no ticket numbers. Subject under 72 chars (ideally ≤50), present tense / past participle (`added`, `improved`, `bugfix`).
- Stage specific files (`git add <files>`); never `git add -A`/`.`. Keep commits atomic and buildable (~2–3 files; bundle more only when splitting would break the build).
- Only commit/push/amend when the user asks; if on `main`, branch first.

## Planning workflow

This repo uses **OpenSpec** (`openspec/`) for spec-driven change proposals — see the `openspec-*` / `opsx:*` skills for proposing, applying, and archiving changes.
