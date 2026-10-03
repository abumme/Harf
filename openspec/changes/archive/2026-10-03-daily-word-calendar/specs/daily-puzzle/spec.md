## MODIFIED Requirements

### Requirement: Deterministic daily word per language
The system SHALL select exactly one answer word per language for a given calendar day from the active published calendar — the schedule of a cached server pack when present, otherwise the calendar snapshot bundled with the app build when valid, otherwise the bundled baseline schedule — deterministically and without any network call at play time. Devices sharing the same pack version SHALL get the same word for a given day; the bundled snapshot and the bundled baseline are themselves valid shared versions, so devices of the same build that have never synced still agree with one another.

#### Scenario: Same day same word
- **WHEN** two offline devices on the same schedule version request the daily word for the same language and day
- **THEN** they receive the same word

#### Scenario: Different days differ
- **WHEN** the daily word is requested for consecutive days and the language's answer pool has more than one eligible word
- **THEN** the words differ across those days (no immediate repeat)

#### Scenario: Words do not repeat while unused words remain
- **WHEN** the daily words of a language's published calendar are compared across days counted since the public launch
- **THEN** no word appears on two of those days unless that language had no never-used eligible word left when the later day was filled

#### Scenario: A vocabulary update never changes a past or current day
- **WHEN** a new pack version is published and a device adopts it
- **THEN** the answer for yesterday or any earlier date, for today and for tomorrow in the language's timezone is unchanged from the previous version, and only later dates may differ

#### Scenario: Never-synced device uses the build's snapshot
- **WHEN** a device has never synced a pack for a language and its app build includes a valid calendar snapshot covering today
- **THEN** today's word is the snapshot's word for today, not the generated baseline's

## ADDED Requirements

### Requirement: Fresh install waits briefly for the published calendar
When the app resolves today's word for a language that has no cached server pack, it SHALL wait for that language's first pack sync of the session for at most 2 seconds, then resolve from the cached pack if the sync succeeded, otherwise from the bundled pack. The wait SHALL NOT surface an error or block play beyond the bound, and SHALL NOT happen when a cached pack already exists.

#### Scenario: First sync finishes within the wait
- **WHEN** a fresh install opens today's round online and the first pack sync completes within 2 seconds
- **THEN** today's word is taken from the freshly cached pack

#### Scenario: Offline fresh install falls back silently
- **WHEN** a fresh install opens today's round with no network
- **THEN** today's word is resolved from the bundled pack within 2 seconds and no error is shown

#### Scenario: Cached pack means no wait
- **WHEN** a language already has a cached server pack
- **THEN** today's word is resolved from it without waiting for a sync
