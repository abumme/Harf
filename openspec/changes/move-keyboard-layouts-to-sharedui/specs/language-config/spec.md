## MODIFIED Requirements

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
