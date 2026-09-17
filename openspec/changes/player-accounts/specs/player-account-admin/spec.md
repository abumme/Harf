## Purpose

Lets ADMINs find player accounts and act on them for support and moderation: inspect an account's state, delete it, end its sessions, block its word suggestions and clear its display name, with every action audited.

## ADDED Requirements

### Requirement: Only ADMINs administer player accounts
Searching, viewing and acting on player accounts SHALL be available only to a signed-in ADMIN. A WORDER SHALL be refused every player-account request as forbidden, and the panel SHALL NOT show a WORDER any navigation to player accounts. A request without a valid staff session SHALL be refused as unauthorized, including one carrying a valid player access token.

#### Scenario: WORDER is refused
- **WHEN** a signed-in WORDER requests a player search, a player's detail or any player action
- **THEN** the system SHALL refuse it as forbidden and SHALL NOT change any player data

#### Scenario: WORDER sees no Players section
- **WHEN** a WORDER is signed in to the panel
- **THEN** the navigation SHALL NOT include the Players section

#### Scenario: Player token is not a staff session
- **WHEN** a player-account request carries a valid player access token and no staff session
- **THEN** the system SHALL refuse it as unauthorized

### Requirement: Searching player accounts
An ADMIN SHALL be able to search player accounts by any combination of: exact account id, display name, account type, creation date range, and whether suggestions are blocked. Display-name search SHALL be case-insensitive and match part of the name. Account type SHALL be one of anonymous (no linked provider), Google, or Apple. An account linked to both providers matches both types. Results SHALL be ordered newest account first, paged with a page size of at most 100, and SHALL indicate whether more results follow. Each result SHALL show the account id, display name, linked provider types, creation time and whether suggestions are blocked.

#### Scenario: Exact account id
- **WHEN** an ADMIN searches with an existing account id
- **THEN** the results SHALL contain exactly that account

#### Scenario: Part of a display name, any case
- **WHEN** an ADMIN searches for `ali` and accounts are named `Alisher` and `Vali`
- **THEN** both accounts SHALL be returned

#### Scenario: Anonymous accounts only
- **WHEN** an ADMIN filters by the anonymous account type
- **THEN** every returned account SHALL have no linked provider

#### Scenario: Creation date range
- **WHEN** an ADMIN filters by a creation date range
- **THEN** only accounts created within that range SHALL be returned

#### Scenario: Blocked accounts only
- **WHEN** an ADMIN filters for accounts with suggestions blocked
- **THEN** only accounts whose suggestions are currently blocked SHALL be returned

#### Scenario: Paging newest first
- **WHEN** more accounts match than fit on one page
- **THEN** the first page SHALL hold the newest matching accounts, SHALL indicate that more results follow, and the next page SHALL continue without repeating or skipping accounts that existed when the search started

#### Scenario: Nothing matches
- **WHEN** no account matches the filters
- **THEN** the system SHALL return an empty page, not an error

### Requirement: Player account detail
An ADMIN SHALL be able to open one player account and see:
- its account id, creation time and display name
- the types of its linked providers
- the time of its most recently accepted stats snapshot
- how many active sessions it has: refresh tokens that are not revoked, not expired and not yet rotated
- per language with synced results: games played, wins, win rate, and current and best streak
- its suggestion counts by status and its most recent suggestions (word, language, status, submission time and decision time)
- whether its suggestions are blocked, and if so by which staff member and when

Games, wins, win rate and streaks SHALL follow the same rules the app uses on its stats screen, with the current streak judged against that language's current puzzle day. The detail SHALL NOT include provider subject identifiers, provider tokens, refresh tokens or access tokens.

#### Scenario: Linked account detail
- **WHEN** an ADMIN opens an account linked to Google that has synced results in `ru`
- **THEN** the detail SHALL show Google as a linked provider type, the `ru` games, wins, win rate, current and best streak, and SHALL NOT show the Google subject identifier

#### Scenario: Streak matches the app
- **WHEN** an account's synced `en` results are wins on the previous three puzzle days, including yesterday
- **THEN** the detail SHALL show a current `en` streak of 3, the same value the app's stats screen shows for those records

#### Scenario: Lapsed streak
- **WHEN** an account's most recent `en` win is two or more puzzle days before the current `en` puzzle day
- **THEN** the current `en` streak SHALL be 0 and the best streak SHALL keep its maximum

#### Scenario: Account without synced stats
- **WHEN** an ADMIN opens an account that never uploaded stats
- **THEN** the detail SHALL show no stats snapshot time and an empty per-language summary, not an error

#### Scenario: Unknown account
- **WHEN** an ADMIN opens an account id that does not exist
- **THEN** the system SHALL respond not found

### Requirement: Deleting a player account
An ADMIN SHALL be able to delete a player account with the same server-side effects as the player deleting it from the app:
- Apple tokens are revoked for any linked Apple identity
- the account, its linked identities, refresh tokens and stats are permanently deleted
- its suggestions are kept with the author cleared

The request SHALL carry an explicit confirmation naming the account, and a request whose confirmation does not match the account SHALL be refused without deleting anything. After deletion, refreshing with any of the account's refresh tokens SHALL be rejected. An access token issued before the deletion may still pass signature checks until it expires, at most 15 minutes, but the account SHALL NOT be recreated and no data SHALL be stored for it.

#### Scenario: Delete removes account data
- **WHEN** an ADMIN deletes an account with a matching confirmation
- **THEN** the account, its linked identities, refresh tokens and stats SHALL be gone, and searching for its id SHALL return nothing

#### Scenario: Apple identity is revoked
- **WHEN** an ADMIN deletes an account linked to an Apple identity
- **THEN** the Apple tokens for that identity SHALL be revoked as part of the deletion

#### Scenario: Suggestions outlive the account
- **WHEN** an ADMIN deletes an account that authored suggestions
- **THEN** those suggestions SHALL remain with no author

#### Scenario: Refresh fails after deletion
- **WHEN** the deleted account's app later tries to refresh its session
- **THEN** the refresh SHALL be rejected

#### Scenario: Confirmation mismatch
- **WHEN** an ADMIN requests deletion with a confirmation that does not name the account
- **THEN** the system SHALL refuse the request and SHALL NOT delete anything

#### Scenario: Panel asks for confirmation
- **WHEN** an ADMIN chooses to delete an account in the panel
- **THEN** the panel SHALL require the ADMIN to type the account's id before the delete can be submitted

### Requirement: Ending a player's sessions
An ADMIN SHALL be able to end all sessions of a player account. Every refresh token of the account SHALL be revoked, so any later refresh with them is rejected. Access tokens already issued SHALL remain usable until they expire, at most 15 minutes. The account, its identities, stats, display name and suggestions SHALL be unchanged.

#### Scenario: Refresh rejected after ending sessions
- **WHEN** an ADMIN ends a player's sessions and the player's app then tries to refresh
- **THEN** the refresh SHALL be rejected

#### Scenario: Account data unchanged
- **WHEN** an ADMIN ends a player's sessions
- **THEN** the account's linked identities, stats, display name and suggestions SHALL be unchanged and its active session count SHALL be 0

### Requirement: Blocking and unblocking suggestions
An ADMIN SHALL be able to block a player account from suggesting words, recording which staff member blocked it and when, and to unblock it. Both operations SHALL be idempotent. Blocking SHALL NOT change the account's existing suggestions, including pending ones, which stay reviewable. Blocking SHALL NOT affect playing, stats sync, linking or account deletion.

#### Scenario: Block records who and when
- **WHEN** an ADMIN blocks an account's suggestions
- **THEN** the account detail SHALL show suggestions as blocked, by that ADMIN, at that time

#### Scenario: Pending suggestions remain
- **WHEN** an ADMIN blocks an account that has pending suggestions
- **THEN** those suggestions SHALL stay pending and reviewable by editors

#### Scenario: Unblock allows suggesting again
- **WHEN** an ADMIN unblocks a blocked account
- **THEN** the account SHALL no longer be shown as blocked and its next valid suggestion SHALL be accepted for review

#### Scenario: Blocking twice
- **WHEN** an ADMIN blocks an account that is already blocked
- **THEN** the system SHALL report success and SHALL keep the original block's staff member and time

#### Scenario: Blocked player still syncs
- **WHEN** a blocked account uploads a stats snapshot
- **THEN** the upload SHALL be handled exactly as for an unblocked account

### Requirement: Clearing a display name
An ADMIN SHALL be able to clear a player account's display name. From then on, every author label for that account's suggestions, in Telegram messages and in the panel, SHALL show the anonymous label. Telegram messages already posted SHALL keep the label they were sent with. The player's device may keep showing its locally stored name until the player links an account again.

#### Scenario: Future author label is anonymous
- **WHEN** an ADMIN clears the display name of an account whose suggestion is later sent to editors
- **THEN** the Telegram message SHALL show the anonymous author label

#### Scenario: Name removed from the account
- **WHEN** an ADMIN clears an account's display name
- **THEN** the account detail and search results SHALL show no display name, and a display-name search for the old name SHALL NOT return the account

### Requirement: Player administration is audited
Every player-account action — deletion, ending sessions, blocking, unblocking and clearing a display name — SHALL record an audit entry naming the acting ADMIN, the action and the account id, recorded together with the action. Entries SHALL NOT contain tokens, provider subject identifiers or display names. Viewing and searching player accounts SHALL NOT create audit entries.

#### Scenario: Deletion is audited
- **WHEN** an ADMIN deletes a player account
- **THEN** an audit entry SHALL record that ADMIN, the deletion and the deleted account's id, and SHALL NOT contain its provider subject identifiers

#### Scenario: Clearing a name does not keep the name
- **WHEN** an ADMIN clears a display name
- **THEN** the audit entry SHALL record the action and the account id and SHALL NOT contain the removed name

#### Scenario: Refused action is not audited as done
- **WHEN** a deletion is refused because the confirmation does not match
- **THEN** no audit entry SHALL record the account as deleted
