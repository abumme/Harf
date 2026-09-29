## ADDED Requirements

### Requirement: Deployed guess dictionaries reach existing server packs
When the backend starts with a guess dictionary containing words missing from a language's stored pack, it SHALL add those words to the pack's guesses and advance the pack version exactly once. It SHALL NOT remove any word already stored in the pack, and SHALL NOT change the pack's answers, schedule, or effective date.

#### Scenario: New dictionary words become available to clients
- **WHEN** the backend starts with a guess dictionary that contains words the stored pack lacks
- **THEN** the stored pack's guesses SHALL include those words and its version SHALL advance, so a client syncing afterwards receives them

#### Scenario: Restart without new words keeps the version
- **WHEN** the backend restarts and the deployed dictionary contains no word missing from the stored pack
- **THEN** the pack version SHALL stay the same, so clients are not made to download an unchanged pack

#### Scenario: Words added after the build are kept
- **WHEN** a stored pack contains words the deployed dictionary lacks, such as editor-accepted or automatically accepted suggestions
- **THEN** those words SHALL remain valid guesses after the merge

#### Scenario: Merging never changes the daily puzzle
- **WHEN** a deployed dictionary is merged into a stored pack
- **THEN** the pack's answers, schedule, and effective date SHALL be unchanged
