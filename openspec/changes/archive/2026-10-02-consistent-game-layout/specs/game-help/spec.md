# game-help Specification

## REMOVED Requirements

### Requirement: Always-available in-game mark legend
**Reason**: The inline legend costs ~48 dp of board height on every phone and is one of the elements that made the game screen's proportions depend on the device. After the first rounds it no longer earns its place next to the board.
**Migration**: The legend stays one tap away in the how-to dialog (Reopenable how-to) and becomes part of the Settings mark-style section (Mark legend in Settings).

## ADDED Requirements

### Requirement: Mark legend in Settings
The Settings screen's mark-style section SHALL show the legend mapping the three feedback marks to their meaning (on the spot / in the word / not in the word), rendered in the currently selected mark style and palette.

#### Scenario: Legend shown with the style choice
- **WHEN** the player opens Settings
- **THEN** the mark-style section shows the three-state legend in the active style

#### Scenario: Legend follows a style change
- **WHEN** the player selects a different mark style
- **THEN** the legend re-renders in the newly selected style
