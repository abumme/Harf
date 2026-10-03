# word-packs Specification

## Purpose
Word-packs are the bundled, offline vocabulary — a curated answer list and a larger guess dictionary per language — that the daily puzzle and guess validation draw from without any network dependency.

## Requirements

### Requirement: Bundled offline word packs per language
The app SHALL ship a bundled answer list and guess dictionary for each launch language as app resources, and SHALL treat the active vocabulary as the freshest valid pack: a locally-cached, server-fetched pack when one is present and valid, otherwise the bundled pack. Both the cached and bundled packs SHALL be loadable without any network access at play time.

#### Scenario: Packs load offline
- **WHEN** the app loads a language's word pack with no network available and nothing cached
- **THEN** the bundled answer list and guess dictionary are available for play

#### Scenario: A cached server pack overrides the bundle
- **WHEN** a valid, newer server pack has been cached for a language
- **THEN** the app uses the cached pack's answers and guesses instead of the bundled ones, still without any network call at play time

### Requirement: Guess validation against the dictionary
The engine SHALL accept a guess only if it exists in the active language's guess dictionary (the dictionary includes the answer list).

#### Scenario: Valid word accepted
- **WHEN** a guess is present in the guess dictionary
- **THEN** it is accepted for scoring

#### Scenario: Non-word rejected
- **WHEN** a guess is not present in the guess dictionary
- **THEN** it is rejected as invalid and not scored

### Requirement: Packs consistent with language config
Every entry in a word pack SHALL tokenize under its language config and match a supported board length; answers SHALL be a subset of the guess dictionary.

#### Scenario: Pack integrity is verifiable
- **WHEN** a word pack is validated against its language config
- **THEN** every entry tokenizes successfully, every answer has a supported grapheme length, and every answer also appears in the guess dictionary

### Requirement: Fetched packs are validated before use
A server-fetched pack SHALL be applied only after it passes the same integrity checks as a bundled pack; a fetched pack that fails SHALL be discarded in favor of the last good pack (cached or bundled).

#### Scenario: Invalid fetched pack is rejected
- **WHEN** a fetched pack fails validation — an entry does not tokenize under the language config, an answer has an unsupported board length, or an answer is missing from the guess dictionary
- **THEN** the app SHALL keep using the previous valid pack and SHALL NOT switch to the invalid one

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

### Requirement: Pack resolution never blocks the UI
Resolving a language's word pack SHALL NOT run on the UI thread on platforms that have a separate background thread pool (Android, iOS, desktop). This covers reading the bundled files, decoding a cached or snapshot pack, tokenizing its entries and checking its integrity. The UI SHALL keep rendering frames while a pack is being resolved. Once a language's pack is resolved, later requests in the same app session SHALL return it without resolving it again, until a newer valid pack is adopted.

#### Scenario: Opening a game while its pack is unresolved
- **WHEN** the player opens a language's game and that language's pack has not been resolved yet in this app session
- **THEN** the pack is resolved off the UI thread, and the navigation transition into the game keeps animating without stalling while it is resolved

#### Scenario: A resolved pack is reused
- **WHEN** a language's pack has been resolved and the player opens that language's game again in the same session
- **THEN** the same pack is used without reading or tokenizing the pack again

#### Scenario: Played languages are ready before the player asks
- **WHEN** the app starts and the player has finished at least one round in some languages
- **THEN** the packs of those languages are resolved in the background while the player is still on Home. Languages the player has never played are not resolved until they are opened.

### Requirement: An adopted fetched pack is not rebuilt
When a fetched pack passes the integrity check, the app SHALL adopt the pack it built during that check. Neither the check nor the adoption SHALL run on the UI thread on platforms with a background thread pool. The next game in that language SHALL use the adopted pack without resolving it again. A fetched pack that fails the check SHALL leave the current pack in use, as before.

#### Scenario: Adopted pack is used without a rebuild
- **WHEN** a background sync fetches a newer pack for a language and it passes integrity
- **THEN** the pack is cached for later launches, and the next game in that language uses it immediately without tokenizing it again

#### Scenario: Rejected pack changes nothing
- **WHEN** a background sync fetches a pack that fails integrity
- **THEN** the language keeps its current resolved pack, and nothing is cached
