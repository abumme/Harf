## ADDED Requirements

### Requirement: Confirming a display name when linking
After a successful native sign-in and before the link is finalized, the client SHALL present a name-confirmation step — an editable name field prefilled with the provider's suggested name and a confirm action — and the confirmed name SHALL be persisted with the account as its display name. A name is required: the confirm action SHALL be unavailable while the field is blank.

#### Scenario: Google name prefills the confirmation field
- **WHEN** the user signs in with Google and the confirmation step opens
- **THEN** the name field SHALL be prefilled with the name the Google sign-in provided

#### Scenario: Apple name prefills on first sign-in
- **WHEN** the user signs in with Apple for the first time (Apple returns the name only once, and not inside the token) and the confirmation step opens
- **THEN** the name field SHALL be prefilled with the name the Apple sign-in provided

#### Scenario: Prefill falls back to the existing name when the provider gives none
- **WHEN** the confirmation step opens and the provider supplied no name (e.g. a later Apple sign-in) but the account already has a stored display name
- **THEN** the name field SHALL be prefilled with the account's existing stored name

#### Scenario: User confirms the suggested name unchanged
- **WHEN** the user confirms without editing the prefilled name
- **THEN** the system SHALL persist that name as the account's display name

#### Scenario: User edits the name before confirming
- **WHEN** the user edits the name field and then confirms
- **THEN** the system SHALL persist the edited name as the account's display name, ignoring the provider's suggestion

#### Scenario: A blank name cannot be confirmed
- **WHEN** the name field is empty
- **THEN** the confirm action SHALL be unavailable and the link SHALL NOT be finalized until a non-blank name is entered

#### Scenario: Cancelling the confirmation leaves the session unchanged
- **WHEN** the user dismisses the confirmation step without confirming
- **THEN** the account SHALL NOT be linked and the current session SHALL remain unchanged

#### Scenario: Link response returns the confirmed name
- **WHEN** the link request with a confirmed name succeeds
- **THEN** the response SHALL include the confirmed display name so the client can store and display it

#### Scenario: Client shows the confirmed name in the account UI
- **WHEN** the client holds a session with a stored display name
- **THEN** the settings account section SHALL present the name (e.g. "Signed in as X") instead of only a generic linked state

### Requirement: Confirmed display name on merge-link
When a linked identity already belongs to a pre-existing account and the session adopts that account, the system SHALL apply the user-confirmed display name to the adopted account, since the name was explicitly confirmed by the user during this link.

#### Scenario: Confirmed name is applied to the adopted account
- **WHEN** a link switches the session to a pre-existing account and the user confirmed a display name during the link
- **THEN** the system SHALL store the confirmed name as the adopted account's display name and return it in the link response
