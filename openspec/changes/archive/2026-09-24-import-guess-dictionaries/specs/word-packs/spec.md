## ADDED Requirements

### Requirement: Guess dictionary coverage
Each language's guess dictionary SHALL contain the real words of that language at the board lengths its puzzles use, including both dictionary forms and their inflected forms, and SHALL exclude proper nouns, abbreviations, misspellings, words that are only vulgar or offensive, and every word on the language's blocklist. An ordinary word that also has a vulgar, offensive or derogatory sense SHALL remain a valid guess unless it is blocklisted. A regenerated dictionary SHALL keep every word the previous dictionary accepted and every answer.

#### Scenario: Inflected form is a valid guess
- **WHEN** a player guesses an inflected form of an ordinary word at the puzzle length (for example a plural or case form)
- **THEN** the guess SHALL be accepted as a word in the dictionary

#### Scenario: Proper noun is not a valid guess
- **WHEN** a player guesses a personal or place name that is not also an ordinary word of the language
- **THEN** the guess SHALL be rejected as not in the dictionary

#### Scenario: Offensive and blocklisted words are excluded
- **WHEN** every sense of a word is vulgar or offensive, or the word appears on the language's blocklist
- **THEN** it SHALL NOT be in the guess dictionary

#### Scenario: Ordinary word with a rude secondary sense stays valid
- **WHEN** an ordinary word also has a vulgar, offensive or derogatory sense (for example "собака", dog)
- **THEN** it SHALL be a valid guess unless it is on the language's blocklist

#### Scenario: Regeneration never removes accepted words
- **WHEN** a guess dictionary is regenerated from updated sources
- **THEN** every word the previous dictionary contained and every answer SHALL still be in it

#### Scenario: Both Uzbek scripts offer comparable coverage
- **WHEN** an ordinary Uzbek word is a valid guess in the Latin script and its Cyrillic spelling fits the board length
- **THEN** its Cyrillic spelling SHALL be a valid guess in the Uzbek Cyrillic dictionary

### Requirement: Dictionary sources are credited
Word lists distributed with the app or its server packs SHALL credit their sources as those sources' licenses require.

#### Scenario: Wiktionary-derived lists are attributed
- **WHEN** a guess dictionary derived from Wiktionary is distributed
- **THEN** the app's public terms and the repository's third-party notice SHALL credit Wiktionary contributors and name the CC BY-SA license
