## MODIFIED Requirements

### Requirement: Deterministic daily word per language
The system SHALL select exactly one answer word per language for a given calendar day from the active date→word schedule — a cached server schedule when present, otherwise the bundled baseline schedule — deterministically and without any network call. Devices sharing the same schedule version SHALL get the same word for a given day; the bundled baseline is itself a valid shared version, so devices that have never synced still agree with one another.

#### Scenario: Same day same word
- **WHEN** two offline devices on the same schedule version request the daily word for the same language and day
- **THEN** they receive the same word

#### Scenario: Different days differ
- **WHEN** the daily word is requested for consecutive days
- **THEN** the words differ across those days (no immediate repeat)

#### Scenario: A vocabulary update never changes a past or current day
- **WHEN** a new schedule version is published and a device adopts it
- **THEN** the answer for any date before the update's effective date is unchanged from what a player already saw, and only dates on or after the effective date may differ
