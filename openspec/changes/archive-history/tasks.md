# Tasks

Visual authority: §03 of `docs/ui-review/before-after.html` (row radius 11.dp, status badges).

## 1. Fix open-day routing (P0)

- [x] 1.1 Add `epochDay: Long?` to the `Game` route in `AppNavHost.kt` (null/absent = today); verify `Home`→`Game` still works with no epochDay.
- [x] 1.2 Pass `epochDay` from `ArchiveScreen.onOpenPuzzle` through `Game(languageId, epochDay)`; verify the archive lambda no longer discards it.
- [x] 1.3 Add `DailyPuzzleProvider.daily(script, epochDay)` that resolves an explicit day from the published calendar; `GameScreen(languageId, epochDay)` uses it (today when absent). Verify opening an archive day loads that day's answer, not today's, and marks the round `RoundKind.ARCHIVE`. — Done with the existing `DailyPuzzleProvider.historical`. An archive round keeps its in-progress state in `ArchiveRoundStore`, records finished playthroughs in `ArchiveHistoryManager` (never the result log, stats or Play Games), and offers Replay.
- [x] 1.4 Add a game test: opening a past `epochDay` yields the puzzle assigned to that day; opening with no epochDay yields today. Verify `./gradlew :sharedUI:jvmTest` passes.

## 2. Rows with date + result

- [ ] 2.1 Render each archive row as a date + result row (won `N/6`, lost `X/6`, "не сыграно") from the result log, using the 11.dp row shape and status badges from the mockup; verify a played and an unplayed day render distinctly.
- [ ] 2.2 Localize the back control: add `action_back` (RU `Назад`, EN `Back`, UZ `Orqaga`), replace `Text("Back")`; adopt the shared `ScreenTopBar` with an accessible back name. Verify no literal English in RU/UZ builds.

## 3. Verification

- [x] 3.1 Compile-check JVM → Android → Wasm/JS.
- [ ] 3.2 Manual: as a Founder owner open several past days across languages and confirm each shows its own word; re-record Archive goldens from CI.

## 4. Results (2026-10-01)

- Android emulator (Pixel 7, API 36, debug build with the RevenueCat Test Store key, Founder bought through the Test Store):
  - today's English word MUSIC lost on the daily; the archive's 30.09.2026 opened an empty board whose word was NIGHT;
  - rows show `dd.MM.yyyy` and Not played / Solved in N/6 / Out of tries; Replay starts a fresh board and the row shows the latest playthrough;
  - the daily round, the result log and Statistics (English: 1 played) are unchanged by archive play;
  - an archive round finished after the Activity was recreated mid-round (dark mode toggled) is still recorded.
- 2.1 is partly done: date and result per row, but not yet the mockup's 11.dp rows and status badges. 2.2 is partly done: the back button is localized (`action_back`), but `ScreenTopBar` is not adopted yet.
- Test Store setup: at first the `harf_founder` product was not attached to any entitlement in the RevenueCat Test Store, so a test purchase recorded a transaction but did not unlock Founder (`entitlements.all` was empty). Once the product was attached to the entitlement, a clean build (no override) was rechecked:
  - after `pm clear`, a Test Store purchase shows Founder as Owned and unlocks the archive and hard mode at once, and both stay unlocked after a restart;
  - earlier purchases unlock through Restore purchases.
- Open: an Uzbek day always opens in Latin with one round slot per script, the same as the daily, so progress made after switching to Cyrillic is not what reopens.
