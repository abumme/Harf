## Why

Streaks and stats are the retention loop — the reason to open Harf every day. The offline MVP needs a local, trustworthy streak and personal statistics that survive process death and relaunch. Modeling it as an event log now (rather than a mutable counter) matches the product's event-sourced intent and keeps the door open for server-mirrored streaks and gifting later without a rewrite.

## What Changes

- Add a **local result log**: each finished daily round appends an immutable record (language, puzzle day, win/lose, attempts used) persisted locally and surviving process death.
- Add **event-sourced streak computation**: current and best streak derived by replaying the log per the streak rule (consecutive days solved), rather than a stored counter.
- Add **personal statistics**: games played, win rate, guess distribution, current/best streak — computed from the log.
- Add a **stats screen** presenting the above, reachable from the game/result/settings.
- Add **in-progress round persistence**: the current day's board/guesses survive app kill and relaunch so a round is never lost.
- Persist all of the above via **KSafe** (from `harf-foundation`) as `@Serializable` data — the log is a serializable list, streaks/stats are computed by replay. No separate database: the log volume is tiny (~one record per language per day).
- Compute **per-language streaks** (a separate streak per language, matching each language's rollover) rather than a single global streak.

Non-goals: server sync/mirroring, streak-at-risk push, streak gifting/repair events (all need the backend — post-launch); leaderboards/leagues.

## Capabilities

### New Capabilities
- `result-log`: an append-only local store of finished-round records that survives relaunch and process death.
- `streaks`: current and best streak computed by replaying the result log against a defined streak rule.
- `player-stats`: games played, win rate, and guess distribution derived from the result log.
- `round-persistence`: the in-progress daily round (board state, guesses) persists and restores across app kill/relaunch.

### Modified Capabilities
<!-- none -->

## Impact

- `sharedUI/.../data/stats/`: KSafe-backed result-log store (serializable list), record model, and a repository; Koin bindings.
- `sharedUI/.../feature/stats/`: stats MVI screen + per-language streak/distribution computation.
- `harf-gameplay` writes a record on round end and saves/restores in-progress state (integration point; small hook, no rule change).
- No new persistence dependency — reuses KSafe from `harf-foundation`.
- Depends on `harf-foundation` (DI, KSafe) and `harf-gameplay` (round lifecycle). Offline-only.
