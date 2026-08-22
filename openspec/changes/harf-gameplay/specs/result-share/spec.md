## Purpose

Result-share turns a finished round into a shareable artifact — the emoji grid text that made the format viral — plus a share/copy action.

## ADDED Requirements

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
