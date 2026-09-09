## Why

None of the app's content screens scroll — every root is a plain `Column(fillMaxSize)` (a `grep verticalScroll` over `feature/` returns zero hits). When content is taller than the viewport it is silently clipped and becomes unreachable. This was found in landscape on a real device (Home's Settings/Stats buttons cut off), but the same clip hits small-height devices and large accessibility font sizes.

## What Changes

- Make the four content screens scrollable so all content is reachable on any viewport: **Settings, Stats, Paywall** (top-aligned, add `verticalScroll`), and **Home** (keep its vertical centering via a center-when-fits idiom).
- Home uses `BoxWithConstraints { Column(Modifier.verticalScroll(state).heightIn(min = maxHeight), verticalArrangement = Arrangement.Center) }` — centered when content fits, scrolls when it overflows, so the current look is preserved on tall screens.
- **Game is explicitly excluded.** Its board + keyboard must be visible together; scrolling a live game is wrong. Fixing Game on short/landscape viewports is a separate adaptive-layout concern (see Non-goals).
- Existing `windowInsetsPadding(WindowInsets.safeDrawing)` on each screen is kept.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `app-shell`: add a requirement that content screens keep all content reachable on any viewport (scroll when content exceeds the available height), with the fixed-fit game screen as the stated exception.

## Impact

- **sharedUI** (`feature/`): root layout of `HomeScreen`, `SettingsScreen`, `StatsScreen`, `PaywallScreen`.
- **Not touched**: `GameScreen` (separate adaptive concern), navigation, ViewModels, data.
- **Tests**: Roborazzi goldens for these screens should be unchanged when content fits the test viewport; re-record only if a diff appears.
- No behavior change beyond reachability; no new dependencies.
