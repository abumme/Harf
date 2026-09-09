## 1. Tile sizing primitive

- [ ] 1.1 Add a `size: Dp` parameter to `Tile` (replace the hard `46.dp`) and scale its text with the size; thread a `tileSize: Dp` through `BoardView`. Default paths still render at 46 where space allows.
- [ ] 1.2 Compute `tileSize = min(46.dp, widthFit, heightFit)` from available space; verify a small/short viewport shrinks tiles and a tall one keeps 46.

## 2. Root adaptive branch

- [ ] 2.1 Wrap `GameScreen`'s content in `BoxWithConstraints`; derive `wide = maxWidth > maxHeight`.
- [ ] 2.2 **Tall branch**: keep the vertical stack, passing the fit-computed `tileSize`; confirm board + full keyboard fit with no clipping on a short portrait viewport.
- [ ] 2.3 **Wide branch**: `Row { board column | keyboard column }`, board centered and scaled to its column height, keyboard filling its column; place help/chips/legend/message/suggest in the board column; finished state renders `ResultView` in the keyboard column.

## 3. Verification

- [ ] 3.1 Compile JVM + Android; `:sharedUI:jvmTest` green.
- [ ] 3.2 Roborazzi: `verifyRoborazziJvm` — portrait `BoardScreenshotTest` unchanged (tile capped at 46). Add a landscape/side-by-side screenshot test and `recordRoborazziJvm` its golden.
- [ ] 3.3 Device/emulator: landscape phone shows board + keyboard side by side, both fully usable; portrait unchanged; a small/short viewport fit-scales without clipping. Desktop/web wide window shows side-by-side.
- [ ] 3.4 `openspec validate game-adaptive-layout --strict` passes.
