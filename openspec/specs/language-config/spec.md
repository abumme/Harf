# language-config Specification

## Purpose
Language-config makes each language a data definition — inventory, tokenizer rules, keyboard layout, script pairing, display — so adding or adjusting a language is a content change, not a code change.

## Requirements

### Requirement: Data-defined language configuration
Each supported language SHALL be defined as data, in two parts keyed by the same language id:
- **Shared language data**, used alike by the app, the server, the staff panel and the word-list tools: its grapheme inventory, tokenizer rules and exception list, script pairing, and display metadata (name, script label).
- **An on-screen keyboard layout**, used by the app only: rows of keys with digraphs as first-class keys, plus the graphemes (if any) hosted in the keyboard's action row.

The shared language data SHALL NOT carry the keyboard layout, so changing a layout changes nothing the server, the staff panel or the word-list tools consume. Every launch language SHALL have exactly one keyboard layout; every key of a layout SHALL be a grapheme of that language's inventory, no action-row key SHALL repeat a letter-row key, and every digraph SHALL have a key.

#### Scenario: Config drives tokenization and keyboard
- **WHEN** the engine loads a language config
- **THEN** tokenization uses that config's inventory/rules, and the keyboard shows the layout registered for that config's language id, with one key per digraph

#### Scenario: Keyboard keys are the language's graphemes
- **WHEN** any launch language's keyboard layout is checked against its grapheme inventory
- **THEN** every letter-row and action-row key is a grapheme of that language, no action-row key is also a letter-row key, and every digraph has a key

#### Scenario: A layout edit stays in the app
- **WHEN** only a language's keyboard layout is changed
- **THEN** the shared language data is unchanged, and the server, the staff panel and the word-list tools see no change

#### Scenario: Adding a language needs no engine code change
- **WHEN** a new language's shared data, its keyboard layout and its word pack are provided
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
