# Tasks

Visual authority: §03 of `docs/ui-review/before-after.html` (row radius 11.dp, status badges).

## 1. Fix open-day routing (P0)

- [ ] 1.1 Add `epochDay: Long?` to the `Game` route in `AppNavHost.kt` (null/absent = today); verify `Home`→`Game` still works with no epochDay.
- [ ] 1.2 Pass `epochDay` from `ArchiveScreen.onOpenPuzzle` through `Game(languageId, epochDay)`; verify the archive lambda no longer discards it.
- [ ] 1.3 Add `DailyPuzzleProvider.daily(script, epochDay)` that resolves an explicit day from the published calendar; `GameScreen(languageId, epochDay)` uses it (today when absent). Verify opening an archive day loads that day's answer, not today's, and marks the round `RoundKind.ARCHIVE`.
- [ ] 1.4 Add a game test: opening a past `epochDay` yields the puzzle assigned to that day; opening with no epochDay yields today. Verify `./gradlew :sharedUI:jvmTest` passes.

## 2. Rows with date + result

- [ ] 2.1 Render each archive row as a date + result row (won `N/6`, lost `X/6`, "не сыграно") from the result log, using the 11.dp row shape and status badges from the mockup; verify a played and an unplayed day render distinctly.
- [ ] 2.2 Localize the back control: add `action_back` (RU `Назад`, EN `Back`, UZ `Orqaga`), replace `Text("Back")`; adopt the shared `ScreenTopBar` with an accessible back name. Verify no literal English in RU/UZ builds.

## 3. Verification

- [ ] 3.1 Compile-check JVM → Android → Wasm/JS.
- [ ] 3.2 Manual: as a Founder owner open several past days across languages and confirm each shows its own word; re-record Archive goldens from CI.
