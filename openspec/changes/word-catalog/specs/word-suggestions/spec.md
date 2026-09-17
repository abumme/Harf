## ADDED Requirements

### Requirement: Suggested words must be playable
The system SHALL reject a suggestion whose word, normalized with its language's rules, does not tokenize into that language's letters or whose grapheme count is outside the language's supported board lengths. Such a suggestion SHALL NOT be stored, verified, or shown to editors.

#### Scenario: Word with a foreign letter is rejected
- **WHEN** a suggestion's word contains a letter outside the suggestion language's alphabet
- **THEN** the system SHALL reject the suggestion and SHALL NOT store it or notify editors

#### Scenario: Word of unsupported board length is rejected
- **WHEN** a suggestion's word has a grapheme count outside the language's supported board lengths
- **THEN** the system SHALL reject the suggestion and SHALL NOT store it or notify editors

### Requirement: Words removed by staff are not accepted automatically
When a suggested word matches a word staff removed from the language's catalog, the system SHALL NOT accept it by dictionary verification; it SHALL send the suggestion to editor review, stating that staff removed the word. An editor accepting it SHALL restore the word.

#### Scenario: Removed word goes to editors
- **WHEN** a player suggests a word that staff removed from that language, and the dictionary would verify it
- **THEN** the suggestion SHALL stay pending, SHALL be sent to editors with a note that staff removed the word, and SHALL NOT be accepted automatically

#### Scenario: Editor acceptance restores the word
- **WHEN** an authorized editor accepts a suggestion for a word staff removed
- **THEN** the word SHALL be active again in the catalog and published to clients

### Requirement: Reviewing suggestions in the admin panel
The panel SHALL list pending suggestions for each language the staff member may review, oldest first, each with its word, language, author label, submission time and the reason it needs review (not found, flagged kind, dictionary unavailable, verification disabled, or removed by staff). A staff member SHALL be able to accept or reject a pending suggestion; the decision SHALL follow the same apply-once rule as Telegram decisions and SHALL be attributed to that staff member. When the suggestion's Telegram message is known, a panel decision SHALL also replace that message's controls with the outcome. The panel SHALL also list decided suggestions, newest first, with outcome, who or what decided, and when.

#### Scenario: Pending queue shows permitted languages only
- **WHEN** a WORDER assigned to Kazakh opens the suggestions page
- **THEN** the panel SHALL list pending Kazakh suggestions oldest first and SHALL NOT list suggestions of other languages

#### Scenario: Out-of-scope decision is refused
- **WHEN** a WORDER submits a decision for a suggestion in a language they are not assigned to
- **THEN** the system SHALL refuse it as forbidden and SHALL NOT change the suggestion

#### Scenario: Accepting in the panel adds the word
- **WHEN** a staff member accepts a pending suggestion in the panel
- **THEN** the suggestion SHALL be accepted and attributed to that staff member, and the word SHALL be active in the catalog and published

#### Scenario: Panel decision updates the Telegram message
- **WHEN** a staff member decides a suggestion in the panel whose Telegram decision message was delivered
- **THEN** that Telegram message SHALL keep its word, language and author and SHALL show the outcome and the deciding staff member instead of the controls

#### Scenario: Panel decision on an already decided suggestion
- **WHEN** a staff member decides a suggestion that was already decided in Telegram, in the panel, or automatically
- **THEN** the system SHALL NOT change the outcome and SHALL tell the staff member it was already decided

#### Scenario: Decided suggestions are listed
- **WHEN** a staff member opens the decided suggestions for a permitted language
- **THEN** the panel SHALL list them newest first with outcome, decider (staff member, Telegram editor, or automatic) and decision time

## MODIFIED Requirements

### Requirement: Editor review over Telegram
The system SHALL notify word editors, through a Telegram bot that presents an accept and a reject control, of each pending suggestion that was not accepted automatically, and SHALL act only on decisions from authorized editors. An authorized editor is an active staff account whose linked Telegram user ID matches the tapping user and whose languages include the suggestion's language (an ADMIN has every language); while a legacy editor allowlist is still configured for the deployment, its Telegram users SHALL also be authorized for every language. Every Telegram message about a suggestion SHALL go to that language's forum topic when one is configured, otherwise to the chat root. A suggestion message SHALL count as delivered only once Telegram confirms it; until then the system SHALL retry. When Telegram is not configured, the feature SHALL degrade to storing and verifying suggestions without notification rather than failing.

#### Scenario: Pending suggestion is posted to editors
- **WHEN** a stored suggestion is not accepted automatically (not found, flagged, dictionary unavailable, verification disabled, or removed by staff) and Telegram is configured
- **THEN** the bot SHALL post the word, its language and its author to the editors' chat with an accept control and a reject control, stating the reason when the word was flagged, could not be verified, or was removed by staff

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
- **WHEN** a Telegram user who is neither linked to an active staff account nor on a configured legacy allowlist taps a decision control
- **THEN** the system SHALL NOT change the suggestion's status, SHALL leave the message and its controls unchanged, and SHALL inform only the tapping user

#### Scenario: Staff editor is limited to their languages
- **WHEN** a Telegram user linked to an active WORDER taps a decision control on a suggestion in a language that WORDER is not assigned to
- **THEN** the system SHALL NOT change the suggestion's status, SHALL leave the message and its controls unchanged, and SHALL inform only the tapping user

#### Scenario: Disabled staff can no longer decide
- **WHEN** a Telegram user linked to a disabled staff account, and not on a configured legacy allowlist, taps a decision control
- **THEN** the system SHALL NOT change the suggestion's status and SHALL inform only the tapping user

#### Scenario: Legacy allowlist keeps working during migration
- **WHEN** a Telegram user on the configured legacy editor allowlist taps a decision control for a suggestion in any language
- **THEN** the system SHALL apply the decision as it did before staff accounts existed

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
When a suggestion is accepted — by an authorized editor in Telegram, by a staff member in the panel, or automatically by dictionary verification — the system SHALL make the word an active word of its language's catalog, recorded as coming from that suggestion and whether it was accepted automatically or by an editor, and SHALL publish the language's pack with an advanced version in the same operation, so that clients receive it through the existing word-pack pull, without altering past daily puzzles. A word that is already active SHALL NOT advance the version again; a word staff removed SHALL be restored.

#### Scenario: Accepted word becomes a valid guess for clients
- **WHEN** an authorized editor or staff member accepts a pending suggestion, or a suggestion is accepted automatically
- **THEN** the system SHALL make the word active in that language's catalog attributed to the suggestion, mark the suggestion accepted, and advance the pack version so a client that pulls the pack afterward can submit the word

#### Scenario: Already active word does not republish
- **WHEN** a suggestion is accepted for a word that staff already added to the catalog after the suggestion was made
- **THEN** the suggestion SHALL be marked accepted and the pack version SHALL NOT advance

#### Scenario: Accepting does not change daily answers or past days
- **WHEN** a suggestion is accepted
- **THEN** the system SHALL add the word only to the guess list and SHALL NOT change the answer schedule or any past day's puzzle

#### Scenario: Rejected word is not added
- **WHEN** an authorized editor or staff member rejects a pending suggestion
- **THEN** the system SHALL mark it rejected and SHALL NOT change the catalog or the word pack
