## Why

The game screen looks different on every phone. The keyboard's structure flips with viewport width (enter/delete inline in the letter rows above ≈404 dp for ru/uz-cyrl and 376 dp for kk, a separate action row below it — an iPhone 15 and a Pixel 7 get different keyboards for the same language), the board is whatever height is left after a legend row, a message box, the script chips and 16 dp paddings (tiles range from 18 dp on a 360×640 Uzbek phone to 64 dp), and the "suggest to add" action and the round result resize the board when they appear. Players on different Android diagonals effectively get different games, and feedback jumps the layout mid-typing.

Visual authority: `docs/ui-review/game-layout.html` (sections 01 "now" and 02–04 "proposal"; the numbers there come from the same formula the implementation will use).

## What Changes

- **Keyboard structure fixed per language, independent of viewport width.** Every language renders its letter rows plus one action row: ENTER at the leading edge and ⌫ at the trailing edge (each three letter-widths), with any configured action-row graphemes between them. Uzbek-Latin's `oʻ` `gʻ` move from their own two-key row into the action row, so all five launch languages have four rows and the same keyboard height. The width-dependent inline/fallback logic from `improve-game-screen` §3 is removed (**supersedes** that change's "actions within the letter rows"; its board cap and active-cell indicator stay).
- **Keyboard sizing from the viewport.** Full-bleed on phones (4 dp side padding, 2 dp key gaps); one letter-key width per layout, from the longest row, capped at 56 dp; key labels scale with key width; key height 48 dp, stepping down to 44 then 40 dp only when the board would otherwise fall below 40 / 36 dp tiles.
- **Vertical budget as a pure layout plan.** Top bar 44 dp (back, hard-mode control, the Uzbek script switch, help), board centered in the remainder (tiles up to 64 dp), a 28 dp status strip, keyboard slot of the planned height — computed from the viewport size, unit-tested over a device matrix.
- **Feedback in a fixed-height status strip.** "Not enough letters", "Not in word list" with the suggest action, hard-mode violations and the suggestion sent/failed confirmations all show in the strip between the board and the keyboard; showing or hiding them moves nothing.
- **Round result in the keyboard slot** at the keyboard's height, so the board stays where it was when the round ends.
- **Chrome trimmed.** The Uzbek script switch moves into the top bar; the compact legend leaves the game screen (it stays in the how-to dialog) and is added to the Settings mark-style section.
- `LanguageConfig` gains `actionRowKeys` (graphemes hosted in the action row, default empty); Uzbek-Latin's keyboard data becomes three letter rows plus `[oʻ, gʻ]`.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `game-keyboard`: the per-language layout gains a dedicated action row and a structure that does not depend on the viewport; key sizing rules (width from viewport, label scaling, height ladder) are specified.
- `game-board`: viewport adaptation becomes a defined budget (top bar, board, status strip, keyboard slot); transient feedback and the round result never move or resize the board or keyboard.
- `game-help`: the compact legend is no longer shown on the game screen itself; the legend is available from the how-to dialog and in the Settings mark-style section.

## Impact

- `sharedUI/.../feature/game/GameScreen.kt` — portrait/wide layout, status strip, `KeyboardView`, result slot; new `feature/game/GameLayout.kt` (pure plan) with `jvmTest/.../GameLayoutTest.kt`.
- `sharedData/.../lang/LanguageConfig.kt` (+ `actionRowKeys`), `LaunchLanguages.kt` (uz-latn rows), `LanguageConfigTest`. The field has a default and is read only by the client UI; backend and panel are unaffected.
- `sharedUI/.../feature/settings/SettingsScreen.kt` — legend in the mark-style section.
- Physical-keyboard mapping (`onPreviewKeyEvent`) includes the action-row graphemes. No ViewModel, state, scoring or persistence changes.
- `BoardScreenshotTest` gains phone-size frames; screenshot goldens (board, keyboard, semantics dump) shift — re-record from the CI artifact.
- `improve-game-screen`: tasks 3.1–3.3 are superseded by this change; 4.2 (goldens) is covered by this change's re-record.
