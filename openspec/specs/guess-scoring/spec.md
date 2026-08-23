# guess-scoring Specification

## Purpose
Guess-scoring produces the per-tile correct/present/absent feedback that drives the whole game, computed at grapheme granularity so feedback matches how players read their language.

## Requirements

### Requirement: Per-grapheme feedback
The engine SHALL compare a guess and an answer of equal grapheme length and return, for each guess position, one of: correct (right grapheme, right position), present (right grapheme, wrong position), or absent.

#### Scenario: Exact match all correct
- **WHEN** a guess equals the answer grapheme-for-grapheme
- **THEN** every position is scored correct

#### Scenario: Grapheme-level present, not character-level
- **WHEN** a guess contains a digraph grapheme that appears elsewhere in the answer
- **THEN** that digraph is scored present as a whole, and is never matched by its individual characters

### Requirement: Duplicate-letter accounting
The engine SHALL honor duplicate handling: the number of present/correct marks for a repeated grapheme SHALL not exceed the count of that grapheme in the answer, with correct positions taking priority.

#### Scenario: More occurrences in guess than answer
- **WHEN** a guess repeats a grapheme more times than it occurs in the answer, with one in the correct position
- **THEN** the correct position is marked correct and the surplus occurrences are marked absent (not present)

### Requirement: Length and language validation
The engine SHALL reject scoring when guess and answer grapheme lengths differ, and SHALL score within a single language's grapheme set.

#### Scenario: Mismatched lengths rejected
- **WHEN** a guess and answer have different grapheme counts
- **THEN** scoring returns an error/invalid result rather than partial feedback

### Requirement: Script-agnostic result equivalence for Uzbek
For an Uzbek daily word rendered in both scripts, the engine SHALL score each script against its own grapheme decomposition, so competition by number of guesses remains fair even when tile counts differ between scripts.

#### Scenario: Same lexeme, two scripts, fair by guess count
- **WHEN** the same solved-in-N-guesses outcome is produced on the Latin board and on the Cyrillic board of one lexeme
- **THEN** both count as solving the same word in N guesses regardless of differing tile counts
