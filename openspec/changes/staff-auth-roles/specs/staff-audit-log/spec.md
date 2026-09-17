## Purpose

Keeps a permanent, append-only record of staff sign-ins and staff actions so the team can see who changed what and when, with visibility limited by role.

## ADDED Requirements

### Requirement: Staff activity is recorded
The system SHALL record an audit entry for every staff sign-in success, sign-in failure, account lock, sign-out and own password change, for every staff-management action (create, edit, password reset, disable, enable), and for every bootstrap of an ADMIN from configuration. Each entry SHALL state when it happened, who acted (a staff account, or the system), the action, the affected account or object when there is one, the language when the action concerns one, and a summary of what changed. An action and its audit entry SHALL be recorded together: an action that fails SHALL leave no entry claiming it succeeded, and a successful action SHALL always have its entry.

#### Scenario: Staff creation is recorded
- **WHEN** an ADMIN creates a WORDER with languages `ru` and `kk`
- **THEN** an entry SHALL record the ADMIN as actor, the creation, the new account, and its role and languages

#### Scenario: Language change records before and after
- **WHEN** an ADMIN changes a WORDER's languages from `ru` to `ru` and `kk`
- **THEN** the entry SHALL show the languages before and after the change

#### Scenario: Failed sign-in is recorded
- **WHEN** a sign-in attempt for an existing account fails
- **THEN** an entry SHALL record the failure for that account

#### Scenario: Bootstrap is recorded as a system action
- **WHEN** the server creates an ADMIN from bootstrap configuration
- **THEN** an entry SHALL record the system as actor and the affected account

#### Scenario: Refused action leaves no success entry
- **WHEN** an ADMIN's attempt to disable the last active ADMIN is refused
- **THEN** no entry SHALL record that account as disabled

### Requirement: Secrets are never recorded
Audit entries SHALL NOT contain passwords, password hashes, session tokens or anti-forgery tokens, including for password changes, password resets and failed sign-ins. A failed sign-in for a username that does not exist SHALL NOT record the attempted username.

#### Scenario: Password reset entry has no password
- **WHEN** an ADMIN resets a staff password
- **THEN** the entry SHALL record that a reset happened for that account and SHALL NOT contain the new password or its hash

#### Scenario: Unknown username not stored
- **WHEN** a sign-in is attempted with a username that does not exist
- **THEN** any entry recorded for it SHALL NOT contain the attempted username

### Requirement: Audit log is append-only
The system SHALL offer no way, in the panel or the admin API, to edit or delete audit entries, and SHALL keep them when the acting or affected staff account is disabled.

#### Scenario: Disabled account's history remains
- **WHEN** a WORDER who made changes is disabled
- **THEN** the entries recording that WORDER's actions SHALL still be listed with that WORDER as actor

### Requirement: Audit visibility by role
An ADMIN SHALL be able to list all audit entries, newest first, filtered by actor, action, language and date range, and paged. A WORDER SHALL be able to list only entries where they are the actor, with the same filters, and SHALL be refused any request for other staff members' entries.

#### Scenario: ADMIN filters by actor and language
- **WHEN** an ADMIN lists entries for one WORDER in language `ru` over the last 7 days
- **THEN** the system SHALL return only that WORDER's `ru` entries from that period, newest first

#### Scenario: WORDER sees only their own entries
- **WHEN** a WORDER lists audit entries
- **THEN** every returned entry SHALL have that WORDER as actor

#### Scenario: WORDER asks for another actor
- **WHEN** a WORDER lists audit entries filtered by another staff member as actor
- **THEN** the system SHALL refuse it as forbidden
