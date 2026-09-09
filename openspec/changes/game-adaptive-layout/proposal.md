## Why

The game screen is the one screen the scrollable-screens change deliberately left as fixed-fit — and it overflows on any viewport that isn't a tall phone. Tiles are a hard `46.dp` and the whole screen is one vertical `Column`, so on landscape phones the keyboard is pushed off the bottom (seen on a real device), and the same clip hits small phones and large font scales. Because Harf is Compose Multiplatform, **desktop and web windows are inherently wide/short** and already render this vertical stack — so a wide/short layout is required regardless of mobile orientation, not optional.

## What Changes

- Make `GameScreen` adapt to its viewport (hybrid, chosen by aspect at the root via `BoxWithConstraints`):
  - **Tall / portrait** — keep the vertical stack, but size tiles to fit: `tileSize = min(46.dp, widthFit, heightFit)` so board + keyboard always fit; tile text scales with tile size. Capped at `46.dp` so tall phones look exactly as today.
  - **Wide / landscape / desktop / web** — lay board and keyboard **side by side** (`Row { board | keyboard }`), each using its half; the board scales to its column height. Finished state shows the result view in the keyboard column.
- Both board and on-screen keyboard remain fully visible on every viewport — no clipping, no scrolling of the play area.
- Scope is `GameScreen` only: board/tile sizing, keyboard placement, and the aspect branch. Engine, ViewModel, navigation, and the physical-keyboard input path are untouched.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `game-board`: add a requirement that the game screen adapts the board and on-screen keyboard to the viewport (fit-scaled vertical layout when tall; side-by-side when wide) so both are always fully visible without clipping.

## Impact

- **sharedUI** (`feature/game/GameScreen.kt`): root `BoxWithConstraints` + tall/wide branches; `Tile` gains a size parameter; `BoardView` takes a computed tile size; keyboard reused as-is (already width-adaptive).
- **Tests**: `BoardScreenshotTest` should be unchanged in portrait (tile capped at 46 at the test viewport); add a landscape/side-by-side screenshot test.
- **Not touched**: `engine/`, `GameViewModel`, navigation, `game-keyboard` internals, physical-keyboard handling.
- No new dependencies; the `adaptive` skill (MediaQuery / window-size) informs the approach.
