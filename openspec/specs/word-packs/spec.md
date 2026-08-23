# word-packs Specification

## Purpose
Word-packs are the bundled, offline vocabulary — a curated answer list and a larger guess dictionary per language — that the daily puzzle and guess validation draw from without any network dependency.

## Requirements

### Requirement: Bundled offline word packs per language
The app SHALL ship an answer list and a guess dictionary for each launch language as app resources, loadable without any network access.

#### Scenario: Packs load offline
- **WHEN** the app loads a language's word pack with no network available
- **THEN** the answer list and guess dictionary are available for play

### Requirement: Guess validation against the dictionary
The engine SHALL accept a guess only if it exists in the active language's guess dictionary (the dictionary includes the answer list).

#### Scenario: Valid word accepted
- **WHEN** a guess is present in the guess dictionary
- **THEN** it is accepted for scoring

#### Scenario: Non-word rejected
- **WHEN** a guess is not present in the guess dictionary
- **THEN** it is rejected as invalid and not scored

### Requirement: Packs consistent with language config
Every entry in a word pack SHALL tokenize under its language config and match a supported board length; answers SHALL be a subset of the guess dictionary.

#### Scenario: Pack integrity is verifiable
- **WHEN** a word pack is validated against its language config
- **THEN** every entry tokenizes successfully, every answer has a supported grapheme length, and every answer also appears in the guess dictionary
