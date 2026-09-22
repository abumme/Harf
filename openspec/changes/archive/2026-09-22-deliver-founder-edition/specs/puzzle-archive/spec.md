# Spec Delta

## Purpose

The puzzle archive lets Founder owners revisit every published daily puzzle while keeping replay progress and results distinct from the official daily competition.

## ADDED Requirements

### Requirement: Published past puzzles are discoverable
An owner of the Founder lifetime entitlement SHALL be able to browse and open puzzles for each supported language from that language's first public puzzle day through its most recently completed day, using the same fixed language timezone as daily play. Today and future days SHALL NOT appear in the archive.

#### Scenario: Founder opens a past day
- **WHEN** a Founder owner selects a published day before the current day in that language
- **THEN** the archive opens the puzzle originally assigned to that language and day

#### Scenario: Today's puzzle stays in daily play
- **WHEN** the archive is viewed on a language's current calendar day
- **THEN** the latest archive entry is yesterday, and today's puzzle remains accessible through the free daily game

#### Scenario: Before publication is unavailable
- **WHEN** a requested day precedes the first public puzzle day for its language
- **THEN** the archive does not offer a playable puzzle for that day

### Requirement: Historical answers remain stable
An archive puzzle SHALL resolve to the answer originally published for its language and day, including the paired Latin and Cyrillic forms of an Uzbek lexeme, regardless of later word-pack updates or the device used.

#### Scenario: Word pack changes after publication
- **WHEN** a player opens a past day after adopting a newer word-pack version
- **THEN** the answer matches the word published on that day

#### Scenario: Uzbek script choice
- **WHEN** the same Uzbek archive day is opened in Latin and Cyrillic
- **THEN** both script forms represent the same published lexeme

### Requirement: Archive rounds can be replayed
The player SHALL be able to start or resume an archive round and start another playthrough after completion. Each playthrough SHALL use the normal six-attempt game rules or, when selected before that playthrough, the owned hard mode.

#### Scenario: Resume an unfinished archive round
- **WHEN** the player reopens an unfinished archived day after restarting the app
- **THEN** its submitted rows, current input, and chosen mode are restored

#### Scenario: Replay a completed day
- **WHEN** the player chooses to replay a completed archive day
- **THEN** a new independent playthrough begins without erasing the completed playthrough from archive history

### Requirement: Archive progress is separate and account-synchronized
Archive rounds and results SHALL be stored separately from official daily rounds. Completed archive playthroughs SHALL synchronize across devices signed into the same Harf account. A completed archive playthrough SHALL NOT write an official daily result or affect ordinary statistics, streaks, or platform achievements. Offline completed playthroughs SHALL synchronize later without duplicates.

#### Scenario: Archive completion leaves daily progress unchanged
- **WHEN** an archive round is completed or replayed
- **THEN** the official daily result log, ordinary statistics, streaks, and platform achievements remain unchanged

#### Scenario: History appears on another device
- **WHEN** an account's archive playthrough completes on one device and a second device signs into that account
- **THEN** the second device can see that playthrough in its archive history after synchronization

#### Scenario: Offline result synchronizes once
- **WHEN** an archive playthrough is completed offline and connectivity later returns
- **THEN** it appears once in the account's archive history despite upload retries

### Requirement: Archive history is private to its account
The system SHALL expose or modify synchronized archive history only for the authenticated account that owns it. Local archive state from one account SHALL NOT be shown as another account's history, and confirmed account deletion SHALL remove the deleted account's synchronized archive history.

#### Scenario: Account switch isolates history
- **WHEN** a device switches from account A to account B
- **THEN** account A's archive history is not displayed or uploaded as account B's history

#### Scenario: Account deletion removes history
- **WHEN** the server confirms deletion of an account
- **THEN** its synchronized archive history is deleted with that account
