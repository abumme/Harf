# Spec Delta

## Purpose

Hard mode gives Founder owners a more constrained way to solve the same daily or archived puzzle by requiring every later guess to respect clues already revealed.

## ADDED Requirements

### Requirement: Hard mode preserves the puzzle and attempt limit
An owner of the Founder lifetime entitlement SHALL be able to select hard mode before starting a daily or archive playthrough. Hard mode SHALL use the same answer, tile count, and six-attempt limit as normal mode. The selected mode SHALL persist through app restart and SHALL NOT change after the first accepted guess.

#### Scenario: Same daily puzzle
- **WHEN** two players open the same language and day in normal and hard mode
- **THEN** they receive the same answer, tile count, and six-attempt limit

#### Scenario: Selection persists
- **WHEN** the player starts a hard-mode round, submits a guess, and restarts the app
- **THEN** the round resumes in hard mode with the submitted guess intact

#### Scenario: Mid-round mode switch is rejected
- **WHEN** a player with an accepted guess tries to change that round's mode
- **THEN** the existing mode and progress remain unchanged

### Requirement: Every guess respects accumulated revealed clues
After the first accepted guess, a hard-mode guess SHALL keep every previously correct grapheme in its confirmed position, include each previously present grapheme in at least the confirmed minimum quantity, and avoid positions where that grapheme was previously marked present. Requirements SHALL accumulate across all accepted guesses. An absent mark alone SHALL NOT prohibit use of its grapheme.

#### Scenario: Correct positions stay fixed
- **WHEN** a previous row marked a grapheme correct in a position and the next guess changes that position
- **THEN** the next guess is rejected by the hard-mode rule

#### Scenario: Present graphemes move
- **WHEN** a previous row marked a grapheme present in a position and the next guess omits it or repeats it in that position
- **THEN** the next guess is rejected by the hard-mode rule

#### Scenario: Repeated graphemes have a minimum count
- **WHEN** previous feedback confirmed two occurrences of the same grapheme
- **THEN** a later guess containing fewer than two occurrences is rejected

#### Scenario: Absent mark with a duplicate
- **WHEN** one copy of a repeated grapheme is marked absent but another copy is confirmed correct or present
- **THEN** the absent mark does not prohibit the confirmed copy from appearing in a later guess

### Requirement: Invalid hard-mode guesses do not spend attempts
A guess violating a hard-mode rule SHALL not be scored, recorded as a row, or counted as an attempt, and the player SHALL receive a localized explanation of the violated clue. Dictionary validation and the normal six-attempt win/loss rules SHALL still apply.

#### Scenario: Clue violation is explained
- **WHEN** a complete dictionary word violates a revealed clue
- **THEN** the player sees an explanation and the attempt count remains unchanged

#### Scenario: Valid guess is scored normally
- **WHEN** a complete dictionary word satisfies all accumulated clues
- **THEN** it is scored by the ordinary grapheme scoring rules and counts as one attempt

### Requirement: Script switching cannot discard hard-mode constraints
Switching between Uzbek Latin and Cyrillic SHALL preserve the same lexeme, the selected mode, accepted guesses, and their equivalent grapheme-level constraints.

#### Scenario: Uzbek script changes during hard mode
- **WHEN** a player changes Uzbek script after receiving feedback in hard mode
- **THEN** the corresponding constraints still apply to the next guess in the new script
