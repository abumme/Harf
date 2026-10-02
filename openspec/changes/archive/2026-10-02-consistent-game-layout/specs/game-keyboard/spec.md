# game-keyboard Specification

## MODIFIED Requirements

### Requirement: Data-driven per-language layout
The keyboard SHALL render from the active language's configured layout: its letter rows, one key per grapheme including a dedicated key for each digraph, followed by exactly one action row. The action row SHALL hold the enter action at its leading edge and the delete action at its trailing edge, with the language's configured action-row graphemes (if any) as ordinary keys between them. The row structure of a language's keyboard — the number of rows, the keys in each row and their order — SHALL NOT depend on the viewport size.

#### Scenario: Digraph has its own key
- **WHEN** the Uzbek-Latin keyboard is shown
- **THEN** `sh`, `ch`, `ng`, `oʻ`, `gʻ` each have their own key and are entered as a single grapheme, with `oʻ` and `gʻ` placed in the action row between enter and delete

#### Scenario: Layout matches the active language
- **WHEN** the active language changes
- **THEN** the keyboard renders that language's key set

#### Scenario: Action row is always present
- **WHEN** any language's keyboard is shown
- **THEN** its last row is the action row with enter leading and delete trailing, and no letter row contains an action key

#### Scenario: Structure is independent of the viewport
- **WHEN** the same language's keyboard is shown on viewports of different width (for example 360 dp and 412 dp wide)
- **THEN** both show the same rows with the same keys in the same order, differing only in key size

### Requirement: Per-key used-state feedback
Keys SHALL reflect the best known state of their grapheme from prior guesses (correct/present/absent), using the theme marks. Graphemes placed in the action row SHALL carry used-state feedback like any letter key; the enter and delete action keys SHALL NOT.

#### Scenario: Key shows best-known state
- **WHEN** a grapheme has been guessed and scored
- **THEN** its key shows the mark for its best-known state and does not downgrade on a later worse result

#### Scenario: Action-row grapheme shows its state
- **WHEN** an Uzbek-Latin guess containing `oʻ` has been scored
- **THEN** the `oʻ` key in the action row shows the mark for its best-known state

## ADDED Requirements

### Requirement: Key size follows the viewport
All letter keys of a layout SHALL share one width, chosen so the layout's longest row spans the keyboard's available width (the viewport width minus small side padding and inter-key gaps) without clipping, and capped at a standard maximum so keys do not grow oversized on wide windows. Action keys (enter, delete) SHALL be three letter-widths wide. Key labels SHALL scale with the key width within a readable range. Key height SHALL be a standard touch height (48 dp) and SHALL be reduced in steps (to 44 dp, then 40 dp) only when the board would otherwise fall below its minimum tile size; it SHALL never be reduced below 40 dp.

#### Scenario: Narrow phone keys span the width
- **WHEN** the keyboard is shown on a narrow phone (for example 360 dp wide)
- **THEN** the longest row spans the keyboard width with no key clipped, all letter keys have the same width, and the action row's enter and delete keys are at the keyboard's edges

#### Scenario: Wide window caps the key size
- **WHEN** the keyboard is shown in a wide window (desktop, web, tablet)
- **THEN** keys do not exceed the maximum key width and the keyboard is centered in its area

#### Scenario: Short viewport lowers key height before shrinking the board further
- **WHEN** the viewport is too short to give 48 dp keys and a board of at least the minimum tile size
- **THEN** the key height steps down (44 dp, then 40 dp) before the board shrinks below that minimum, and never below 40 dp

#### Scenario: Script switch keeps the keyboard height
- **WHEN** the player switches Uzbek between Latin and Cyrillic during a round
- **THEN** the keyboard occupies the same height before and after the switch
