## Context

See proposal.md — Why. Current `GameScreen` is a single `Column(fillMaxSize)`: help row → optional Uzbek script chips → `BoardView` → `MarkLegend` → message box → optional suggest button → `KeyboardView` (or `ResultView` when finished). `Tile` is hard-coded `Modifier.size(46.dp)` with `20.sp` text. `KeyboardView` already sizes key width from `BoxWithConstraints` (`keyW = (maxWidth - gaps)/maxKeys`, capped 44.dp) but key height is fixed 42.dp. `scrollable-screens` intentionally left the game as the fixed-fit exception; this change is that exception's real fix.

## Goals / Non-Goals

- **Goals**: board + keyboard always fully visible on any viewport; tall = fit-scaled vertical; wide = side-by-side; portrait look on tall phones unchanged.
- **Non-Goals**: changing the engine/scoring/VM; orientation locking; a tablet list-detail or multi-pane nav; redesigning the keyboard's key styling; animating the layout transition; touching other screens (scrollable-screens already handled them).

## Decisions

- **Aspect at the root picks the layout.** Wrap the screen in `BoxWithConstraints`; `val wide = maxWidth > maxHeight`. Wide → side-by-side; else → vertical. Aspect (not a fixed dp width class) is the right signal because the question is literally "is there room to put the keyboard beside the board." Covers phone-landscape, desktop, web, and tablet-landscape uniformly.
  - *Alternative rejected*: WindowSizeClass width buckets — more machinery; a tall narrow desktop window would wrongly go side-by-side.

- **Tile size is derived, capped at 46.dp.** `tileSize = min(46.dp, widthFit, heightFit)` where `widthFit = (availW - gaps)/tileCount` and `heightFit = (availBoardH - gaps)/maxAttempts`; `availBoardH` = remaining height after chrome + keyboard in the vertical layout, or the column height in side-by-side. Text scales with the tile (e.g. `fontSize ∝ tileSize`). The cap keeps tall phones pixel-identical to today — which also keeps the portrait screenshot golden stable.
  - *Alternative rejected*: leave 46 fixed and only switch layouts — small portrait phones and large-font a11y would still clip.

- **`Tile(size: Dp)` / `BoardView(tileSize: Dp)`** — thread the computed size down instead of the hard-coded constant. Single primitive both layouts reuse.

- **Keyboard reused as-is.** It already adapts width; in side-by-side it gets its column's width, in vertical the full width. In very short landscape the side-by-side column gives it full height, so its fixed key height fits. No keyboard change expected; revisit only if a target still clips.

- **Wide finished-state** puts `ResultView` in the keyboard column (board stays left). Keeps a consistent two-column frame between playing and finished.

## Risks / Trade-offs

- **Screenshot goldens**: `BoardScreenshotTest` renders portrait at a fixed size; with the 46 cap the tile stays 46 there, so the golden should not change. If a rounding difference appears, re-record. A new landscape test should be recorded fresh for the side-by-side layout.
- **Two layout branches** double the visual surface to eye-check; mitigated by one shared tile primitive and a landscape screenshot test.
- **Chrome placement in wide mode** (help/chips/legend/message/suggest) needs a home — likely stacked in the board column above/below the board; a fiddly bit, not a risk to correctness.
- **Very small square-ish windows** (e.g. tiny desktop resize) — aspect ≈ 1; either branch works; fit-scaling keeps it valid.

## Migration Plan

Pure client UI; ship in the app. Revert = restore the single Column + fixed tile size. No data/persistence impact.

## Open Questions

- Exact wide-mode placement of the secondary chrome (legend / message / suggest button) — a layout detail resolvable at implementation without changing the approach or the spec.
