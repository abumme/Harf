# game-keyboard Specification

## Purpose
Game-keyboard is the custom on-screen keyboard that makes grapheme input unambiguous, laid out per language with digraphs as first-class keys.

## Requirements

### Requirement: Data-driven per-language layout
The keyboard SHALL render from the active language's configured layout, presenting one key per grapheme including a dedicated key for each digraph, plus enter and delete actions.

#### Scenario: Digraph has its own key
- **WHEN** the Uzbek-Latin keyboard is shown
- **THEN** `sh`, `ch`, `ng`, `oʻ`, `gʻ` each have their own key and are entered as a single grapheme

#### Scenario: Layout matches the active language
- **WHEN** the active language changes
- **THEN** the keyboard renders that language's key set

### Requirement: Per-key used-state feedback
Keys SHALL reflect the best known state of their grapheme from prior guesses (correct/present/absent), using the theme marks.

#### Scenario: Key shows best-known state
- **WHEN** a grapheme has been guessed and scored
- **THEN** its key shows the mark for its best-known state and does not downgrade on a later worse result
