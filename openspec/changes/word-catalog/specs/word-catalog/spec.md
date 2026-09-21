## Purpose

Lets staff manage each language's vocabulary on screen — browse, add, edit, remove and restore words — with every change validated by the app's own language rules, attributed and audited, limited to the staff member's languages, and published to players immediately.

## ADDED Requirements

### Requirement: Existing vocabulary is carried into the catalog
The system SHALL hold every word of every language's published pack (guesses and answers) as an active catalog word, and SHALL do so without changing the set of words clients receive or advancing any pack version. Carrying words over SHALL happen once per language and SHALL be safe to repeat.

#### Scenario: Published words become catalog words
- **WHEN** the catalog is introduced for a language whose published pack already contains words
- **THEN** every word of that pack SHALL appear as an active catalog word for that language

#### Scenario: Clients see no change from the carry-over
- **WHEN** a language's words have been carried into the catalog
- **THEN** the published pack for that language SHALL contain the same words and keep the same version as before

#### Scenario: Carry-over is not repeated
- **WHEN** the system restarts after a language's words were carried over and staff have since changed the catalog
- **THEN** the carry-over SHALL NOT run again for that language and SHALL NOT undo those changes

#### Scenario: Accepted suggestions keep their origin
- **WHEN** a carried-over word matches a previously accepted suggestion for the same language
- **THEN** the catalog word SHALL record that it came from a player suggestion, and whether it was accepted automatically or by an editor

### Requirement: Browsing and searching words
The panel SHALL let staff list a language's catalog words page by page, search by text, filter by status (active, removed), source (bundled dictionary, player suggestion, automatic acceptance, staff), the staff member who added the word, and the date it was added, and sort by text or by date. A search query SHALL be normalized with the language's rules before matching.

#### Scenario: Words are listed page by page
- **WHEN** a staff member opens a permitted language's word list
- **THEN** the panel SHALL show one page of active words with the total count and controls to move between pages

#### Scenario: Search matches normalized text
- **WHEN** a staff member searches a language for text containing a letter variant that the language normalizes (for example an apostrophe variant in Uzbek Latin or "ё" in Russian)
- **THEN** the results SHALL include words containing the normalized form of that text

#### Scenario: Removed words can be listed
- **WHEN** a staff member filters a language's words by removed status
- **THEN** the panel SHALL list the removed words with who removed each and when

### Requirement: Words are valid for their language
Before a word is added, edited or restored, the system SHALL normalize it with the language's rules and SHALL accept it only if it tokenizes into the language's letters, its letter count (in graphemes) is within the language's supported board lengths, it is not on the language's blocklist, and no other active word in that language has the same normalized text. A rejected word SHALL change nothing and SHALL come with the reason.

#### Scenario: Word is stored in normalized form
- **WHEN** a staff member adds a word typed with uppercase letters or a letter variant the language normalizes
- **THEN** the system SHALL store and publish the normalized form

#### Scenario: Normalized form is shown before saving
- **WHEN** a staff member types a word to add
- **THEN** the panel SHALL show the normalized form and whether it is valid, with the reason when it is not, before the word is saved

#### Scenario: Word with a foreign letter is rejected
- **WHEN** a staff member adds a word containing a letter that is not in the language's alphabet
- **THEN** the system SHALL reject it as not tokenizable and SHALL NOT change the catalog

#### Scenario: Word of unsupported length is rejected
- **WHEN** a staff member adds a word whose grapheme count is outside the language's supported board lengths
- **THEN** the system SHALL reject it for its length and SHALL NOT change the catalog

#### Scenario: Blocklisted word is rejected
- **WHEN** a staff member adds a word that is on the language's blocklist
- **THEN** the system SHALL reject it as blocklisted and SHALL NOT change the catalog

#### Scenario: Duplicate word is rejected
- **WHEN** a staff member adds a word whose normalized text equals an active word in the same language
- **THEN** the system SHALL reject it as a duplicate and SHALL NOT change the catalog

### Requirement: Adding a word
A staff member SHALL be able to add a single valid word to a permitted language. The word SHALL become active immediately, recorded as added by staff, with the adding staff member and time. Adding a word whose normalized text belongs to a removed word SHALL restore that word instead of creating a second one.

#### Scenario: Valid word is added
- **WHEN** a staff member adds a valid word to a permitted language
- **THEN** the word SHALL be active in the catalog, attributed to that staff member with the time, and marked as added by staff

#### Scenario: Adding a removed word restores it
- **WHEN** a staff member adds a word whose normalized text matches a removed word in that language
- **THEN** the system SHALL restore the removed word as active, record the restoration, and SHALL NOT create a duplicate entry

### Requirement: Adding words in bulk
A staff member SHALL be able to paste up to 1,000 lines into a permitted language in one request. Each non-empty line SHALL be handled as a single add and SHALL receive its own outcome: added, restored, duplicate (already active, or repeated earlier in the same paste), invalid with the reason, or blocklisted. Valid lines SHALL be applied even when other lines fail, and the whole request SHALL publish at most one new pack version.

#### Scenario: Each line reports its outcome
- **WHEN** a staff member pastes lines containing a new word, a word that is already active, a word with a foreign letter and a blocklisted word
- **THEN** the system SHALL add the new word and report added, duplicate, invalid (not tokenizable) and blocklisted for the respective lines

#### Scenario: A bulk add publishes once
- **WHEN** a bulk add adds or restores several words
- **THEN** the language's pack version SHALL advance exactly once for that request

#### Scenario: A bulk add with no effective change publishes nothing
- **WHEN** every line of a bulk add is a duplicate, invalid or blocklisted
- **THEN** the pack version SHALL NOT advance

#### Scenario: Oversized paste is refused
- **WHEN** a staff member submits more than 1,000 lines at once
- **THEN** the system SHALL refuse the whole request and SHALL NOT change the catalog

### Requirement: Editing a word
A staff member SHALL be able to change the spelling of an active word in a permitted language. The new spelling SHALL pass word validation and SHALL NOT equal a removed word's text. After the edit the old spelling SHALL no longer be a valid word and SHALL be kept as removed, so it does not return; the edit SHALL record who changed it, when, and the previous spelling.

#### Scenario: Spelling is corrected
- **WHEN** a staff member changes an active word's spelling to a valid, unused spelling
- **THEN** the catalog word SHALL have the new spelling, the published pack SHALL contain the new spelling and not the old one, and the edit SHALL be attributed with the previous spelling

#### Scenario: Old spelling does not come back
- **WHEN** a word's old spelling is also present in a deployed bundled dictionary and the system restarts
- **THEN** the old spelling SHALL remain absent from the published pack

#### Scenario: Editing into a removed word is refused
- **WHEN** a staff member changes a word's spelling to the text of a removed word in the same language
- **THEN** the system SHALL refuse the edit, SHALL indicate that the target word was removed and can be restored, and SHALL NOT change the catalog

### Requirement: Removing and restoring a word
A staff member SHALL be able to remove an active word from a permitted language and restore a removed one. Removal SHALL keep the word as a removed entry with who removed it and when; a removed word SHALL NOT be served to players. Restoring SHALL make the word active again only if it still passes word validation.

#### Scenario: Removed word leaves the published pack
- **WHEN** a staff member removes an active word
- **THEN** the word SHALL be marked removed with the staff member and time, and the next published pack SHALL NOT contain it

#### Scenario: Removed word can be restored
- **WHEN** a staff member restores a removed word that still passes validation
- **THEN** the word SHALL be active again and SHALL be in the next published pack

#### Scenario: Restoring a now-blocklisted word is refused
- **WHEN** a staff member restores a removed word that has since been added to the language's blocklist
- **THEN** the system SHALL refuse the restore with the reason and the word SHALL stay removed

### Requirement: Word changes are live immediately
Every successful catalog change SHALL publish that language's pack with an advanced version as part of the same operation, so a client pulling the pack afterwards receives the change. A change that fails SHALL publish nothing. Concurrent changes to the same language SHALL all be applied, each publishing a distinct version.

#### Scenario: A saved change reaches clients
- **WHEN** a staff member adds, edits, removes or restores a word
- **THEN** the language's pack version SHALL advance and a client pulling the pack afterwards SHALL receive the changed word list

#### Scenario: A failed change publishes nothing
- **WHEN** a catalog change is rejected
- **THEN** the language's published pack and version SHALL be unchanged

#### Scenario: Concurrent changes are not lost
- **WHEN** two staff members change different words of the same language at the same time
- **THEN** both changes SHALL be in the catalog and in the latest published pack

### Requirement: Published packs stay adoptable by the app
The system SHALL NOT publish a pack that the app's integrity check for fetched packs would reject. A catalog change whose resulting pack would fail that check SHALL be refused with a message that does not reveal daily-word information, and SHALL change nothing.

#### Scenario: Change that would break the pack is refused
- **WHEN** a catalog change would leave a language's pack failing the app's integrity check (for example with no remaining answers)
- **THEN** the system SHALL refuse the change, keep the previous published pack, and report that the change would break the word list

### Requirement: Staff work only in permitted languages
A WORDER SHALL see and change only the words of their assigned languages; an ADMIN SHALL see and change every language. A request for a language outside the staff member's scope SHALL be refused as forbidden and SHALL change nothing.

#### Scenario: WORDER sees only assigned languages
- **WHEN** a WORDER assigned to Russian opens the word pages
- **THEN** the panel SHALL offer only Russian

#### Scenario: Out-of-scope change is refused
- **WHEN** a WORDER assigned to Russian tries to add, edit, remove or restore a Kazakh word
- **THEN** the system SHALL refuse the request as forbidden and SHALL NOT change the catalog or any pack

#### Scenario: Scope change applies on the next request
- **WHEN** an ADMIN removes a language from a WORDER while the WORDER is signed in
- **THEN** the WORDER's next request for that language SHALL be refused

### Requirement: Word provenance is visible
For each catalog word the panel SHALL show its source (bundled dictionary, player suggestion, automatic acceptance, or staff), who added it and when (or that it predates staff tracking), who last edited it and when, and for a removed word who removed it and when.

#### Scenario: Staff-added word shows its author
- **WHEN** a staff member views a word added by another staff member
- **THEN** the panel SHALL show the source as staff, the adding staff member and the time

#### Scenario: Suggested word shows its origin
- **WHEN** a staff member views a word that entered the catalog from an accepted suggestion
- **THEN** the panel SHALL show whether it was accepted automatically or by an editor

### Requirement: Word screens reveal no daily-word information
Word catalog screens and responses SHALL NOT include whether a word is eligible to be a daily word, whether or when it was or will be a daily word, or any warning derived from that. Refusals and outcomes shown for catalog changes SHALL NOT disclose such information.

#### Scenario: WORDER word list has no daily fields
- **WHEN** a WORDER lists, searches or opens words
- **THEN** no response or screen SHALL contain daily eligibility, schedule dates or daily usage

#### Scenario: Removing a scheduled word shows no daily warning
- **WHEN** a WORDER removes or edits a word that is scheduled as a daily word
- **THEN** the outcome SHALL be reported like for any other word, without mentioning the schedule

### Requirement: Word changes are audited
Every catalog change — by staff, by an accepted suggestion, or by the startup dictionary merge — SHALL be recorded in the audit log with the actor, the language, the word, the action (added, restored, edited with previous spelling, removed, merged) and the time.

#### Scenario: Staff edit is audited
- **WHEN** a staff member edits a word's spelling
- **THEN** the audit log SHALL contain an entry naming that staff member, the language, the previous and new spelling, and the time

#### Scenario: System changes are audited
- **WHEN** a word is added by an automatic acceptance or by the startup dictionary merge
- **THEN** the audit log SHALL record the change as a system action
