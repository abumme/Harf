## Purpose

The browser-based staff panel for ADMINs and WORDERs: where it is reachable, how staff sign in and out, and how its pages and navigation follow the signed-in staff member's role.

## ADDED Requirements

### Requirement: Panel is served on the API's origin
The system SHALL serve the admin panel from the same origin as the backend API, under the deployment's admin path (in production `https://api.lazydevs.uz/harf/admin/`), over HTTPS. Opening any panel page address directly, including after a browser reload, SHALL load the panel at that page. Panel pages SHALL NOT be displayable inside a frame on another page.

#### Scenario: Panel opens at its address
- **WHEN** a browser opens the admin panel address
- **THEN** the panel SHALL load, showing the login page if no staff member is signed in

#### Scenario: Reload keeps the page
- **WHEN** a signed-in staff member reloads the browser on the staff management page
- **THEN** the panel SHALL load the staff management page again

#### Scenario: Page address opened directly
- **WHEN** a signed-in staff member opens the audit log page's address in a new browser tab
- **THEN** the panel SHALL load and show the audit log page

#### Scenario: Unknown panel address
- **WHEN** a browser opens an address under the admin path that matches no panel page
- **THEN** the panel SHALL load and show a not-found page in Russian instead of a server error

#### Scenario: Framing is refused
- **WHEN** another page tries to show the admin panel inside a frame
- **THEN** the browser SHALL be instructed not to display it

### Requirement: Login page
The panel SHALL present a login page with username and password fields. On success it SHALL take the staff member to their home page, or to the page they originally requested. On failure it SHALL show the generic failure message, or the temporarily blocked or rate-limited message when the server reports those, and SHALL keep the entered username.

#### Scenario: Successful login goes home
- **WHEN** a staff member signs in from the login page without having requested another page
- **THEN** the panel SHALL show that member's role-based home page

#### Scenario: Failed login keeps the username
- **WHEN** a sign-in fails with wrong credentials
- **THEN** the login page SHALL show the generic failure message and keep the username field filled

### Requirement: Role-based home page
After sign-in, an ADMIN's home SHALL be the dashboard and a WORDER's home SHALL be the words section. Until the sections that fill them exist, both SHALL be placeholder pages that name the signed-in member and their role.

#### Scenario: ADMIN lands on the dashboard
- **WHEN** an ADMIN signs in
- **THEN** the panel SHALL show the dashboard page

#### Scenario: WORDER lands on words
- **WHEN** a WORDER signs in
- **THEN** the panel SHALL show the words page listing the WORDER's languages

### Requirement: Navigation shows only permitted sections
The panel SHALL show navigation entries only for sections the signed-in staff member is permitted. An ADMIN SHALL see the dashboard, staff, audit log and account sections; a WORDER SHALL see the words, their own activity and account sections. Opening the address of a section the member is not permitted SHALL show a not-permitted page instead of the section.

#### Scenario: WORDER does not see staff management
- **WHEN** a WORDER is signed in
- **THEN** the navigation SHALL NOT show a staff section

#### Scenario: WORDER opens the staff address directly
- **WHEN** a signed-in WORDER opens the staff section's address
- **THEN** the panel SHALL show a not-permitted page and no staff data

### Requirement: Session expiry returns to login
When the server reports that the session is no longer valid, the panel SHALL show the login page and, after the staff member signs in again, SHALL return them to the page they were on.

#### Scenario: Expired session mid-work
- **WHEN** a staff member's session has expired and they open the audit log page
- **THEN** the panel SHALL show the login page, and after a successful sign-in SHALL show the audit log page

### Requirement: Sign-out
The panel SHALL offer sign-out on every page. Signing out SHALL end the session on the server and show the login page; using the browser's back button afterwards SHALL NOT show staff data.

#### Scenario: Back after sign-out
- **WHEN** a staff member signs out and then presses the browser back button
- **THEN** the panel SHALL show the login page and no previously loaded staff data

### Requirement: Staff management pages
For an ADMIN, the panel SHALL provide pages to list staff accounts, create an account, edit display name, role, languages and Telegram user ID, reset a password, and disable or re-enable an account. Disabling an account and resetting a password SHALL ask for confirmation first. Validation failures reported by the server SHALL be shown next to the related field.

#### Scenario: Create a WORDER from the panel
- **WHEN** an ADMIN fills in username, password, role WORDER and at least one language and submits
- **THEN** the new account SHALL appear in the staff list

#### Scenario: Disable asks for confirmation
- **WHEN** an ADMIN chooses to disable an account
- **THEN** the panel SHALL ask for confirmation and SHALL disable the account only after the ADMIN confirms

#### Scenario: Duplicate username shown on the field
- **WHEN** the server refuses a new account because the username exists
- **THEN** the panel SHALL show the error next to the username field

### Requirement: Audit log page
The panel SHALL provide an audit log page listing entries newest first with filters for actor, action, language and date range and with paging. For an ADMIN it SHALL list all entries; for a WORDER it SHALL list only their own activity and SHALL NOT offer the actor filter.

#### Scenario: ADMIN filters the log
- **WHEN** an ADMIN selects an actor and a date range on the audit log page
- **THEN** the page SHALL list only matching entries

#### Scenario: WORDER activity page
- **WHEN** a WORDER opens their activity page
- **THEN** it SHALL list only entries where they acted, without an actor filter

### Requirement: Account page
The panel SHALL provide an account page where the signed-in staff member sees their username, role and languages and changes their own password by entering the current password and the new password twice. The page SHALL refuse to submit when the two new passwords differ.

#### Scenario: Mismatched new passwords
- **WHEN** a staff member enters two different new passwords
- **THEN** the page SHALL not submit and SHALL show that the passwords do not match

#### Scenario: Password changed from the account page
- **WHEN** a staff member submits a correct current password and matching valid new passwords
- **THEN** the page SHALL confirm the change and the member SHALL remain signed in

### Requirement: Russian interface
All panel text shown to staff, including labels, messages and errors, SHALL be in Russian.

#### Scenario: Login page language
- **WHEN** the login page is shown
- **THEN** its labels, buttons and messages SHALL be in Russian
