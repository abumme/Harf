# streaks Specification

## Purpose
Streaks turn the result log into the daily-habit signal — current and best consecutive-day solve counts — computed by replay so the value is always consistent with history.

## Requirements

### Requirement: Per-language streak computed by replaying the log
Current and best streak SHALL be computed per language by replaying the result log against the streak rule (consecutive solved days in that language's rollover timezone), not stored as a mutable counter. Each language has its own independent streak.

#### Scenario: Consecutive solves increase the streak
- **WHEN** the player solves the daily word for a language on consecutive days
- **THEN** that language's current streak equals the count of those consecutive solved days

#### Scenario: A missed or lost day breaks the streak
- **WHEN** a day is missed or lost between solves for a language
- **THEN** that language's current streak resets and its best streak retains the previous maximum

#### Scenario: Streaks are independent per language
- **WHEN** the player has different solve histories across two languages
- **THEN** each language's current and best streak reflect only that language's history

### Requirement: Best streak retained
Best streak SHALL be the maximum current streak ever reached, retained even after the current streak resets.

#### Scenario: Best persists after a reset
- **WHEN** the current streak resets to a lower value
- **THEN** best streak remains at the highest value previously achieved
