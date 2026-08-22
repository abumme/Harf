## Purpose

Daily-puzzle gives every player the same one word per language per day, chosen deterministically and offline, with a culturally correct local-midnight rollover.

## ADDED Requirements

### Requirement: Deterministic daily word per language
The system SHALL select exactly one answer word per language for a given calendar day, deterministically, so all devices playing that language on that day get the same word without any network call.

#### Scenario: Same day same word
- **WHEN** two offline devices request the daily word for the same language and day
- **THEN** they receive the same word

#### Scenario: Different days differ
- **WHEN** the daily word is requested for consecutive days
- **THEN** the words differ across those days (no immediate repeat)

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
