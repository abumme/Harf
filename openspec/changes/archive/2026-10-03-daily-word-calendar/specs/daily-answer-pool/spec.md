## Purpose

The daily answer pool is the ADMIN-curated set of words eligible to become a language's word of the day, kept separate from the full guess dictionary and published to players as the pack's answers.

## ADDED Requirements

### Requirement: Only ADMINs manage the answer pool
The system SHALL let only ADMINs view or change daily eligibility. Every answer-pool request from a WORDER SHALL be refused as forbidden and change nothing, the panel SHALL NOT show WORDERs any answer-pool navigation or page, and no response given to a WORDER SHALL reveal whether a word is daily-eligible.

#### Scenario: WORDER is refused
- **WHEN** a WORDER requests the answer pool of any calendar or tries to mark or unmark a word
- **THEN** the system SHALL respond forbidden and SHALL NOT change eligibility

#### Scenario: WORDER sees no answer-pool entry
- **WHEN** a WORDER is signed in to the panel
- **THEN** the navigation SHALL NOT contain an answer-pool or calendar section

#### Scenario: ADMIN manages every calendar
- **WHEN** an ADMIN opens the answer pool
- **THEN** the system SHALL offer the `en`, `ru`, `kk` and `uz` calendars

### Requirement: Marking words as daily-eligible
An ADMIN SHALL be able to mark active catalog words of a calendar's language as daily-eligible, one word at a time or in bulk from a pasted list. A word SHALL become eligible only if it is an active catalog word of that language whose grapheme length is a supported board length. A bulk request SHALL report an outcome for every entry — marked, already eligible, not in the catalog, removed, or unsupported length — and SHALL mark every valid entry even when other entries fail.

#### Scenario: Active word is marked
- **WHEN** an ADMIN marks an active `en` catalog word of a supported board length
- **THEN** the word SHALL be daily-eligible for the `en` calendar

#### Scenario: Unknown or removed word is refused
- **WHEN** an ADMIN marks a word that is not in the catalog, or whose catalog entry is removed
- **THEN** the system SHALL refuse that word with a reason and SHALL NOT make it eligible

#### Scenario: Unsupported length is refused
- **WHEN** an ADMIN marks a catalog word whose grapheme length is outside the language's supported board lengths
- **THEN** the system SHALL refuse that word with a reason

#### Scenario: Bulk marking reports each entry
- **WHEN** an ADMIN submits a list containing an eligible-to-be word, an already eligible word and an unknown word
- **THEN** the first SHALL be marked, and the response SHALL report "marked", "already eligible" and "not in the catalog" for the respective entries

### Requirement: Unmarking daily eligibility
An ADMIN SHALL be able to remove a word from the answer pool. An unmarked word SHALL no longer be picked for any unlocked day, and unmarking SHALL NOT change any past, current or next day.

#### Scenario: Unmarked word leaves the pool
- **WHEN** an ADMIN unmarks an eligible word
- **THEN** the word SHALL no longer be eligible and SHALL NOT be offered for manual or automatic picks

#### Scenario: Unmarking keeps locked days
- **WHEN** an ADMIN unmarks a word that is today's or tomorrow's daily word
- **THEN** today's and tomorrow's daily words SHALL be unchanged

### Requirement: Uzbek eligibility by lexeme pair
For the `uz` calendar, eligibility SHALL belong to a lexeme pair: one active Uzbek Latin catalog word and one active Uzbek Cyrillic catalog word for the same lexeme, each of a supported board length in its own script. When an ADMIN starts a pair from a Latin word, the system SHALL suggest the Cyrillic spelling by transliteration, and the ADMIN SHALL confirm or correct it before the pair is created. When the confirmed Cyrillic spelling is not an active catalog word, the ADMIN SHALL be able to add it to the catalog as part of creating the pair, subject to the catalog's validation. A word SHALL belong to at most one pair, and removing a pair SHALL remove its eligibility.

#### Scenario: Cyrillic spelling is suggested
- **WHEN** an ADMIN starts a pair from the Uzbek Latin word `shahar`
- **THEN** the system SHALL suggest `шаҳар` as the Cyrillic spelling for the ADMIN to confirm

#### Scenario: Pair with a missing Cyrillic word
- **WHEN** the ADMIN confirms a Cyrillic spelling that is not an active catalog word and chooses to add it
- **THEN** the system SHALL add the Cyrillic word to the catalog through its normal validation and create the eligible pair

#### Scenario: A word cannot join two pairs
- **WHEN** an ADMIN creates a pair using a word that already belongs to another pair
- **THEN** the system SHALL refuse the pair and name the existing pair

#### Scenario: Single-script word is not eligible
- **WHEN** an Uzbek word is not part of any pair
- **THEN** it SHALL NOT be picked for the `uz` calendar

### Requirement: Eligibility survives removal and restore
Daily eligibility SHALL persist while a catalog word is removed, but a removed word SHALL never be picked or published as an answer. Restoring the word SHALL return it to the answer pool.

#### Scenario: Removed eligible word is not picked
- **WHEN** an eligible word is removed from the catalog
- **THEN** it SHALL NOT be picked for any unlocked day and SHALL NOT appear in the pack's answers

#### Scenario: Restored word returns to the pool
- **WHEN** a removed word that was eligible is restored
- **THEN** it SHALL be eligible again without an ADMIN re-marking it

### Requirement: Never-used counter
For each calendar, the panel SHALL show ADMINs the number of eligible words that have never been the daily word of any day from the history start through tomorrow and are not manually picked for a future day. It SHALL be shown as information only, without any alert or notification.

#### Scenario: Counter drops when a word is played
- **WHEN** a never-used eligible word becomes the daily word of today or tomorrow
- **THEN** the calendar's never-used counter SHALL decrease by one

#### Scenario: Counter grows with the pool
- **WHEN** an ADMIN marks a never-used word as eligible
- **THEN** the calendar's never-used counter SHALL increase by one

### Requirement: The answer pool is published with the word pack
Every answer-pool change SHALL publish immediately: each affected language's pack answers SHALL be exactly its active eligible words (for Uzbek, each eligible pair's word in that script), and the pack version SHALL advance so clients receive it on their next sync.

#### Scenario: Marking publishes a new version
- **WHEN** an ADMIN marks a word as eligible for `ru`
- **THEN** the `ru` pack's answers SHALL include the word and its version SHALL advance

#### Scenario: Uzbek pair publishes both scripts
- **WHEN** an ADMIN creates an eligible Uzbek pair
- **THEN** both the `uz-latn` and `uz-cyrl` packs SHALL list their script's word among the answers and both versions SHALL advance

### Requirement: Existing answers start eligible
When this capability is introduced, every current answer of the `en`, `ru` and `kk` packs SHALL be daily-eligible, and every current Uzbek lexeme pair SHALL exist as an eligible pair, so no calendar starts empty.

#### Scenario: Migration keeps today's answers
- **WHEN** the system starts for the first time with this capability
- **THEN** the answer pool of each calendar SHALL contain every answer its pack had before

### Requirement: Answer-pool changes are audited
Every marking, unmarking, pair creation and pair removal SHALL be recorded in the audit log with the acting ADMIN, the calendar, the word or pair, and the time. These entries SHALL NOT be visible to WORDERs.

#### Scenario: Marking is recorded
- **WHEN** an ADMIN marks a word as eligible
- **THEN** an audit entry SHALL record the ADMIN, the calendar, the word and the time
