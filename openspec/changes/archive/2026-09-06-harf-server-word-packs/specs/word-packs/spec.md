## MODIFIED Requirements

### Requirement: Bundled offline word packs per language
The app SHALL ship a bundled answer list and guess dictionary for each launch language as app resources, and SHALL treat the active vocabulary as the freshest valid pack: a locally-cached, server-fetched pack when one is present and valid, otherwise the bundled pack. Both the cached and bundled packs SHALL be loadable without any network access at play time.

#### Scenario: Packs load offline
- **WHEN** the app loads a language's word pack with no network available and nothing cached
- **THEN** the bundled answer list and guess dictionary are available for play

#### Scenario: A cached server pack overrides the bundle
- **WHEN** a valid, newer server pack has been cached for a language
- **THEN** the app uses the cached pack's answers and guesses instead of the bundled ones, still without any network call at play time

## ADDED Requirements

### Requirement: Fetched packs are validated before use
A server-fetched pack SHALL be applied only after it passes the same integrity checks as a bundled pack; a fetched pack that fails SHALL be discarded in favor of the last good pack (cached or bundled).

#### Scenario: Invalid fetched pack is rejected
- **WHEN** a fetched pack fails validation — an entry does not tokenize under the language config, an answer has an unsupported board length, or an answer is missing from the guess dictionary
- **THEN** the app SHALL keep using the previous valid pack and SHALL NOT switch to the invalid one
