## 1. Layouts in :sharedUI

- [x] 1.1 Add `sharedUI/.../feature/game/KeyboardLayouts.kt` with `object KeyboardLayouts { val all: Map<String, KeyboardShape> }`: the five layouts cut and pasted (not retyped) from `LaunchLanguages`, keyed by `LaunchLanguages.<lang>.id`, with the `oʻ gʻ` action-row comment and the "first-pass, flagged for native review" note moved along. In `GameLayout.kt`, add `KeyboardShape.keys` (letter rows flattened, then action-row keys) and make `KeyboardShape.of(config)` return `KeyboardLayouts.all.getValue(config.id)`. Verify with a throwaway, uncommitted commonTest asserting `KeyboardLayouts.all.getValue(id) == KeyboardShape(config.keyboard, config.actionRowKeys)` for all five launch ids: it passes under `./gradlew :sharedUI:jvmTest --tests "*KeyboardLayouts*"`. Then delete it.
- [x] 1.2 Add `sharedUI/src/commonTest/.../feature/game/KeyboardLayoutsTest.kt` checking against `LaunchLanguages` graphemes: `KeyboardLayouts.all.keys == LaunchLanguages.all.keys`, every `keys` entry is a grapheme, no action-row key is in a letter row, Uzbek-Latin has three letter rows and `actionRowKeys == [oʻ, gʻ]`, the other four languages have no action-row keys, and every Uzbek-Latin digraph is a key. Verify `./gradlew :sharedUI:jvmTest --tests "*KeyboardLayoutsTest" --tests "*GameLayoutTest"` passes.
- [x] 1.3 Commit `KeyboardLayouts.kt`, `GameLayout.kt` and `KeyboardLayoutsTest.kt` as `sharedUI: keyboard layouts keyed by language id`, following the `git-commit` skill. Verify that `git show --stat HEAD` lists exactly those three files and that the message has no trailer.

## 2. Switch the direct readers

- [x] 2.1 In `GameScreen.kt`, build `keyLookup` from `KeyboardShape.of(config).keys.associateBy { it.lowercase() }`. In `KeyboardViewTest`, take the expected labels from `KeyboardShape.of(LaunchLanguages.uzCyrl).letterRows.flatten()`. Verify that `grep -rnE 'config\.keyboard|config\.actionRowKeys|\.keyboard\.flatten' sharedUI/src` finds nothing and that `./gradlew :sharedUI:jvmTest --tests "*KeyboardViewTest" --tests "*GameContentTest"` passes.
- [x] 2.2 Run `./gradlew :desktopApp:hotRun --auto` and drive it with the compose-hot-reload tools. In an en round and in a ru round, typing letters on the physical keyboard fills tiles, Backspace deletes one and Enter submits (the semantics tree shows the row scored). Verify that both languages behave as before.
- [x] 2.3 Commit `GameScreen.kt` and `KeyboardViewTest.kt` as `sharedUI: physical keyboard reads the layout shape`. Verify that `git show --stat HEAD` lists exactly those two files.

## 3. Drop the fields from :sharedData

- [x] 3.1 Remove `keyboard` and `actionRowKeys` from `LanguageConfig`. Its KDoc says the tokenizer and packs read it and the app keeps the keyboard layout keyed by `id`. Remove the five layouts from `LaunchLanguages`; its KDoc keeps only "inventories first-pass". From `LanguageConfigTest`, remove the keyboard checks and keep the round trip, a non-empty inventory per language, the Uzbek-Latin digraph set and no digraphs elsewhere. Verify that `./gradlew :sharedData:jvmTest :sharedUI:jvmTest` passes and that `grep -rnE 'actionRowKeys|\.keyboard\b' sharedData backend adminWeb tools` finds nothing.
- [x] 3.2 Verify that `./gradlew -PbackendOnly :backend:compileKotlin` and `./gradlew -PadminWebOnly :adminWeb:compileKotlinJs` compile, and that `git status --short backend adminWeb tools` is empty.
- [x] 3.3 Compile-check the remaining client targets in order: `:androidApp:assembleDebug`, then `:webApp:wasmJsBrowserDevelopmentWebpack`. Verify that both succeed.
- [x] 3.4 Commit `LanguageConfig.kt`, `LaunchLanguages.kt` and `LanguageConfigTest.kt` as `sharedData: dropped keyboard layout from language config`. Verify that `git show --stat HEAD` lists exactly those three files.

## 4. Docs and verification

- [x] 4.1 In `CLAUDE.md` (Languages paragraph), add one line: on-screen keyboard layouts are app-only data in `:sharedUI` (`feature/game/KeyboardLayouts.kt`), keyed by language id, and never part of `LanguageConfig`. Commit it unscoped (for example `noted where keyboard layouts live`). Verify that `git show --stat HEAD` lists only `CLAUDE.md`.
- [x] 4.2 Simulate the CI `changes` job with the `BACKEND_PATHS` and `WEB_PATHS` pathspecs copied from `.github/workflows/ci.yml`. Verify three results:
  - Over this change's commit range (`<first commit>^..HEAD`), the backend is reported changed, which is expected once.
  - After a throwaway working-tree edit to `KeyboardLayouts.kt` only (swap two keys), `git diff --quiet HEAD -- <BACKEND_PATHS>` exits 0 (backend=false) and `git diff --quiet HEAD -- <WEB_PATHS>` exits 1 (web=true).
  - After `git checkout -- <file>`, the working tree is clean.
- [ ] 4.3 Verify that `git status --short sharedUI/roborazzi` is empty after a local `:sharedUI:verifyRoborazziJvm`. When the user asks to push, verify that CI's backend tests, `:sharedUI:jvmTest` and `verifyRoborazziJvm` pass with no golden re-recorded.
