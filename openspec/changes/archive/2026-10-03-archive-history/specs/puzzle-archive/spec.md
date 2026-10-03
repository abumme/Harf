# puzzle-archive Specification

## MODIFIED Requirements

### Requirement: Published past puzzles are discoverable
An owner of the Founder lifetime entitlement SHALL be able to browse and open puzzles for each supported language from that language's first public puzzle day through its most recently completed day, using the same fixed language timezone as daily play. Today and future days SHALL NOT appear in the archive. Each browsable day SHALL be presented with its calendar date and the player's result state for that day (solved with the number of attempts used, lost, or not yet played), derived from the result log, rather than a bare day index. Selecting a day SHALL carry that day's identity through navigation so the opened puzzle is the one originally assigned to that language and day.

#### Scenario: Founder opens a past day
- **WHEN** a Founder owner selects a published day before the current day in that language
- **THEN** the archive opens the puzzle originally assigned to that language and day, not the current day's puzzle

#### Scenario: Today's puzzle stays in daily play
- **WHEN** the archive is viewed on a language's current calendar day
- **THEN** the latest archive entry is yesterday, and today's puzzle remains accessible through the free daily game

#### Scenario: Before publication is unavailable
- **WHEN** a requested day precedes the first public puzzle day for its language
- **THEN** the archive does not offer a playable puzzle for that day

#### Scenario: Rows show date and result state
- **WHEN** the archive list is shown for a language
- **THEN** each row displays that day's calendar date and its play state — solved with attempts used, lost, or not yet played — from the result log
