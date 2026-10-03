## Why

`:sharedData` is compiled into the backend image, so CI's change detection (`.github/workflows/ci.yml`, job `changes`, `BACKEND_PATHS`) counts every main-source edit under `sharedData/` as a backend input. The on-screen keyboard layouts live there (`LanguageConfig.keyboard`, `LanguageConfig.actionRowKeys`), so a pure client edit to a layout rebuilds and redeploys the backend for nothing: `git diff --quiet 30c09d5^ 30c09d5 -- <BACKEND_PATHS>` reports a change for "sharedData: host uzbek latin oʻ gʻ in the keyboard action row". A path filter cannot fix it, because `LanguageConfig.kt` and `LaunchLanguages.kt` also hold the graphemes, normalization and exceptions that the backend, the staff panel and `tools/wordlists` really use. The layout is presentation data that only `:sharedUI` reads (`:backend`, `:adminWeb`, `tools/`, the platform apps and the rest of `:sharedData` never read either field).

## What Changes

- The five launch layouts (uz-latn, uz-cyrl, ru, en, kk) move, unchanged, from `LaunchLanguages` into `:sharedUI` as data keyed by language id, next to `KeyboardShape` in `feature/game/`. The client resolves the active language's layout from `LanguageConfig.id`.
- `LanguageConfig` loses `keyboard` and `actionRowKeys`. Its other fields, `LanguageRegistry`, `Tokenizer` and every `:sharedData` behavior stay as they are.
- The keyboard invariants move from `sharedData`'s `LanguageConfigTest` to a `:sharedUI` commonTest (keys are graphemes, action-row keys are not letter-row keys, Uzbek-Latin digraphs have keys, only Uzbek-Latin has action-row keys), still checked against the graphemes in `LaunchLanguages`. A new check: every launch language has exactly one layout.
- No visible change: same rows, same keys, same order, same physical-keyboard mapping; every Roborazzi golden stays byte-identical.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `language-config`: a language's definition is split into shared language data (inventory, tokenizer rules and exceptions, script pairing, display), used by the app, the server, the staff panel and the word-list tools, and a client-side keyboard layout keyed by the same language id. Both stay pure per-language data; adding a language still needs no engine code change.

`game-keyboard` is unchanged: "Data-driven per-language layout" renders from "the active language's configured layout" and names no module, so it holds as written.

## Impact

- `:sharedUI`: new `feature/game/KeyboardLayouts.kt` (the layouts) and its commonTest; `KeyboardShape.of(config)` reads the layouts by id (`GameLayout.kt`); the physical-keyboard lookup in `GameScreen.kt` reads the shape instead of the config; `KeyboardViewTest` reads its expected labels from the shape. `GameContent.kt`, `KeyboardView`, `GameLayoutTest`, `GameContentTest` and `BoardScreenshotTest` already go through `KeyboardShape.of(config)` and do not change.
- `:sharedData`: `LanguageConfig.kt` (two fields fewer), `LaunchLanguages.kt` (layouts removed), `LanguageConfigTest.kt` (keyboard checks removed). `LanguageConfig` is `@Serializable` but never serialized outside its own round-trip test, so no wire or stored format changes.
- `:backend`, `:adminWeb`, `tools/`: no source change; they compile against the slimmer `LanguageConfig`. Merging this change itself still counts as a backend input once (it edits `sharedData` main sources); later layout edits do not.
- `CLAUDE.md`: one line saying where keyboard layouts live, so they are not added back to `:sharedData`.
