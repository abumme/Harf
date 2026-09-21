# game-board Specification

## Purpose
Game-board is the visual grid where guesses appear and feedback is read, rendering grapheme tiles with the pencil-mark styles in the active theme and script.

## Requirements

### Requirement: Grapheme-tile grid
The board SHALL render a grid of the puzzle's tile length (4–7) by 6 attempt rows, one tile per grapheme, with digraphs shown as a single tile.

#### Scenario: Digraph occupies one tile
- **WHEN** a guess containing a digraph grapheme is shown
- **THEN** that digraph fills exactly one tile

#### Scenario: Grid width matches puzzle length
- **WHEN** the daily puzzle is a given tile length
- **THEN** the board renders that many tile columns

### Requirement: Per-tile mark feedback distinguishable by shape
After a guess is scored, each tile SHALL show its feedback using the theme's pencil marks (loop=correct, underline=present, strike=absent), distinguishable by shape as well as color.

#### Scenario: Feedback marks render per state
- **WHEN** a scored guess row is displayed
- **THEN** each tile shows the mark matching its correct/present/absent state

#### Scenario: Readable without color
- **WHEN** feedback is shown in grayscale
- **THEN** correct, present, and absent remain distinguishable by mark shape

### Requirement: Board and keyboard adapt to the viewport
The game screen SHALL keep the board and the on-screen keyboard fully visible on any viewport, adapting its layout rather than clipping or scrolling the play area. When the viewport is tall it SHALL stack them vertically and scale the board to fit the available height; when the viewport is wide it SHALL place the board and keyboard side by side.

#### Scenario: Tall viewport fits both by scaling
- **WHEN** the game is shown on a tall/portrait viewport where the default tile size would overflow (small phone, large font scale)
- **THEN** the board tiles SHALL scale down so the board and the full keyboard are both visible without clipping

#### Scenario: Tall viewport with ample height keeps the standard size
- **WHEN** the game is shown on a tall viewport with enough height for the standard tile size
- **THEN** the board SHALL render at its standard size (unchanged from the non-adaptive layout)

#### Scenario: Wide viewport uses a side-by-side layout
- **WHEN** the game is shown on a wide viewport (landscape phone, desktop, web, or tablet in landscape)
- **THEN** the board and the on-screen keyboard SHALL be laid out side by side, each fully visible, using the available width

#### Scenario: Play area is never clipped or scrolled
- **WHEN** the game is shown on any supported viewport
- **THEN** neither the board nor the keyboard SHALL be cut off, and the play area SHALL NOT require scrolling to reach a row or a key
