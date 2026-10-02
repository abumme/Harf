## Context

See proposal.md — Why. The relevant current state in `GameScreen.kt`:

- Portrait layout is a `Column` with 12 dp spacing: help row (40) · script chips (36, uz) · board (`weight(1f)`, tile = min(64, fit width, fit height)) · feedback column (compact legend ~40 + 8 + 20 dp message box, + 48 dp when the suggest button appears) · keyboard. The root has 16 dp padding on all sides inside `safeDrawing`.
- `KeyboardView` picks its structure from width: actions inline (⌫ end of row 0, ⏎ end of the last row, 1.5 letter-widths) when the resulting key is ≥ 24 dp, else a separate centered `ENTER` `⌫` row. Key height is fixed 48, key width ≤ 56, gaps 4 / 8.
- The result view replaces the keyboard in the same column slot with no height reservation.
- `LanguageConfig.keyboard` is read only by the client UI (keyboard and the physical-key lookup) and `LanguageConfigTest`; backend and panel compile the type but never read the field.
- Constraints: `commonMain` only (no platform code), `app-shell` keeps the game screen exempt from scrolling, screenshot goldens are canonical as rendered on CI, Uzbek is the primary language.
- Visual authority: `docs/ui-review/game-layout.html` — its JS `planAfter`/`planWide` is the reference formula; the Kotlin plan must reproduce its numbers.

## Goals / Non-Goals

**Goals:**
- One keyboard structure per language on every viewport; one keyboard height for all launch languages.
- Board and keyboard proportions derived from a single, unit-testable budget.
- Zero layout motion from feedback, the suggest action, the hard-mode control hiding, the script switch, or the round ending.

**Non-Goals:**
- The richer result moment (streak pill, inline grid, countdown) — `result-moment`; it inherits the result slot.
- Changing scoring, hard-mode rules, suggestion semantics, or any persisted data.
- Dark/other palettes, mark styles, or the help dialog content.
- Tablet-specific two-pane designs beyond the existing side-by-side rule.

## Decisions

1. **Dedicated action row for every language (over inline actions, or inline with a width fallback).** Chosen by the product owner for consistency. It is the only arrangement whose structure is identical across widths and across the Uzbek script switch, and it gives every launch language four rows → one keyboard height (210 dp at 48 dp keys). Cost: ~54 dp more than a 3-row inline keyboard; recovered by removing the legend row, the chips row and the 16 dp paddings (net board gain on a 360×640 phone: tiles 18 → 40 dp for uz-latn). Inline placements were rejected: top-right ⌫ narrows every key in the longest row and matches neither Gboard/iOS nor Wordle; bottom-row placements make uz-cyrl's 11-key row 14 units wide.
2. **`oʻ` `gʻ` hosted in the action row via data (`LanguageConfig.actionRowKeys`)** rather than a UI heuristic on "short last row". Explicit data keeps the keyboard data-driven (spec `game-keyboard`), is trivially testable, and leaves the three letter rows at 9 keys — the widest keys of any language (37 dp on a 360 dp phone). Alternative — folding `oʻ gʻ` into row 3 (11 keys, 30 dp) — rejected: narrower keys for the primary language with no height benefit. The field defaults to empty, so the server and panel need no change.
3. **Pure layout plan `GameLayout.plan(...)` in `feature/game/GameLayout.kt`** (no Compose types beyond `Dp`), returning tile size, key width, key height, keyboard height for a viewport. The composable only measures (`BoxWithConstraints`) and lays out from the plan. Rationale: the mockup and a `jvmTest` matrix can pin the numbers without rendering; the previous `BoxWithConstraints` arithmetic was spread across three lambdas and untestable.
   - Budget (portrait): `availH = H − top/bottom padding (8 + 8)`; chrome = top bar 44 + status strip 28 + 3 × 8 dp gaps; `kbH = rows × keyH + (rows − 1) × 6`; `tile = min(64, fitWidth(W − 32), fitHeight(availH − chrome − kbH))` with 6 dp tile gaps; ladder: keyH 48 → 44 when tile < 40 → 40 when tile < 36 (stop as soon as the threshold holds).
   - Keys: `keyW = min(56, (W − 2×4 − 2×(maxUnits − 1)) / maxUnits)`, `maxUnits = max(longest letter row, 6 + actionRowKeys.size)`; action key = `3 × keyW + 2 × 2`; label size = `(keyW × 0.48).sp` clamped to 12–16 sp.
   - Wide (W > H): half width = `(W − 2×16 − 16) / 2`; the left half gets top bar + board + strip, the right half the keyboard slot; `keyH` stays 48 (the keyboard no longer competes with the board for height) and the tile fits the left half's width and height.
4. **Status strip (fixed 28 dp) instead of a toast, snackbar, dialog or bottom sheet.** The strip is in the one place the message already lived, costs nothing on top of the gap it replaces, never covers board rows or keys, and makes "nothing moves" a layout property rather than an animation promise. A dialog or sheet would interrupt typing on every typo. One `StripMessage` state (text, optional action, sticky flag) replaces the separate `message` / `hardViolation` / `suggestCandidate` visibility logic; crossfade on change.
5. **Result slot = keyboard height, inner `verticalScroll`** rather than `animateContentSize`. Guarantees the board never moves at round end; today's `ResultView` (~200 dp) fits in 210/194/178 dp slots, and `result-moment` can scroll if it grows.
6. **Script switch in the top bar, back/help as 40 dp icon-sized controls.** Saves the chips row (36 + 12 dp) on Uzbek screens; with 40 dp controls and the short hard-mode label the row fits at 360 dp (and 320 dp in the mockup). Top bar is 44 dp because the segmented switch is ~42 dp tall.
7. **Legend moves to Settings' mark-style section** (reusing `MarkLegend`), spec `game-help` updated; the `?` dialog keeps goal + legend. The board gains ~48 dp on every phone.
8. **Keyboard is full-bleed (4 dp side padding) while the board keeps 16 dp.** Matches system keyboards and Wordle; the 12-key Cyrillic rows gain ~3 dp per key.

## Risks / Trade-offs

- [12-key Cyrillic rows give 24–27 dp keys on 360 dp phones] → full-bleed + 2 dp gaps (vs 23.7 dp today with the 4-row fallback), label scaling, and a `GameLayoutTest` floor of 21 dp at 320 dp; accepted by the product owner for structural consistency.
- [All screenshot goldens for the board/keyboard and the semantics dump shift] → re-record from the CI `roborazzi-goldens` artifact, review the diff, commit; never record locally.
- [`improve-game-screen` is unarchived and its `game-keyboard` delta contradicts this one] → this change's delta is written against the main spec and includes the still-valid parts (board cap, action keys without used-state); mark 3.1–3.3 superseded in its tasks and archive it after this change lands (its goldens task is subsumed).
- [Physical keyboard lookup built from `keyboard.flatten()` would miss action-row graphemes] → build it from letter rows + `actionRowKeys` (single-char graphemes only ever match, so behavior for `oʻ`/`gʻ` is unchanged: they remain on-screen keys).
- [Large system font scale] → tile and key labels are derived from dp sizes in sp, so they still scale; the ladder keeps the keyboard visible, and the strip truncates with an ellipsis rather than wrapping.
- [Inset variation (3-button navigation = 48 dp bottom)] → the plan works on the measured constraints inside `safeDrawing`; the ladder absorbs the difference.
- [Board centered in the remainder leaves a large gap above the strip on 20:9 phones] → accepted (Wordle does the same); the bias is a one-constant knob if it reads wrong on devices.

## Migration Plan

No persisted data or API changes. Ship with the next client release; the `actionRowKeys` default keeps older bundles and the server's `LanguageConfig` usage compatible. Rollback is a revert of the client commits.

## Open Questions

None that affect the specs or tasks.
