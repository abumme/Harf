## Purpose

Round-persistence makes an in-progress daily round survivable — the board and guesses come back after an app kill so a player never loses today's progress.

## ADDED Requirements

### Requirement: In-progress round persists and restores
The current day's in-progress round (submitted guesses and their feedback, current input, active script) SHALL persist locally and restore on relaunch until the round is finished.

#### Scenario: Resume after app kill
- **WHEN** the app is killed mid-round and reopened on the same day
- **THEN** the board restores the previously submitted guesses and feedback

#### Scenario: New day clears prior in-progress state
- **WHEN** the daily word rolls over to a new day
- **THEN** a fresh round starts and stale in-progress state from a prior day is not shown

#### Scenario: Finished round is not re-entered
- **WHEN** the player has finished today's round and reopens the app
- **THEN** the finished result is shown rather than an editable board
