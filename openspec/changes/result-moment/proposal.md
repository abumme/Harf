## Why

The payoff of a daily word game is the moment it ends — and Harf's is a single 18sp line (`«Решено за 3/6»`) plus Share/Copy (`GameScreen.kt:591–603`). The streak (🔥) that actually drives return visits is buried in a Stats screen nobody opens mid-round; the emoji share grid is computed but invisible until after you share; and marks appear instantly on submit — the app's one signature motion, Wordle's staggered tile flip, is absent. The end screen should feel like the peak it is.

Target design: §04 of the before/after mockup — `docs/ui-review/before-after.html` (published: https://claude.ai/artifact/4ZkU31gB1xvWx7xViVhu7H). Streak pill (fully rounded), inline grid, primary Share, next-word countdown.

## What Changes

- **Surface the streak at round end.** On a solved round the result screen shows the current per-language streak (already computed by `Streaks`) as the loudest element — the pill from the mockup — instead of hiding it in Stats.
- **Render the share grid inline.** Show the emoji/color grid (already built for `ShareGrid`) on the result screen before the Share action, so the win is visible in-app. Share stays the primary button; Copy is secondary.
- **Staggered mark-reveal animation.** On submit, the row's marks reveal with a per-tile stagger (flip or scale) at grapheme granularity, respecting `prefers-reduced-motion` / the platform reduced-motion setting.
- **Next-word countdown** on the result screen closes the loop with a reason to return (time to the next daily rollover in that language's timezone, from the existing `PuzzleDays`).

Non-goals: changing streak/stats computation, share text format, or scoring; the loss screen keeps its honest tone (reveal the answer) and gains only the same reveal animation and countdown.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `result-share`: the finished-round result screen renders the share grid visually (not only as shareable text) before the share action.
- `streaks`: the current streak is presented on the result screen at round end, not only in the statistics surface.
- `game-board`: marks reveal with a staggered per-tile animation on submit, honoring reduced-motion.

## Impact

- `sharedUI/.../feature/game/GameScreen.kt`: `ResultView` gains streak, inline grid, primary Share, countdown; `BoardView`/`Tile` gain the reveal animation on the just-submitted row.
- Reads existing `Streaks`, `ShareGrid`, `PuzzleDays`; no new data.
- Screenshot goldens for the result state shift — re-record from CI. Animation is time-based; keep goldens on a settled frame.
- Depends on `ui-visual-pass` for the pill/primary-button styles (or style inline if sequenced first).
