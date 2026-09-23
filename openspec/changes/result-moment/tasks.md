# Tasks

Visual authority: §04 of `docs/ui-review/before-after.html` (streak pill fully rounded, inline grid, primary Share, countdown).

## 1. Result screen

- [ ] 1.1 In `ResultView`, show the current per-language streak (from `Streaks`) as the prominent pill element on a solved round; verify the value matches the Stats screen for the same state.
- [ ] 1.2 Render the feedback grid inline in `ResultView` (reuse the data behind `ShareGrid`, active-edition colors); verify it appears before the share action and matches the played rows.
- [ ] 1.3 Make Share the primary button and Copy secondary; add a next-word countdown to the daily rollover for that language (from `PuzzleDays`). Verify the countdown ticks and uses the language's timezone.

## 2. Reveal animation

- [ ] 2.1 Animate the just-submitted row's marks with a per-tile stagger in `BoardView`/`Tile`; verify marks reveal in sequence and input is accepted after completion.
- [ ] 2.2 Honor reduced-motion (platform setting): marks appear without stagger when reduced motion is requested; verify on a platform with the setting on.

## 3. Verification

- [ ] 3.1 Compile-check JVM → Android → Wasm/JS; run `./gradlew :sharedUI:jvmTest`.
- [ ] 3.2 Re-record result-state goldens from CI on a settled (post-animation) frame; verify `verifyRoborazziJvm` passes.
- [ ] 3.3 Manual: win a round on device and confirm streak, inline grid, staggered reveal, and countdown match §04.
