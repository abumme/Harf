## Purpose

Game-help keeps the rules within reach during play — a compact legend always on the board and a way to reopen the how-to — so a player never has to remember what a mark meant.

## ADDED Requirements

### Requirement: Always-available in-game mark legend
The Game screen SHALL show a compact legend mapping the three feedback marks to their meaning (on the spot / in the word / not in the word), rendered in the active mark style.

#### Scenario: Legend present during a round
- **WHEN** the player is on the Game screen
- **THEN** a legend for the three feedback states is visible without leaving the screen

#### Scenario: Legend tracks the active style
- **WHEN** the active mark style changes
- **THEN** the in-game legend re-renders in that style

### Requirement: Reopenable how-to
The Game screen SHALL offer a help affordance that reopens the how-to (goal + legend) at any time during play.

#### Scenario: Help reopens the how-to
- **WHEN** the player activates the help affordance
- **THEN** the how-to (daily-word goal + feedback legend) is shown and can be dismissed back to the round in progress
