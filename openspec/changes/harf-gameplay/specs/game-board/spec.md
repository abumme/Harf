## Purpose

Game-board is the visual grid where guesses appear and feedback is read, rendering grapheme tiles with the pencil-mark styles in the active theme and script.

## ADDED Requirements

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
