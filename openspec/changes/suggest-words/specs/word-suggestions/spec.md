## Purpose

Lets players propose dictionary words that the game rejected as unknown, routes each proposal to word editors for a one-tap accept/reject decision over a Telegram bot, and distributes accepted words to all players through the existing word-pack channel.

## ADDED Requirements

### Requirement: Suggesting a rejected word from the game
The client SHALL offer a suggestion action when — and only when — a full-length guess is rejected because it is not in the dictionary, and submitting it SHALL send the rejected word and its language to the backend and report that it was sent for review.

#### Scenario: Suggestion action appears only for an unknown full-length word
- **WHEN** a submitted guess is full length but not in the dictionary
- **THEN** the client SHALL present a "suggest to add" action for that word

#### Scenario: No suggestion action for an incomplete guess
- **WHEN** a guess is rejected only because it is shorter than the required length
- **THEN** the client SHALL NOT present a suggestion action

#### Scenario: Sending a suggestion reports it was received
- **WHEN** the player invokes the suggestion action
- **THEN** the client SHALL send the rejected word and the active language to the backend and SHALL show that the suggestion was sent for review, without claiming it was accepted

### Requirement: Accepting and storing a word suggestion
The backend SHALL accept an authenticated suggestion of a word for a language from any valid session, including an anonymous one, and SHALL store it as pending together with the author's account identity.

#### Scenario: A valid suggestion is stored pending
- **WHEN** an authenticated client submits a well-formed, non-duplicate word that passes validation
- **THEN** the system SHALL store the suggestion with a pending status and record the submitting account as its author, and SHALL respond that it was accepted for review

#### Scenario: An unauthenticated suggestion is rejected
- **WHEN** a suggestion request arrives without a valid session token
- **THEN** the system SHALL reject it as unauthorized and SHALL NOT store anything

#### Scenario: Author association survives account deletion
- **WHEN** an account that authored suggestions is deleted
- **THEN** the system SHALL keep those suggestions but clear their author reference, so accepted contributions and history are not lost

### Requirement: Suggestion validation and anti-spam
The system SHALL validate a suggestion before it is stored or shown to editors, filtering out ill-formed, offensive, obviously nonsensical, duplicate, and excessive submissions. These heuristics reduce noise only; editors remain the authority on whether a word is real.

#### Scenario: Wrong-length word is rejected
- **WHEN** a suggested word does not match the puzzle length for its language
- **THEN** the system SHALL reject the suggestion and SHALL NOT notify editors

#### Scenario: Offensive word is rejected
- **WHEN** a suggested word is on the offensive-word blocklist
- **THEN** the system SHALL reject the suggestion and SHALL NOT notify editors

#### Scenario: Obvious gibberish is rejected
- **WHEN** a suggested word fails cheap gibberish checks (for example a letter repeated three or more times in a row, too few distinct letters, or no vowel where the language has vowels)
- **THEN** the system SHALL reject the suggestion and SHALL NOT notify editors

#### Scenario: Duplicate pending suggestion is collapsed
- **WHEN** a word is suggested for a language that already has an identical pending suggestion
- **THEN** the system SHALL NOT create a second pending entry and SHALL still report success to the submitter

#### Scenario: Per-user daily cap is enforced
- **WHEN** an account exceeds its allowed number of suggestions within a day
- **THEN** the system SHALL reject further suggestions from that account until the window resets

#### Scenario: A word already in the pack is not re-suggested
- **WHEN** a suggested word is already a valid guess in the language's word pack
- **THEN** the system SHALL reject the suggestion as already present

### Requirement: Editor review over Telegram
The system SHALL notify word editors of each pending suggestion through a Telegram bot that presents an accept and a reject control, and SHALL act only on decisions from authorized editors. When Telegram is not configured, the feature SHALL degrade to storing suggestions without notification rather than failing.

#### Scenario: Pending suggestion is posted to editors
- **WHEN** a suggestion has been stored as pending and Telegram is configured
- **THEN** the bot SHALL post the word, its language, to the editors' chat with an accept control and a reject control

#### Scenario: Only allowlisted editors may decide
- **WHEN** a Telegram user who is not on the editor allowlist taps a decision control
- **THEN** the system SHALL ignore the action and SHALL NOT change the suggestion's status

#### Scenario: A decision is applied once
- **WHEN** a decision control is tapped for a suggestion that is no longer pending (already accepted or rejected)
- **THEN** the system SHALL not change the outcome again and SHALL indicate the suggestion was already decided

#### Scenario: Telegram not configured degrades gracefully
- **WHEN** a valid suggestion is submitted while no Telegram bot token is configured
- **THEN** the system SHALL still store the suggestion as pending and respond with success, without error

### Requirement: Accepted word enters the distributed word pack
When an authorized editor accepts a suggestion, the system SHALL add the word to its language's word-pack guesses and advance the pack version so that clients receive it through the existing word-pack pull, without altering past daily puzzles.

#### Scenario: Accepted word becomes a valid guess for clients
- **WHEN** an authorized editor accepts a pending suggestion
- **THEN** the system SHALL add the word to that language's pack guesses, mark the suggestion accepted, and advance the pack version so a client that pulls the pack afterward can submit the word

#### Scenario: Accepting does not change daily answers or past days
- **WHEN** a suggestion is accepted
- **THEN** the system SHALL add the word only to the guess list and SHALL NOT change the answer schedule or any past day's puzzle

#### Scenario: Rejected word is not added
- **WHEN** an authorized editor rejects a pending suggestion
- **THEN** the system SHALL mark it rejected and SHALL NOT change the word pack
