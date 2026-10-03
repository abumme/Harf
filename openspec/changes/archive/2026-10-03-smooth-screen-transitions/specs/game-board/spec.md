## ADDED Requirements

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
