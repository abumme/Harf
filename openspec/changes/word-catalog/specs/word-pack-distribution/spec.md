## ADDED Requirements

### Requirement: Published pack follows the word catalog
Each language's published pack SHALL consist of that language's active catalog words as guesses, its answers limited to active catalog words, and its schedule. Every catalog change SHALL advance the version as part of the change. A removed word SHALL NOT appear in the pack's guesses or answers. Catalog changes SHALL NOT change the pack's schedule or effective date.

#### Scenario: Removed word disappears for clients
- **WHEN** staff remove a word and a client pulls the language's pack afterwards
- **THEN** the pack SHALL NOT contain the word in its guesses or answers, and the client SHALL no longer accept it as a guess unless it is a scheduled daily word

#### Scenario: Removing an answer keeps the schedule
- **WHEN** staff remove or respell a word that is an answer and appears in the schedule
- **THEN** the pack's answers SHALL no longer contain the removed spelling, the schedule SHALL be unchanged, and every day's daily word SHALL stay the same

#### Scenario: Unchanged catalog keeps the version
- **WHEN** no catalog change happens
- **THEN** the pack version SHALL stay the same and conditional requests SHALL keep answering not-modified

### Requirement: Pack responses are compressed on request
The server SHALL compress word-pack responses when the request declares that it accepts a supported compression, and SHALL send them uncompressed otherwise; the decompressed body SHALL be identical either way.

#### Scenario: Client accepting gzip receives a compressed pack
- **WHEN** a client requests a language's pack declaring that it accepts gzip
- **THEN** the server SHALL respond with a gzip-encoded body that decodes to the same pack

#### Scenario: Client not declaring compression receives plain JSON
- **WHEN** a client requests a language's pack without declaring accepted compression
- **THEN** the server SHALL respond with an uncompressed body

## MODIFIED Requirements

### Requirement: Deployed guess dictionaries reach existing server packs
When the backend starts with a guess dictionary containing words missing from a language's catalog, it SHALL add those words to the catalog as bundled-dictionary words and advance the pack version exactly once. It SHALL NOT re-add a word staff removed or respelled, SHALL NOT remove any word already active, and SHALL NOT change the pack's answers, schedule, or effective date.

#### Scenario: New dictionary words become available to clients
- **WHEN** the backend starts with a guess dictionary that contains words the catalog lacks
- **THEN** the catalog and the published pack's guesses SHALL include those words and the version SHALL advance, so a client syncing afterwards receives them

#### Scenario: Restart without new words keeps the version
- **WHEN** the backend restarts and the deployed dictionary contains no word missing from the catalog
- **THEN** the pack version SHALL stay the same, so clients are not made to download an unchanged pack

#### Scenario: Words added after the build are kept
- **WHEN** the catalog contains active words the deployed dictionary lacks, such as editor-accepted, automatically accepted or staff-added words
- **THEN** those words SHALL remain valid guesses after the merge

#### Scenario: Staff removals survive the merge
- **WHEN** the deployed dictionary contains a word staff removed, or the previous spelling of a word staff respelled
- **THEN** the merge SHALL NOT make that word active again and SHALL NOT advance the version because of it

#### Scenario: Merging never changes the daily puzzle
- **WHEN** a deployed dictionary is merged into the catalog
- **THEN** the pack's answers, schedule, and effective date SHALL be unchanged
