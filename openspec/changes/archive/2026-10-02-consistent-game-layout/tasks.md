## 1. Layout plan (pure, testable)

- [x] 1.1 Add `feature/game/GameLayout.kt` with the portrait and wide `plan(...)` functions and the chrome constants from design.md (top bar 44, strip 28, gaps 8, key gap 2, row gap 6, action key 3 units, tile cap 64, key cap 56, ladder 48/44/40 at 40/36 dp tiles); verify `jvmTest/.../GameLayoutTest.kt` passes over the device matrix 320×568, 360×640, 360×740, 360×800, 393×852, 412×915, 430×932 × all five launch languages: row count never depends on width, tiles ≥ 36 dp from 360×640 up, key width within 21–56 dp, the ladder steps only when the tile threshold fails, and the numbers match `docs/ui-review/game-layout.html` for the 360×640 / 393×852 / 412×915 cases.

## 2. Keyboard data

- [x] 2.1 Add `actionRowKeys: List<String> = emptyList()` to `LanguageConfig` and change `LaunchLanguages.uzLatn` to three letter rows plus `actionRowKeys = ["oʻ", "gʻ"]`; verify `LanguageConfigTest` (keys ⊆ graphemes across letter rows and action-row keys; Uzbek-Latin digraphs still have keys) and `./gradlew :sharedData:allTests` pass, and `-PbackendOnly :backend:compileKotlin` plus `-PadminWebOnly :adminWeb:compileKotlinJs` still compile.

## 3. Keyboard view

- [x] 3.1 Rewrite `KeyboardView` to the fixed structure: letter rows, then the action row with ENTER (3 units) leading, ⌫ (3 units) trailing and the action-row graphemes centered between them; sizes and label size from the plan; remove the inline/fallback branch and `ActionCap`; verify in `BoardScreenshotTest` frames at 360 and 412 dp widths that uz-cyrl and ru render the same rows, and that action-row graphemes show used-state marks while ENTER/⌫ do not.
- [x] 3.2 Build the physical-keyboard lookup from letter rows plus `actionRowKeys`; verify on desktop (hot reload) that typing letters, Enter and Backspace still drive en and ru rounds.

## 4. Game screen layout

- [x] 4.1 Restructure the portrait layout in `GameScreen.kt` per design.md: 8 dp vertical padding, 44 dp top bar (40 dp back/help controls, hard-mode chip, Uzbek script switch centered), board box taking the remainder with 16 dp side padding, 28 dp status strip, keyboard slot of the planned height with 4 dp side padding; delete the chips row and the inline legend; verify with hot reload at 360×640, 393×852 and 412×915 that tile/key sizes match the plan and nothing clips.
- [x] 4.2 Apply the same pieces to the wide layout (left: top bar + board + strip; right: slot) using the wide plan; verify at 915×412 and 1100×720 that both halves fit without scrolling.
- [x] 4.3 Replace the `message` / `hardViolation` / `suggestCandidate` visibility logic with one strip-message state: incomplete and hard-mode messages auto-dismiss after 1.5 s, the not-in-word-list message with its suggest link stays until the guess changes or the suggestion is sent, sent/failed confirmations auto-dismiss, single line with ellipsis, crossfade on change; verify by playing through each case on hot reload that the board and keyboard bounds never change (semantics tree bounds before/after) and that the suggest action still sends.
- [x] 4.4 Give the bottom slot a fixed height equal to the keyboard height and show `ResultView` inside it, centered, with inner vertical scroll; verify that finishing a round and restoring a finished round leave the board bounds unchanged.

## 5. Settings legend

- [x] 5.1 Add `MarkLegend()` under the style picker in the Settings mark-style section; verify it renders in the active style and re-renders when the style changes (jvmTest or screenshot), and that the game screen's `?` dialog still shows the legend.

## 6. Verification

- [x] 6.1 Compile JVM → Android → Wasm/JS (`:sharedUI:compileKotlinJvm`, `:androidApp:assembleDebug`, `:webApp:wasmJsBrowserDevelopmentWebpack`) and run `./gradlew :sharedUI:jvmTest :sharedData:allTests`; verify all pass.
- [x] 6.2 Extend `BoardScreenshotTest` with phone frames (360×640, 393×852, 412×915 × uz-latn, uz-cyrl, en) and a landscape frame, re-record goldens from the CI `roborazzi-goldens` artifact, review the diff and commit; verify `verifyRoborazziJvm` passes on CI.
- [ ] 6.3 Mark `improve-game-screen` tasks 3.1–3.3 as superseded by this change (note in its tasks.md); verify on two Android phones of different diagonals that the keyboard structure is identical and the board is at least 40 dp tiles on both.
