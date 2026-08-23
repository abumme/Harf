# play-flow Specification

## Purpose
Play-flow is the interactive round: entering guesses, validating them, scoring, and resolving win or loss — including switching Uzbek script mid-play.

## Requirements

### Requirement: Guess entry and editing
The player SHALL be able to enter graphemes up to the puzzle length, delete the last grapheme, and submit a completed guess.

#### Scenario: Type and delete
- **WHEN** the player taps grapheme keys and then delete
- **THEN** the current row fills and the last grapheme is removed on delete

#### Scenario: Submit requires full length
- **WHEN** the player submits a row shorter than the puzzle length
- **THEN** the guess is not accepted and the player is prompted to complete it

### Requirement: Guess validation
A submitted guess SHALL be accepted only if it exists in the active language's guess dictionary; otherwise it is rejected with feedback and does not consume an attempt.

#### Scenario: Invalid word rejected without cost
- **WHEN** the player submits a word not in the guess dictionary
- **THEN** the guess is rejected, feedback is shown, and the attempt count is unchanged

### Requirement: Win/lose resolution
The round SHALL end in a win when a guess is all-correct, or in a loss after the sixth incorrect attempt, revealing the answer on loss.

#### Scenario: Win on correct guess
- **WHEN** a submitted valid guess is all-correct
- **THEN** the round ends as a win on that attempt

#### Scenario: Loss after six attempts
- **WHEN** the sixth attempt is scored and is not all-correct
- **THEN** the round ends as a loss and the answer is revealed

### Requirement: Uzbek script switching in play
For Uzbek, the player SHALL be able to pick and switch script (Latin/Cyrillic); the board, keyboard, and word render in the chosen script for the same daily lexeme.

#### Scenario: Switch script preserves the lexeme
- **WHEN** the player switches Uzbek script during a round
- **THEN** the board and keyboard re-render in the new script for the same daily word
