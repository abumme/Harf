## 1. Data model

- [ ] 1.1 Define `@Serializable ResultRecord` (language, puzzleDay, outcome, attempts) and the serialized log/in-progress shapes with a version field; verify serialization round-trips.

## 2. Result log

- [ ] 2.1 Implement a KSafe-backed `ResultLog` repository (append, list) with in-repo de-dup on (language, puzzleDay); verify tests: record survives re-open (KSafe), duplicate language-day ignored.
- [ ] 2.2 Add the gameplay hook: record on round end; verify a played round produces exactly one record.

## 3. Streaks & stats

- [ ] 3.1 Implement replay-based per-language current/best streak using each language's fixed-timezone day rule; verify tests: consecutive solves increase, missed/lost breaks, best retained, independent per-language streaks, midnight-boundary cases.
- [ ] 3.2 Implement stats (played, win rate, guess distribution) from the log; verify tests including the empty state.
- [ ] 3.3 Build the stats MVI screen (played/win-rate/distribution/streak) reachable from game/result/settings; verify preview + values.

## 4. Round persistence

- [ ] 4.1 Implement a versioned serializable in-progress round blob stored via KSafe (save on change, restore on open, clear on finish/new-day); verify tests: resume after kill, new-day clears, finished shows result not editable board.
- [ ] 4.2 Wire save/restore into `harf-gameplay`; verify manual kill-and-resume mid-round on desktop.

## 5. Integration

- [ ] 5.1 Multi-day simulation: record solves/misses across days and assert streak/stats; verify JVM test suite passes and stats screen reflects it.
