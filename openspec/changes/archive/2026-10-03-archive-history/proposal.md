## Why

The Archive is the Founder paywall's headline feature — and it is **broken end to end**. `onOpenPuzzle` in `AppNavHost.kt:54` captures `epochDay` and then discards it, navigating `Game(languageId)`; the `Game` route has no `epochDay` field and `GameScreen` never accepts one. Every archive day opens **today's** puzzle. This directly violates the `puzzle-archive` requirement "the archive opens the puzzle originally assigned to that language and day." On top of the bug, each past day is a bare full-width `OutlinedButton` labeled only `«День N»` — no date, no whether you played or won — so even once fixed it is a flat, uninformative list. A hardcoded English `Text("Back")` (`ArchiveScreen.kt:124`) also leaks into the RU/UZ builds.

Target design: the **"After" column §03** of the before/after mockup — `docs/ui-review/before-after.html` (published: https://claude.ai/artifact/4ZkU31gB1xvWx7xViVhu7H). Its row shape (11.dp radius) and status treatment are the visual authority.

## What Changes

- **Fix the open-day routing (P0).** Thread `epochDay` from the archive row through the navigation route into the game: `Game(languageId, epochDay)` (default = today for normal play) → `GameScreen(languageId, epochDay)` → `DailyPuzzleProvider.daily(script, epochDay)`. Opening an archive day loads that day's puzzle, with archive replay progress kept distinct from the official daily (existing `RoundKind.ARCHIVE`).
- **Rows that say something.** Each archive row shows the real date and its play state (won `N/6`, lost `X/6`, or "не сыграно"), reading from the existing result log, using the shared row shape (11.dp) and status badges from the mockup.
- **Localize + shared top bar.** Replace `Text("Back")` with a string resource; adopt the shared `ScreenTopBar` (from `ui-visual-pass`) with an accessible back control; `archive_day_label` "День %1$d" is superseded by the date+status row.

Non-goals: entitlement/gating logic (unchanged), archive scheduling/calendar (unchanged), the general button system (defined in `ui-visual-pass`).

## Capabilities

### Modified Capabilities
- `puzzle-archive`: add a requirement that each browsable past day is presented with its calendar date and the player's result/played state for that day; the existing "opens the originally assigned puzzle" requirement is reaffirmed (the routing must carry the selected day).

## Impact

- `sharedUI/.../navigation/AppNavHost.kt`: `Game` route gains `epochDay`; archive `onOpenPuzzle` passes it.
- `sharedUI/.../feature/game/GameScreen.kt`: accepts an optional `epochDay` (today when absent).
- `sharedUI/.../feature/daily/DailyPuzzleProvider.kt`: `daily(script, epochDay)` overload for an explicit day.
- `sharedUI/.../feature/archive/ArchiveScreen.kt`: date+status rows, shared top bar, localized back.
- Strings: add `action_back`; the row now renders a formatted date + result rather than `archive_day_label`.
- Depends on `ui-visual-pass` for `ScreenTopBar` and the row shape token (can also be done inline if sequenced first).
