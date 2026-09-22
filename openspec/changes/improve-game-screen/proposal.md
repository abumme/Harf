## Why

The game screen wastes vertical space (tiles capped at 46.dp leave a large empty halo on tall phones), the Enter/Delete actions sit in an orphan centered row that breaks system-keyboard muscle memory, and nothing marks which cell the next grapheme lands in. Fixing all three makes the core surface read like a real keyboard and use the screen it is given.

## What Changes

- **Raise the tile-size cap** from 46.dp to 64.dp so the board grows to fill available space on tall/portrait viewports, reducing the empty halo. Text already scales with tile size; the wide side-by-side layout is unaffected.
- **Move Enter/Delete into the keyboard body, system-keyboard style** (layout "B"): the delete key sits at the right end of the top letter row, the enter key at the bottom-right corner of the last letter row. The standalone centered action row is removed.
  - **Fallback**: when appending an action key to a row would overflow the viewport width (e.g. the 12-key Uzbek-Cyrillic rows on a narrow phone), keep a separate action row for that layout so no key clips.
- **Show the active input cell**: the next empty cell of the in-progress row is marked with an accent border plus a subtle pulse while the round is playing.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `game-board`: raise the tile-scaling cap; add an active-cell indicator requirement.
- `game-keyboard`: enter/delete relocated into the letter rows (system-keyboard layout) with a per-language overflow fallback.

## Impact

- `sharedUI/.../feature/game/GameScreen.kt` only — `board(...)` sizing, `KeyboardView`/`KeyCap`/`ActionCap`, `BoardView`/`Tile`. No ViewModel/state change (`state.current.size` already gives the active index).
- Screenshot goldens (`*ScreenshotTest`, `BoardScreenshotTest`, `SemanticsDumpTest`) will shift — re-record from CI artifact.
- Physical-keyboard mapping (`onPreviewKeyEvent`) unchanged.
