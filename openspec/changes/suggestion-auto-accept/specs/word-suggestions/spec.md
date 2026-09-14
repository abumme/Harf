## ADDED Requirements

### Requirement: Automatic dictionary verification
The system SHALL check each validated, stored suggestion against English Wiktionary for an entry in the suggestion's language (both Uzbek scripts map to Uzbek), and SHALL accept it automatically when that entry is a dictionary form or an inflected form of a word in that language and carries no flag that needs human judgement. Every other outcome SHALL leave the suggestion pending for editor review; verification SHALL never reject a suggestion. Verification SHALL NOT delay the response to the submitting player.

#### Scenario: Dictionary form is auto-accepted
- **WHEN** Wiktionary lists the suggested word as a dictionary form (including a variant form such as an alternative script) in the suggestion's language
- **THEN** the system SHALL accept the suggestion automatically, record that it was decided automatically, and add the word to the language's pack guesses

#### Scenario: Inflected form is auto-accepted
- **WHEN** Wiktionary lists the suggested word as an inflected (non-dictionary) form in the suggestion's language
- **THEN** the system SHALL accept the suggestion automatically

#### Scenario: Entry only in another language is not auto-accepted
- **WHEN** Wiktionary has a page for the word but no entry for the suggestion's language
- **THEN** the system SHALL leave the suggestion pending for editor review

#### Scenario: Flagged entry goes to editors
- **WHEN** the word's entry in the suggestion's language is a proper noun, an abbreviation, acronym or initialism, a misspelling, or a vulgar, offensive or derogatory term — even if it is also listed as a dictionary form
- **THEN** the system SHALL leave the suggestion pending for editor review and SHALL state the flag to editors

#### Scenario: Unknown word goes to editors
- **WHEN** Wiktionary has no page for the suggested word
- **THEN** the system SHALL leave the suggestion pending for editor review

#### Scenario: Unavailable dictionary falls back to editors
- **WHEN** the dictionary lookup fails (timeout, error or unreadable response) on each of a bounded number of attempts
- **THEN** the system SHALL post the suggestion to editors marked as unverified, and SHALL neither accept nor reject it automatically

#### Scenario: Verification can be disabled
- **WHEN** automatic verification is disabled by configuration
- **THEN** every validated suggestion SHALL go to editor review without a dictionary lookup

#### Scenario: Player response does not wait for verification
- **WHEN** a player submits a valid suggestion
- **THEN** the system SHALL respond that it was accepted for review without waiting for the dictionary lookup

### Requirement: Auto-accepted words are announced
When a suggestion is accepted automatically and Telegram is configured, the bot SHALL post an informational message naming the word, its language, whether it is a dictionary or inflected form, a link to its Wiktionary entry, and its author, without accept or reject controls.

#### Scenario: Announcement is posted without controls
- **WHEN** a suggestion is accepted automatically
- **THEN** the bot SHALL post the word, language, form kind, Wiktionary link and author, and SHALL NOT attach decision controls

#### Scenario: Failed announcement is retried without re-deciding
- **WHEN** Telegram does not confirm an announcement
- **THEN** the system SHALL retry the announcement later, the word SHALL remain accepted, and the word pack SHALL NOT change again

### Requirement: Daily per-language suggestion report
When Telegram is configured, the system SHALL send one report per language shortly after 00:00 Asia/Tashkent, covering the calendar day that just ended in Asia/Tashkent time, to that language's topic (or the chat root when none is configured). The report SHALL list the words accepted automatically, the words accepted by editors, and the words rejected during that day, each with its count, and the number of that language's suggestions still pending when the report is built.

#### Scenario: Report lists the day's decisions
- **WHEN** suggestions for a language were decided during the reported day
- **THEN** that language's report SHALL list the auto-accepted, editor-accepted and rejected words with their counts, and the pending count

#### Scenario: Decisions count toward the day they were made
- **WHEN** a suggestion submitted on one day is decided on the following day
- **THEN** it SHALL appear in the report for the day it was decided

#### Scenario: Quiet day sends nothing
- **WHEN** no suggestion for a language was decided during the reported day and none is pending
- **THEN** the system SHALL NOT send a report for that language and day

#### Scenario: Report is sent once
- **WHEN** the report for a language and day has been delivered
- **THEN** the system SHALL NOT send it again, including after a restart

#### Scenario: Missed report is retried
- **WHEN** the report for the previous day could not be delivered at 00:00 (server down or Telegram failure)
- **THEN** the system SHALL keep retrying and deliver it once possible during the following day

#### Scenario: Long lists are truncated
- **WHEN** a group has more words than fit in one message
- **THEN** the report SHALL show a bounded number of words and state how many more were omitted

## MODIFIED Requirements

### Requirement: Suggestion validation and anti-spam
The system SHALL validate a suggestion before it is stored, verified against the dictionary, or shown to editors, filtering out ill-formed, offensive, obviously nonsensical, duplicate, and excessive submissions. These heuristics reduce noise only; editors and dictionary verification decide whether a word is real.

#### Scenario: Wrong-length word is rejected
- **WHEN** a suggested word is shorter than 2 or longer than 24 characters
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
The system SHALL notify word editors, through a Telegram bot that presents an accept and a reject control, of each pending suggestion that was not accepted automatically, and SHALL act only on decisions from authorized editors. Every Telegram message about a suggestion SHALL go to that language's forum topic when one is configured, otherwise to the chat root. A suggestion message SHALL count as delivered only once Telegram confirms it; until then the system SHALL retry. When Telegram is not configured, the feature SHALL degrade to storing and verifying suggestions without notification rather than failing.

#### Scenario: Pending suggestion is posted to editors
- **WHEN** a stored suggestion is not accepted automatically (not found, flagged, dictionary unavailable, or verification disabled) and Telegram is configured
- **THEN** the bot SHALL post the word, its language and its author to the editors' chat with an accept control and a reject control, stating the reason when the word was flagged or could not be verified

#### Scenario: Auto-accepted suggestion gets no decision controls
- **WHEN** a suggestion was accepted automatically
- **THEN** the bot SHALL NOT post accept or reject controls for it

#### Scenario: Suggestions are routed to per-language topics when configured
- **WHEN** a per-language forum topic is configured for the suggestion's language
- **THEN** the bot SHALL post every message about that suggestion into that language's topic; a language with no configured topic SHALL post to the chat root

#### Scenario: Unconfirmed delivery is retried
- **WHEN** Telegram does not confirm a suggestion message
- **THEN** the system SHALL retry it later and SHALL NOT treat the suggestion as delivered to editors

#### Scenario: Only allowlisted editors may decide
- **WHEN** a Telegram user who is not on the editor allowlist taps a decision control
- **THEN** the system SHALL NOT change the suggestion's status, SHALL leave the message and its controls unchanged, and SHALL inform only the tapping user

#### Scenario: A decided message keeps its context
- **WHEN** an authorized editor's decision is applied
- **THEN** the message SHALL keep the word, language and author, and SHALL replace the controls with the outcome and the deciding editor

#### Scenario: A decision is applied once
- **WHEN** a decision control is tapped for a suggestion that is no longer pending (already accepted or rejected)
- **THEN** the system SHALL not change the outcome again and SHALL indicate the suggestion was already decided

#### Scenario: Concurrent decisions apply once
- **WHEN** two authorized decisions for the same pending suggestion arrive at the same time
- **THEN** exactly one SHALL take effect and the word pack SHALL change at most once

#### Scenario: Telegram not configured degrades gracefully
- **WHEN** a valid suggestion is submitted while no Telegram bot token is configured
- **THEN** the system SHALL still store the suggestion and respond with success, without error, and dictionary verification SHALL still apply

### Requirement: Accepted word enters the distributed word pack
When a suggestion is accepted — by an authorized editor or automatically by dictionary verification — the system SHALL add the word to its language's word-pack guesses and advance the pack version so that clients receive it through the existing word-pack pull, without altering past daily puzzles.

#### Scenario: Accepted word becomes a valid guess for clients
- **WHEN** an authorized editor accepts a pending suggestion, or a suggestion is accepted automatically
- **THEN** the system SHALL add the word to that language's pack guesses, mark the suggestion accepted, and advance the pack version so a client that pulls the pack afterward can submit the word

#### Scenario: Accepting does not change daily answers or past days
- **WHEN** a suggestion is accepted
- **THEN** the system SHALL add the word only to the guess list and SHALL NOT change the answer schedule or any past day's puzzle

#### Scenario: Rejected word is not added
- **WHEN** an authorized editor rejects a pending suggestion
- **THEN** the system SHALL mark it rejected and SHALL NOT change the word pack
