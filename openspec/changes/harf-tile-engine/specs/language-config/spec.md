## Purpose

Language-config makes each language a data definition — inventory, tokenizer rules, keyboard layout, script pairing, display — so adding or adjusting a language is a content change, not a code change.

## ADDED Requirements

### Requirement: Data-defined language configuration
Each supported language SHALL be defined as data providing: its grapheme inventory, tokenizer rules and exception list, an on-screen keyboard layout (rows of keys with digraphs as first-class keys), and display metadata (name, script label).

#### Scenario: Config drives tokenization and keyboard
- **WHEN** the engine loads a language config
- **THEN** tokenization uses that config's inventory/rules and the keyboard layout exposes that config's keys, including one key per digraph

#### Scenario: Adding a language needs no engine code change
- **WHEN** a new language config and its word pack are provided
- **THEN** the engine can tokenize, score, and expose a keyboard for it without modifying engine logic

### Requirement: Launch language set and orthography rules
The configuration SHALL cover the launch set — Uzbek-Latin, Uzbek-Cyrillic, Russian, English, Kazakh — encoding: tutuq words excluded at launch, Uzbek 1995 orthography with U+02BB canonical apostrophe, Russian `ё` unified with `е`, and Kazakh restricted to its native-word alphabet.

#### Scenario: Kazakh excludes Russian-loan-only letters
- **WHEN** the Kazakh keyboard and word set are produced
- **THEN** letters occurring essentially only in Russian loanwords are excluded from keys, answers, and guesses

#### Scenario: Configurable board length
- **WHEN** a puzzle specifies a tile length in the supported range (4–7, default 5)
- **THEN** the engine and its word buckets operate at that length

### Requirement: Uzbek script pairing as one lexeme
The Uzbek configuration SHALL model one lexeme with a per-script grapheme decomposition (Latin and Cyrillic), so a player can pick and switch script while playing the same daily word.

#### Scenario: One lexeme resolves in the chosen script
- **WHEN** a lexeme is requested in Latin or in Cyrillic
- **THEN** the engine returns that script's grapheme decomposition for the same underlying word
