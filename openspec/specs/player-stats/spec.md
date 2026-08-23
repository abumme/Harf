# player-stats Specification

## Purpose
Player-stats summarizes the result log into the numbers players check — games played, win rate, and guess distribution — and presents them.

## Requirements

### Requirement: Statistics derived from the log
The app SHALL compute games played, win rate, and a guess distribution (wins bucketed by attempts used) from the result log.

#### Scenario: Stats reflect recorded rounds
- **WHEN** the log contains finished rounds
- **THEN** games played, win rate, and the guess distribution match those records

#### Scenario: Empty state
- **WHEN** no rounds have been recorded
- **THEN** stats show a defined empty/zero state rather than an error

### Requirement: Stats screen
The app SHALL present a stats screen showing games played, win rate, guess distribution, and current/best streak, reachable from game/result/settings.

#### Scenario: Stats screen displays current values
- **WHEN** the player opens the stats screen
- **THEN** it shows the current played, win-rate, distribution, and streak values
