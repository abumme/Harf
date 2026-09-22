# result-share Specification

## Purpose
Result-share turns a finished round into a shareable artifact — the emoji grid text that made the format viral — plus a share/copy action.

## Requirements

### Requirement: Emoji share-grid text
On round end the system SHALL produce a text emoji grid representing each guess row's feedback (one square per tile), sized to the puzzle's tile length, using non-Wordle color squares.

#### Scenario: Grid mirrors the played rows
- **WHEN** a round ends
- **THEN** the generated grid has one row per played attempt and one square per tile, matching each tile's feedback state

#### Scenario: Header line included
- **WHEN** the share text is generated
- **THEN** it includes an identifying header (game name, language, puzzle number, and score like N/6)

### Requirement: Share and copy actions
The result SHALL offer a share action and a copy action for the generated text.

#### Scenario: Copy places text on the clipboard
- **WHEN** the player taps copy
- **THEN** the share text is placed on the clipboard

#### Scenario: Share invokes the platform sheet
- **WHEN** the player taps share on a platform with a native share sheet
- **THEN** the platform share sheet opens with the share text

### Requirement: Shared results identify archive and hard mode accurately
The identifying header of a shared result SHALL distinguish an archive replay from today's official daily round and SHALL identify hard mode when used. Sharing an archive replay SHALL not imply a new official daily result.

#### Scenario: Archive replay share
- **WHEN** the player shares a completed archived puzzle
- **THEN** the header identifies it as an archive result and includes its original puzzle day or number

#### Scenario: Hard-mode share
- **WHEN** the player shares a completed hard-mode round
- **THEN** the header includes a hard-mode marker while the emoji grid still reflects the actual attempts

#### Scenario: Ordinary daily share
- **WHEN** the player shares a normal official daily round
- **THEN** the existing daily header and grid remain valid
