## Purpose

Lets an ADMIN create and maintain the staff accounts — other ADMINs and the WORDERs with their languages — that can use the admin panel, while keeping at least one active ADMIN at all times.

## Requirements

### Requirement: ADMIN lists staff accounts
An ADMIN SHALL be able to list all staff accounts, each showing username, display name, role, status, assigned languages, Telegram user ID when set, whether it is currently locked, and when it last signed in. The list SHALL never include password material.

#### Scenario: Staff list shows account state
- **WHEN** an ADMIN lists staff accounts
- **THEN** each entry SHALL show username, display name, role, status, languages, Telegram user ID, lock state and last sign-in time, and no password or password hash

### Requirement: ADMIN creates staff accounts
An ADMIN SHALL be able to create a staff account by entering a username, a password, a role, the languages (required, at least one, for a WORDER), an optional display name and an optional Telegram user ID. The new account SHALL be active and able to sign in with that password immediately. A username SHALL be 3 to 32 characters of lowercase Latin letters, digits, `.`, `_` or `-` (uppercase input is stored lowercase) and SHALL be unique ignoring case. A password SHALL be 12 to 128 characters. A display name SHALL be at most 64 characters. A Telegram user ID SHALL be unique among staff. Languages SHALL be languages that have a word pack. Any violated rule SHALL create nothing and SHALL name the invalid field.

#### Scenario: WORDER created
- **WHEN** an ADMIN creates a WORDER with username `dilnoza`, a 14-character password and languages `uz-latn` and `uz-cyrl`
- **THEN** `dilnoza` SHALL be able to sign in immediately as a WORDER limited to those two languages

#### Scenario: Duplicate username ignoring case
- **WHEN** an ADMIN creates an account named `Dilnoza` while `dilnoza` exists
- **THEN** the system SHALL refuse it as a conflict and create nothing

#### Scenario: WORDER without languages
- **WHEN** an ADMIN creates a WORDER with no languages
- **THEN** the system SHALL refuse it as invalid, naming the languages field

#### Scenario: Unknown language
- **WHEN** an ADMIN creates a WORDER with a language that has no word pack
- **THEN** the system SHALL refuse it as invalid

#### Scenario: Telegram user ID already used
- **WHEN** an ADMIN creates an account with a Telegram user ID that another staff account already has
- **THEN** the system SHALL refuse it as a conflict

### Requirement: ADMIN edits staff accounts
An ADMIN SHALL be able to change a staff account's display name, role, languages and Telegram user ID, subject to the same rules as creation. The username SHALL NOT be changeable. Changing an account to WORDER SHALL require at least one language.

#### Scenario: Languages changed
- **WHEN** an ADMIN changes a WORDER's languages from `ru` to `ru` and `kk`
- **THEN** the WORDER SHALL be permitted `kk` from their next request

#### Scenario: Promoted to ADMIN
- **WHEN** an ADMIN changes a WORDER's role to ADMIN
- **THEN** that account SHALL be permitted every admin function in every language from its next request

#### Scenario: Telegram user ID cleared
- **WHEN** an ADMIN clears a staff account's Telegram user ID
- **THEN** the account SHALL have no Telegram user ID

### Requirement: ADMIN resets a staff password
An ADMIN SHALL be able to set a new password (12 to 128 characters) for any staff account without knowing the old one. The reset SHALL end all of that account's sessions and clear any lock. The ADMIN SHALL NOT be able to read any existing password.

#### Scenario: Reset password works immediately
- **WHEN** an ADMIN sets a new password for a WORDER
- **THEN** the WORDER SHALL sign in with the new password, the old password SHALL no longer work, and the WORDER's existing sessions SHALL be ended

### Requirement: ADMIN disables and re-enables staff accounts
An ADMIN SHALL be able to disable a staff account, which ends all its sessions and prevents sign-in, and to re-enable it, which allows sign-in again with its existing password. Staff accounts SHALL never be deleted.

#### Scenario: Disabled account is shut out
- **WHEN** an ADMIN disables a WORDER
- **THEN** the WORDER's sessions SHALL end and sign-in SHALL be refused

#### Scenario: Re-enabled account signs in again
- **WHEN** an ADMIN re-enables a previously disabled WORDER
- **THEN** the WORDER SHALL be able to sign in with their existing password

#### Scenario: No deletion
- **WHEN** an ADMIN manages a staff account
- **THEN** the system SHALL offer no way to delete it

### Requirement: At least one active ADMIN
The system SHALL refuse any disable or role change that would leave no active ADMIN account, including an ADMIN acting on their own account, and SHALL change nothing when refusing.

#### Scenario: Last ADMIN cannot disable themselves
- **WHEN** the only active ADMIN tries to disable their own account
- **THEN** the system SHALL refuse it and the account SHALL stay an active ADMIN

#### Scenario: Last ADMIN cannot be demoted
- **WHEN** the only active ADMIN tries to change their own role to WORDER
- **THEN** the system SHALL refuse it

#### Scenario: One of two ADMINs can be disabled
- **WHEN** two active ADMINs exist and one disables the other
- **THEN** the system SHALL disable that account
