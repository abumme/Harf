# Tasks

## 1. Board scale

- [x] 1.1 Raise the tile-size cap in `GameScreen.kt` `board(...)` from 46.dp to 64.dp; verify by running desktop/portrait and confirming the board fills more height with the keyboard still fully visible (no clip/scroll).
- [x] 1.2 Confirm tile text still scales (font derived from tile size) and the wide side-by-side layout is unchanged; verify visually at wide and tall viewports.

## 2. Active input cell

- [x] 2.1 Add an `active: Boolean` param to `Tile` and render the active treatment (accent border + subtle pulse) only when true; verify the treatment appears on an empty cell.
- [x] 2.2 In `BoardView`, mark the cell at index `state.current.size` of the in-progress row as active only while `status == Playing` and the row is not full; verify the indicator tracks input/delete and disappears on a full row and when the round ends.

## 3. Enter/Delete relocation (system-keyboard layout, "B")

> Superseded by change `consistent-game-layout`: enter/delete now live in a dedicated action row for every language (no inline placement, no width-dependent fallback). Tasks 3.1–3.3 describe the layout this change replaced; 4.2 is covered by that change's golden re-record.

- [x] 3.1 In `KeyboardView`, append the delete key to the top letter row and the enter key to the last letter row (trailing ends), removing the standalone `ActionCap` row; verify Latin and Cyrillic layouts render with actions inline and both actions fire.
- [x] 3.2 Add the overflow fallback: when appending an action to a row would exceed the available width (measure against `maxWidth`/`maxKeys`), keep a separate action row for that layout; verify the 12-key Uzbek-Cyrillic keyboard does not clip on a narrow phone width.
- [x] 3.3 Style the inline action keys (wider than a letter key, no used-state mark); verify they are visually distinct and the letter keys keep their sizing.

## 4. Verification

- [x] 4.1 Compile-check JVM then Android/Wasm; run `./gradlew :sharedUI:jvmTest` and confirm existing game tests pass.
- [x] 4.2 Re-record screenshot goldens from the CI `roborazzi-goldens` artifact (board + keyboard shifted) and commit them; verify `verifyRoborazziJvm` passes on CI.
