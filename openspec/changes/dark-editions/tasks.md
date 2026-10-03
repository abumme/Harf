# Tasks

Visual authority: §06 "Dark editions" of `docs/ui-review/before-after.html` (dark ground + re-tuned marks).

## 1. Tokens

- [x] 1.1 Add `onCorrect`/`onPresent`/`onAbsent` roles to `HarfColors`; verify all editions compile.
- [x] 1.2 Add a dark variant per edition (dark paper/card/ink/muted/rule/accent + re-tuned correct/present/absent), starting from one disciplined dark recipe; verify each edition has a light and dark `HarfColors`.

## 2. Mode + resolution

- [x] 2.1 Persist a theme mode (`system`/`light`/`dark`, default `system`) via `AppSettings`/KSafe; verify it survives relaunch.
- [x] 2.2 In `HarfTheme`, resolve mode → light/dark: pick the edition's light or dark tokens and build `lightColorScheme`/`darkColorScheme` accordingly; `system` uses `isSystemInDarkTheme()`. Verify switching the platform theme flips the app in `system` mode.
- [x] 2.3 `App.kt`: report the resolved `isDark` to the platform (status-bar icons); verify chrome matches on Android/desktop.

## 3. Contrast fix

- [x] 3.1 In `Tile` and `KeyCap`, when a mark fills (Fill/Outline styles) draw the letter with the matching `on*` role instead of `ink`; verify a full round in Fill mode is legible on both light and dark.

## 4. Control + verification

- [x] 4.1 Add a theme-mode control (system/light/dark) in Settings (or Home); verify selection applies live.
- [x] 4.2 Compile-check JVM → Android → Wasm/JS; run `./gradlew :sharedUI:jvmTest`.
- [ ] 4.3 Record dark-variant screenshot goldens from CI; verify `verifyRoborazziJvm` passes for light and dark.
