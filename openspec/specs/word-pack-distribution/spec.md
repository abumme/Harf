# word-pack-distribution Specification

## Purpose
Distributes vocabulary over the air: the backend serves a versioned word pack and date→word schedule per language, and the client fetches, validates, and caches it offline-first — so words can grow and be corrected without an app release, while the daily round still plays with no network call.

## Requirements

### Requirement: Serve a versioned word pack per language
The backend SHALL expose, per language, the current word pack (answer list, guess dictionary, and date→word schedule) with a version identifier over a public, cacheable endpoint, and SHALL answer a conditional request that already carries the current version as not-modified.

#### Scenario: Fetch the current pack
- **WHEN** a client requests the word pack for a supported language
- **THEN** the server returns the current version's answers, guesses, and schedule together with a version/ETag identifier

#### Scenario: Unchanged pack is cheap to poll
- **WHEN** a client requests a language's pack with a conditional check that matches the server's current version
- **THEN** the server responds not-modified and does not resend the pack body

#### Scenario: Unknown language
- **WHEN** a client requests a pack for a language the server does not serve
- **THEN** the server responds not-found and the client keeps its bundled or cached pack

### Requirement: Offline-first client sync
The client SHALL fetch pack updates opportunistically — on launch or foreground when online and its cached version is stale — and SHALL never block gameplay on the network.

#### Scenario: Update fetched when online and stale
- **WHEN** the app is online and foregrounded and the server has a newer version than the cached one
- **THEN** the client downloads and caches the new pack for later play

#### Scenario: Offline never blocks play
- **WHEN** the app is offline or the fetch fails
- **THEN** the daily round and guess validation still work from the cached or bundled pack, with no error surfaced that stops play

### Requirement: Cache with bundled fallback
The client SHALL persist the last valid fetched pack locally and resolve the active pack as the cached pack when valid, otherwise the bundled pack.

#### Scenario: Cached pack survives restart
- **WHEN** a valid pack was cached and the app restarts while offline
- **THEN** the app uses the cached pack, not the bundled one

#### Scenario: Fresh install falls back to the bundle
- **WHEN** the app has never successfully fetched a pack
- **THEN** it plays from the bundled pack until a fetch succeeds

### Requirement: Updates take effect from a future date
A published pack update SHALL carry an effective date that is not in the past, so that adopting the update never changes the answer for a day players have already seen.

#### Scenario: Update does not rewrite history
- **WHEN** the maintainer publishes a new pack version
- **THEN** its schedule changes apply only to dates on or after its effective date, leaving past and current days unchanged

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
