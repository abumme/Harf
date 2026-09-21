## Purpose

Defines the ADMIN and WORDER staff roles and how the server enforces what each role, and each WORDER's assigned languages, may reach in the admin API and panel.

## ADDED Requirements

### Requirement: Staff roles
Every staff account SHALL have exactly one role, ADMIN or WORDER. An ADMIN SHALL be permitted every admin function in every language. A WORDER SHALL be permitted only the functions defined for WORDERs (word and suggestion work in their assigned languages, their own password, and their own audit entries) and SHALL be refused every other admin function, including staff management.

#### Scenario: ADMIN reaches staff management
- **WHEN** a signed-in ADMIN lists staff accounts
- **THEN** the system SHALL return the list

#### Scenario: WORDER is refused staff management
- **WHEN** a signed-in WORDER attempts to list, create or change staff accounts
- **THEN** the system SHALL refuse as forbidden and SHALL change nothing

### Requirement: WORDER language scope
A WORDER SHALL be assigned at least one language from the languages that have a word pack. Every language-specific admin request by a WORDER SHALL be checked on the server against their assigned languages; a request for any other language SHALL be refused as forbidden and SHALL change nothing, regardless of what the panel displays. An ADMIN SHALL have every language without assignment.

#### Scenario: Assigned language is allowed
- **WHEN** a WORDER assigned `ru` makes a language-specific request for `ru`
- **THEN** the system SHALL process it

#### Scenario: Other language is refused even if called directly
- **WHEN** a WORDER assigned only `ru` sends a language-specific request for `kk` without using the panel
- **THEN** the system SHALL refuse it as forbidden and SHALL change nothing

#### Scenario: Language list is scoped
- **WHEN** a WORDER assigned `uz-latn` and `uz-cyrl` asks for the languages available to them
- **THEN** the system SHALL return exactly `uz-latn` and `uz-cyrl`, while an ADMIN asking the same SHALL receive every language that has a word pack

### Requirement: Unauthenticated versus forbidden
An admin request without a valid staff session SHALL be refused as unauthenticated. A request with a valid session that its role or language scope does not permit SHALL be refused as forbidden. Neither refusal SHALL change any data.

#### Scenario: No session
- **WHEN** an admin request arrives without a staff session
- **THEN** the system SHALL refuse it as unauthenticated

#### Scenario: Session without permission
- **WHEN** a WORDER with a valid session requests the full audit log of all staff
- **THEN** the system SHALL refuse it as forbidden

### Requirement: Access changes take effect on the next request
The system SHALL evaluate a staff member's current status, role and languages on every admin request, so that disabling the account, changing the role, or changing the assigned languages applies to that member's very next request without requiring them to sign in again.

#### Scenario: Removed language is refused immediately
- **WHEN** an ADMIN removes `kk` from a signed-in WORDER's languages
- **THEN** that WORDER's next request for `kk` SHALL be refused as forbidden

#### Scenario: Demoted ADMIN loses ADMIN functions immediately
- **WHEN** an ADMIN changes another signed-in ADMIN's role to WORDER
- **THEN** that member's next staff-management request SHALL be refused as forbidden

#### Scenario: Disabled account loses access immediately
- **WHEN** an ADMIN disables a signed-in WORDER
- **THEN** that WORDER's next request SHALL be refused as unauthenticated

### Requirement: Staff learn their own permissions
A signed-in staff member SHALL be able to retrieve their own role, assigned languages and the set of admin sections they are permitted, so the panel can show only what they can use. This information SHALL NOT replace the server-side checks.

#### Scenario: WORDER sees WORDER sections only
- **WHEN** a signed-in WORDER retrieves their own permissions
- **THEN** the result SHALL include their languages and SHALL NOT include staff management or the full audit log
