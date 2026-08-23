# grapheme-tokenization Specification

## Purpose
Grapheme-tokenization turns a written word into the ordered sequence of linguistic letters a native speaker counts, so digraphs like `sh` or `oʻ` occupy a single tile and word length is measured in tiles, not Unicode characters.

## Requirements

### Requirement: Longest-match grapheme decomposition
The engine SHALL decompose a word into an ordered list of graphemes for a given language using longest-match rules, so multi-character letters defined by the language are treated as one grapheme.

#### Scenario: Uzbek Latin digraphs count as one tile
- **WHEN** the Uzbek-Latin word `shahar` is tokenized
- **THEN** the result is `[sh, a, h, a, r]` — 5 graphemes, not 6 characters

#### Scenario: Single-character languages are 1:1
- **WHEN** a Russian, English, Kazakh, or Uzbek-Cyrillic word is tokenized
- **THEN** each character maps to exactly one grapheme

#### Scenario: Longest match wins over prefixes
- **WHEN** a word contains a sequence where a longer grapheme and a shorter grapheme both start (e.g. `ng` vs `n`)
- **THEN** the longer defined grapheme is chosen

### Requirement: Exception list overrides tokenization
The engine SHALL support a per-language exception list so words where a default-matched digraph is actually two separate letters across a morpheme boundary tokenize correctly.

#### Scenario: Exception splits a false digraph
- **WHEN** a word is listed in the language's exception list with an explicit grapheme breakdown
- **THEN** tokenization returns that explicit breakdown instead of the default longest-match result

### Requirement: Input normalization before tokenization
The engine SHALL normalize known character variants to the language's canonical form before tokenizing (e.g. Uzbek apostrophe variants to U+02BB; Russian `ё` treated as `е`).

#### Scenario: Apostrophe variants normalize
- **WHEN** an Uzbek-Latin word uses a non-canonical apostrophe variant for `oʻ`/`gʻ`
- **THEN** it is normalized to the canonical form and tokenizes to the same graphemes as the canonical spelling
