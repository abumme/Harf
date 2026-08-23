## Why

With the foundation and tile engine in place, Harf needs its actual playable surface: the daily puzzle loop players open every day. This is the core of the offline MVP — a board, a custom on-screen keyboard, the daily word, and a shareable result. Without it there is no game to ship.

## What Changes

- Add the **daily puzzle** selection: one deterministic word per language per day, chosen from the bundled answer packs, with a per-language fixed-timezone rollover (Asia/Tashkent for uz/en, Asia/Almaty for kk, Europe/Moscow for ru). Fully offline and deterministic (no server).
- Add the **game board**: a dynamic grid (4–7 tiles wide, 6 rows) rendering guesses with per-tile pencil-mark feedback (loop=correct, underline=present, strike=absent) from the theme tokens.
- Add the **custom on-screen keyboard**: data-driven layout per language from `language-config`, digraphs as first-class keys, per-key used-state marks.
- Add **input & play flow**: type/delete/submit, guess validation against the guess dictionary, invalid-guess feedback, win/lose resolution within 6 attempts.
- Add **Uzbek script switching** in-play: pick and switch Latin/Cyrillic; the board, keyboard, and word render in the chosen script for the same daily lexeme.
- Add the **result / share**: a win/lose end state producing the emoji share-grid text (color squares) plus a copy/share action. (No deep links or attribution — that is post-launch/backend.)

Non-goals: streaks/stats persistence (that is `harf-streak-stats`), the 3 selectable cell-mark styles + A/B (`harf-cell-styles`), monetization, leagues/gifting, deep links.

## Capabilities

### New Capabilities
- `daily-puzzle`: deterministic per-language daily word selection with fixed-timezone rollover, offline.
- `game-board`: render a guess grid with grapheme tiles and per-tile mark feedback for the active theme and script.
- `game-keyboard`: a data-driven on-screen keyboard per language with digraph keys and per-key used-state feedback.
- `play-flow`: the interactive round — input, guess validation, scoring integration, win/lose resolution, and Uzbek script switching.
- `result-share`: end-of-round result with emoji share-grid text and a share/copy action.

### Modified Capabilities
<!-- none -->

## Impact

- `sharedUI/.../feature/game/`: board, keyboard, and play-flow ViewModel/State/Action/Event (MVI from foundation), consuming the engine (`grapheme-tokenization`, `guess-scoring`, `language-config`, `word-packs`).
- `sharedUI/.../feature/daily/`: daily-word selection + rollover clock.
- `sharedUI/.../feature/result/`: result screen + share-grid text builder; share action via an `expect/actual` share hook (android/ios real, desktop/web best-effort).
- Depends on `harf-foundation` and `harf-tile-engine`. No new third-party dependencies; uses kotlinx-datetime if not already present for timezone rollover (add to catalog if needed).
