# game-help Specification

## Purpose

Game-help keeps the rules within reach during play — a compact legend always on the board and a way to reopen the how-to — so a player never has to remember what a mark meant.

## Requirements

### Requirement: Reopenable how-to
The Game screen SHALL offer a help affordance that reopens the how-to (goal + legend) at any time during play.

#### Scenario: Help reopens the how-to
- **WHEN** the player activates the help affordance
- **THEN** the how-to (daily-word goal + feedback legend) is shown and can be dismissed back to the round in progress

### Requirement: Mark legend in Settings
The Settings screen's mark-style section SHALL show the legend mapping the three feedback marks to their meaning (on the spot / in the word / not in the word), rendered in the currently selected mark style and palette.

#### Scenario: Legend shown with the style choice
- **WHEN** the player opens Settings
- **THEN** the mark-style section shows the three-state legend in the active style

#### Scenario: Legend follows a style change
- **WHEN** the player selects a different mark style
- **THEN** the legend re-renders in the newly selected style
