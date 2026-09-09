## Context

See proposal.md — Why. All five feature screens root on `Column(Modifier.fillMaxSize()…)` with a `verticalArrangement` and no scroll. `HomeScreen` additionally centers with `Arrangement.spacedBy(12.dp, Alignment.CenterVertically)`. Screens already apply `windowInsetsPadding(WindowInsets.safeDrawing)`.

## Goals / Non-Goals

- **Goals**: make Home, Settings, Stats, Paywall reachable on any viewport; preserve Home's centered look when content fits.
- **Non-Goals**: the Game screen (board+keyboard fixed-fit — needs an adaptive/scaled layout for landscape and tiny screens, tracked separately); horizontal scrolling; pull-to-refresh; nested scroll containers; redesigning any screen's content.

## Decisions

- **Top-aligned screens get a one-liner.** Settings, Stats, Paywall keep their `verticalArrangement = spacedBy(n)` and just add `Modifier.verticalScroll(rememberScrollState())` to the root `Column`. Order matters: apply scroll after `fillMaxSize()` and the inset padding so the padded region scrolls correctly.

- **Home uses the center-when-fits idiom** rather than plain scroll, because naive `verticalScroll` + `Arrangement.Center` top-aligns (a scroll container wraps content tightly, so centering has no room to act):
  ```kotlin
  BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(
      Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
      verticalArrangement = Arrangement.Center,
    ) { … }
  }
  ```
  `heightIn(min = maxHeight)` makes the column at least a viewport tall, so `Center` centers within it when content fits; when content exceeds the viewport the column grows past `maxHeight` and the scroll takes over. Insets stay applied inside.
  - *Alternative rejected*: always top-aligned scroll — simpler but loses Home's deliberate vertical centering on tall screens.

- **Game excluded by nature, not omission.** Scrolling a live board/keyboard is a UX regression; both must be on-screen. Its short-viewport handling is an adaptive-layout problem (scale the board, or side-by-side in landscape) and belongs to a separate change; the spec states the exemption so this isn't read as a gap.

## Risks / Trade-offs

- **Roborazzi goldens** render at a fixed viewport; when content fits, output is identical, so goldens should not change. If the wrapping shifts a pixel, re-record. Low risk.
- **Nested scroll**: none of these screens contain an inner scrollable today, so adding a root scroll introduces no nested-scroll conflict. (If Stats later gains a LazyColumn, revisit.)
- **`heightIn(min)` + scroll** is a well-worn Compose idiom; the only subtlety is keeping inset padding inside the scrolling column so padded edges scroll.

## Migration Plan

Pure client UI change; ship in the app. No migration, no rollback data. Revert = remove the modifiers.
