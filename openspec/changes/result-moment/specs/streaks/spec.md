# streaks Specification

## ADDED Requirements

### Requirement: Streak is surfaced on the result screen
When a round is solved, the result screen SHALL present the player's current streak for that language as a prominent element, using the streak value computed by the streak rule. The streak SHALL be shown at the moment of the win, not only within the statistics surface.

#### Scenario: Solved round shows the current streak
- **WHEN** the player solves the daily word for a language
- **THEN** the result screen displays that language's current streak prominently

#### Scenario: Streak value matches the computed rule
- **WHEN** the streak is shown on the result screen
- **THEN** its value equals the current streak computed by replaying the result log, consistent with the statistics surface
