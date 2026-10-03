# game-board Specification

## MODIFIED Requirements

### Requirement: Board and keyboard adapt to the viewport
The game screen SHALL keep the board and the on-screen keyboard fully visible on any viewport, adapting its layout rather than clipping or scrolling the play area. When the viewport is tall it SHALL stack them vertically and scale the board to fit the available height; when the viewport is wide it SHALL place the board and keyboard side by side. On a tall viewport with room to spare the board SHALL grow to use the available space (up to a standard maximum tile size) rather than leaving a large empty margin.

#### Scenario: Tall viewport fits both by scaling
- **WHEN** the game is shown on a tall/portrait viewport where the default tile size would overflow (small phone, large font scale)
- **THEN** the board tiles SHALL scale down so the board and the full keyboard are both visible without clipping

#### Scenario: Tall viewport with ample height keeps the standard size
- **WHEN** the game is shown on a tall viewport with enough height for the standard tile size
- **THEN** the board SHALL render at its standard maximum tile size, growing to reduce the empty vertical margin, while keeping the full keyboard visible

#### Scenario: Wide viewport uses a side-by-side layout
- **WHEN** the game is shown on a wide viewport (landscape phone, desktop, web, or tablet in landscape)
- **THEN** the board and the on-screen keyboard SHALL be laid out side by side, each fully visible, using the available width

#### Scenario: Play area is never clipped or scrolled
- **WHEN** the game is shown on any supported viewport
- **THEN** neither the board nor the keyboard SHALL be cut off, and the play area SHALL NOT require scrolling to reach a row or a key

## ADDED Requirements

### Requirement: Active input cell indicator
While the round is playing, the board SHALL mark the cell that will receive the next entered grapheme — the first empty cell of the in-progress row — so the player can see where input lands. The indicator SHALL be shown only for the active cell and only while the game status is playing.

#### Scenario: Next empty cell is marked during play
- **WHEN** the round is playing and the in-progress row has one or more empty cells
- **THEN** the first empty cell of that row SHALL be visually distinguished from the other empty cells

#### Scenario: Indicator tracks input
- **WHEN** the player enters or deletes a grapheme in the in-progress row
- **THEN** the active-cell indicator SHALL move to the new first-empty cell

#### Scenario: No indicator when the round is over
- **WHEN** the game status is won or lost
- **THEN** no active-cell indicator SHALL be shown

#### Scenario: No indicator on a full row
- **WHEN** the in-progress row is completely filled (awaiting submit)
- **THEN** no active-cell indicator SHALL be shown
