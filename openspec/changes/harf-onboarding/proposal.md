## Why

Harf currently drops a new player straight onto a blank board with no explanation: nothing says it's one hidden word a day, that you have six tries, or what the pencil marks (circle / underline / strike) mean. Experienced Wordle players cope; newcomers — the "finally a game in our language" audience we're chasing in Uzbek/Kazakh — are left guessing the rules, not just the word. Guessing the word blind is the game and stays; guessing the *rules* is a fixable onboarding gap (onboarding was in the MVP scope in `docs/02-mvp-scope.md` but no change implemented it).

## What Changes

- Add a **first-run intro** shown once before the first round: what Harf is (one word a day, 6 tries), and the feedback legend (on the spot / in the word / not in the word) using the active mark style. Skippable; a persisted flag (via `AppSettings`) prevents it reappearing.
- Add an **in-game mark legend**: a compact always-visible legend on the Game screen mapping the three marks to their meaning, distinguishable by shape (matches the active `MarkStyle`).
- Add a **help affordance** on the Game screen (a "?" that reopens the legend / how-to) so the rules are reachable any time, not only at first run.
- Keep it minimal and non-blocking: no multi-step wizard, no account, no forced tutorial round — read-and-play.

Non-goals: language/script selection (already handled by the Home buttons and the in-game script toggle), notification opt-in (push isn't built), interactive practice/tutorial round, and any hint about the answer itself (the word stays blind by design).

## Capabilities

### New Capabilities
- `onboarding`: a one-time first-run introduction (what the game is + the feedback legend) shown before the first round and not shown again once seen or skipped.
- `game-help`: an always-available in-game mark legend plus a help affordance that reopens the how-to, so the rules are discoverable during play.

### Modified Capabilities
<!-- none — additive; the daily round behavior is unchanged -->

## Impact

- `sharedUI/.../feature/onboarding/`: first-run intro screen/flow + a persisted "seen" flag in `AppSettings`.
- `sharedUI/.../feature/game/`: add a mark legend row + a help affordance to the Game screen (small additions; no change to play-flow or scoring).
- Navigation/App: gate the first-run intro before Home/Game on first launch.
- Depends on `harf-foundation` (settings, theme, MVI, navigation) and consumes the `MarkStyle`/feedback tokens (from cell-styles) for the legend. Offline-only.
