# result-log Specification

## Purpose
Result-log is the append-only local record of every finished daily round — the single source the streak and stats are computed from — durable across relaunch and process death.

## Requirements

### Requirement: Append-only durable result records
On finishing a daily round the app SHALL append an immutable record (language, puzzle day, outcome win/lose, attempts used) to a local store that survives app relaunch and process death.

#### Scenario: Record survives relaunch
- **WHEN** a round finishes and the app is relaunched
- **THEN** that round's record is still present in the log

#### Scenario: Records are not mutated
- **WHEN** a new round finishes
- **THEN** it is appended and existing records are unchanged

### Requirement: One record per language-day
The log SHALL hold at most one finished record per (language, puzzle day); a day already recorded SHALL NOT be double-counted.

#### Scenario: No duplicate for the same day
- **WHEN** a finished round is recorded for a language-day that already has a record
- **THEN** the log does not gain a second record for that language-day
