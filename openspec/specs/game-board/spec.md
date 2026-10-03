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
The game screen SHALL keep the board and the on-screen keyboard fully visible on any viewport, adapting its layout rather than clipping or scrolling the play area. On a tall viewport the screen SHALL stack, top to bottom: a top bar (back, hard-mode control, the Uzbek script switch when applicable, help), the board centered in the remaining height, a fixed-height status strip, and the keyboard anchored at the bottom. The board's tile size SHALL be derived from the height left by that fixed chrome and the keyboard: scaled down on short viewports and growing up to a standard maximum tile size on tall ones rather than leaving a large empty margin. On a wide viewport the board (with its top bar and status strip) and the keyboard SHALL be placed side by side. The chrome's height SHALL be constant for the whole round, so the board's size and position do not change while playing.

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

#### Scenario: Chrome stays constant during a round
- **WHEN** the hard-mode control disappears after the first guess, a feedback message appears or disappears, or the Uzbek script is switched
- **THEN** the board's tile size and position SHALL NOT change

### Requirement: Feedback never moves the board or keyboard
Transient feedback — not enough letters, not in word list (with the suggest-to-add action), a hard-mode violation, and the suggestion sent/failed confirmations — SHALL be shown in a status strip of fixed height between the board and the keyboard, so showing or hiding feedback never changes the size or position of any other element. The strip SHALL show at most one message at a time. Messages SHALL auto-dismiss after a short interval, except the not-in-word-list message with its suggest action, which SHALL stay until the guess is edited or the suggestion is sent.

#### Scenario: Rejected guess shows in the strip
- **WHEN** a guess is rejected (incomplete, unknown word, or hard-mode violation)
- **THEN** the reason is shown in the status strip and the board and keyboard keep their size and position

#### Scenario: Suggest action lives in the strip
- **WHEN** a full-length guess is not in the word list
- **THEN** the strip shows the message and the suggest-to-add action in the same line; activating the action sends the suggestion and the strip then shows the sent (or failed) confirmation in place

#### Scenario: Editing clears the offer
- **WHEN** the player changes the rejected guess
- **THEN** the not-in-word-list message and its suggest action disappear from the strip

### Requirement: Round result keeps the board in place
When the round ends, the result (outcome, revealed answer on a loss, share actions) SHALL be shown in the keyboard's slot at the keyboard's height, so the board neither moves nor resizes. Result content taller than the slot SHALL scroll within the slot rather than push the board.

#### Scenario: Round ends
- **WHEN** the round is won or lost
- **THEN** the result replaces the keyboard within the same bounds and the board keeps its size and position

#### Scenario: Finished round is reopened
- **WHEN** a finished round is restored on relaunch
- **THEN** the board and the result slot are laid out exactly as at the moment the round ended

### Requirement: The game page is present from the first frame
The game screen SHALL draw its page (paper background across the whole window, safe-area padding, and its header with the back control) from the first frame it is composed, including while today's round is still loading. It SHALL NOT render as an empty or transparent page. When the round finishes loading, the board and keyboard SHALL appear inside that page without shifting the page's header.

#### Scenario: Game page slides in while loading
- **WHEN** the player opens a game and the round is not ready by the first frame of the transition
- **THEN** the incoming page already shows the game's background and header, and the board and keyboard appear in place once the round is ready

#### Scenario: Back works while loading
- **WHEN** the player presses the game's back control before the round has finished loading
- **THEN** the app returns to the previous destination

### Requirement: The game keeps its loaded round across returns
Once a round is loaded, it SHALL stay loaded for as long as the game destination remains in the back stack. Returning to the game from a destination opened on top of it (such as the paywall) SHALL show the board immediately, with the player's current input intact. When the player switches Uzbek script, the current board SHALL stay visible until the other script's round is ready, and SHALL then be replaced by it.

#### Scenario: Returning from the paywall
- **WHEN** the player opens the paywall from the game and then goes back
- **THEN** the game shows its board immediately, with the same guesses and current input as before, and without a loading frame

#### Scenario: Switching Uzbek script keeps the board until ready
- **WHEN** the player switches between Latin and Cyrillic during an Uzbek game
- **THEN** the board does not disappear while the other script's round is loading. The other script's board replaces it once ready.
