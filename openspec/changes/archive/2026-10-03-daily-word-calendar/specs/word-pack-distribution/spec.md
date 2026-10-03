## MODIFIED Requirements

### Requirement: Updates take effect from a future date
A published pack update SHALL NOT change the answer of any day up to and including tomorrow in the language's timezone, so that adopting the update never changes a day players have already seen, are playing, or may already hold offline for tomorrow. The pack's effective date SHALL be no earlier than the day after tomorrow in the language's timezone.

#### Scenario: Update does not rewrite history
- **WHEN** a new pack version is published
- **THEN** its schedule changes apply only to dates from the day after tomorrow in the language's timezone, leaving past days, today and tomorrow unchanged

#### Scenario: Late-night change spares tomorrow
- **WHEN** a calendar change is published at 23:59 in the language's timezone
- **THEN** the published pack's words for today and tomorrow are identical to the previous version's

## ADDED Requirements

### Requirement: Release builds bundle the published calendar
Each release build of the app SHALL include, for every launch language, a snapshot of the published pack's answers and schedule taken from the production server when the build is prepared. A device that has never synced a language SHALL use that snapshot — combined with the bundled guess dictionary — when it passes the same integrity checks as a fetched pack, and SHALL otherwise use the generated bundled baseline. A cached server pack SHALL always take precedence over the snapshot.

#### Scenario: Offline fresh install agrees with the server
- **WHEN** a release build whose snapshot was taken after a day's word was published is installed and opened offline on that day
- **THEN** the daily word matches the word the server published for that day

#### Scenario: Invalid snapshot falls back to the baseline
- **WHEN** the bundled snapshot for a language is missing or fails integrity validation
- **THEN** the app uses the generated bundled baseline for that language without an error

#### Scenario: Synced pack beats the snapshot
- **WHEN** a device has a valid cached server pack for a language
- **THEN** the app uses the cached pack instead of the bundled snapshot
