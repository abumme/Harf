# daily-puzzle Specification

## Purpose
Daily-puzzle gives every player the same one word per language per day, chosen deterministically and offline, with a culturally correct local-midnight rollover.

## Requirements

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

### Requirement: Per-language fixed-timezone rollover
The daily word SHALL roll over at local midnight in a fixed per-language timezone (uz/en: Asia/Tashkent; kk: Asia/Almaty; ru: Europe/Moscow).

#### Scenario: Rollover at configured midnight
- **WHEN** the language's configured local time passes midnight
- **THEN** the daily word advances to the next day's word

### Requirement: Uzbek daily word is one lexeme across scripts
For Uzbek, the daily selection SHALL be a single lexeme resolvable in either script.

#### Scenario: Same daily lexeme in both scripts
- **WHEN** the Uzbek daily word is requested in Latin and in Cyrillic on the same day
- **THEN** both resolve to the same underlying lexeme
