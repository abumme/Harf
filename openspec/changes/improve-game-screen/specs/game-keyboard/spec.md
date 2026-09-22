# game-keyboard Specification

## MODIFIED Requirements

### Requirement: Data-driven per-language layout
The keyboard SHALL render from the active language's configured layout, presenting one key per grapheme including a dedicated key for each digraph, plus enter and delete actions. The enter and delete actions SHALL be placed within the letter rows in a system-keyboard arrangement — delete at the trailing end of the top letter row and enter at the trailing end of the last letter row — rather than in a separate standalone action row. When a layout's rows are too wide to append an action key without overflowing the viewport (for example the 12-key Uzbek-Cyrillic rows on a narrow phone), that layout SHALL fall back to a separate action row so no key is clipped.

#### Scenario: Digraph has its own key
- **WHEN** the Uzbek-Latin keyboard is shown
- **THEN** `sh`, `ch`, `ng`, `oʻ`, `gʻ` each have their own key and are entered as a single grapheme

#### Scenario: Layout matches the active language
- **WHEN** the active language changes
- **THEN** the keyboard renders that language's key set

#### Scenario: Enter and delete sit within the letter rows
- **WHEN** a keyboard layout fits its rows plus the action keys within the viewport
- **THEN** the delete action SHALL sit at the trailing end of the top letter row and the enter action at the trailing end of the last letter row, with no standalone action row

#### Scenario: Narrow layout falls back to an action row
- **WHEN** appending an action key to a letter row would overflow the viewport width
- **THEN** the enter and delete actions SHALL be shown in a separate action row so no key is clipped

### Requirement: Per-key used-state feedback
Keys SHALL reflect the best known state of their grapheme from prior guesses (correct/present/absent), using the theme marks. The enter and delete action keys SHALL NOT carry used-state feedback.

#### Scenario: Key shows best-known state
- **WHEN** a grapheme has been guessed and scored
- **THEN** its key shows the mark for its best-known state and does not downgrade on a later worse result
