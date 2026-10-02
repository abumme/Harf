## Purpose

Lets ADMIN and WORDER staff sign in to the admin panel with a username and password, keeps their sessions secure and bounded, and keeps staff access completely separate from player accounts.

## Requirements

### Requirement: Staff sign in with username and password
The system SHALL let an active staff account sign in with its username and password and SHALL start a session for that account on success. Usernames SHALL be matched case-insensitively. A sign-in with an unknown username, a wrong password, or a disabled account SHALL be refused with one identical generic failure that does not reveal which of these applied.

#### Scenario: Correct credentials start a session
- **WHEN** an active staff member submits their username and correct password
- **THEN** the system SHALL start a session for that account and SHALL report the account's username, display name, role and languages

#### Scenario: Username case does not matter
- **WHEN** a staff member whose username is `aziz` signs in as `Aziz` with the correct password
- **THEN** the system SHALL sign them in

#### Scenario: Wrong password and unknown username look the same
- **WHEN** a sign-in is attempted once with an existing username and a wrong password, and once with a username that does not exist
- **THEN** both attempts SHALL be refused with the same failure response and message

#### Scenario: Disabled account cannot sign in
- **WHEN** a disabled staff account submits its correct password
- **THEN** the system SHALL refuse with the same generic failure and SHALL NOT start a session

### Requirement: Account lockout after repeated failures
The system SHALL lock a staff account for 15 minutes after 5 consecutive failed sign-in attempts for it. While locked, the account SHALL NOT be able to sign in even with the correct password, and the refusal SHALL say that sign-in is temporarily blocked. A successful sign-in SHALL reset the failure count. An ADMIN resetting the account's password SHALL clear the lock.

#### Scenario: Fifth failure locks the account
- **WHEN** 5 consecutive sign-in attempts for one account fail
- **THEN** the account SHALL be locked for 15 minutes and a sixth attempt with the correct password SHALL be refused as temporarily blocked

#### Scenario: Lock expires
- **WHEN** 15 minutes have passed since an account was locked
- **THEN** the account SHALL be able to sign in with its correct password

#### Scenario: Success resets the count
- **WHEN** an account has 4 consecutive failures and then signs in successfully
- **THEN** its failure count SHALL return to zero, so one later failure does not lock it

#### Scenario: Password reset clears the lock
- **WHEN** an ADMIN resets the password of a locked account
- **THEN** the account SHALL be able to sign in immediately with the new password

### Requirement: Sign-in rate limit per network address
The system SHALL accept no more than 10 sign-in attempts per minute from one client network address, and SHALL refuse further attempts from that address as rate limited until the window allows them again. When the deployment runs behind the trusted reverse proxy, the client address SHALL be the one the proxy reports, not the proxy's own address.

#### Scenario: Eleventh attempt in a minute is refused
- **WHEN** one network address makes 11 sign-in attempts within one minute
- **THEN** the 11th attempt SHALL be refused as rate limited without checking the credentials

#### Scenario: Different clients behind the proxy are limited separately
- **WHEN** two different client addresses reach the server through the trusted reverse proxy and each makes 10 attempts within one minute
- **THEN** neither SHALL be rate limited

### Requirement: Session lifetime
A staff session SHALL end when it has been idle for 12 hours, when 7 days have passed since sign-in, or when it is revoked. A request made with an ended session SHALL be treated as unauthenticated.

#### Scenario: Idle session expires
- **WHEN** a session has made no request for more than 12 hours
- **THEN** its next request SHALL be refused as unauthenticated

#### Scenario: Active session still ends after 7 days
- **WHEN** a session has been used continuously but was started more than 7 days ago
- **THEN** its next request SHALL be refused as unauthenticated

#### Scenario: Activity keeps a session alive
- **WHEN** a session makes a request every few hours for 2 days
- **THEN** its requests SHALL keep succeeding

### Requirement: Session revocation
The system SHALL end a session when its staff member signs out. It SHALL end all sessions of a staff member immediately when an ADMIN resets that member's password or disables the account. When staff members change their own password, the system SHALL end all their other sessions and keep the session that made the change.

#### Scenario: Sign-out ends the session
- **WHEN** a staff member signs out
- **THEN** further requests with that session SHALL be refused as unauthenticated

#### Scenario: ADMIN password reset signs the member out everywhere
- **WHEN** an ADMIN resets a WORDER's password while the WORDER has two active sessions
- **THEN** both sessions' next requests SHALL be refused as unauthenticated

#### Scenario: Own password change keeps the current session
- **WHEN** a staff member with sessions in two browsers changes their password from one of them
- **THEN** the browser that made the change SHALL stay signed in and the other SHALL be signed out

### Requirement: Own password change
A signed-in staff member SHALL be able to change their own password by providing their current password and a new password of 12 to 128 characters. A wrong current password or an out-of-range new password SHALL change nothing.

#### Scenario: Password changed
- **WHEN** a staff member submits their correct current password and a valid new password
- **THEN** the new password SHALL be required at their next sign-in and the old one SHALL no longer work

#### Scenario: Wrong current password
- **WHEN** a staff member submits a wrong current password
- **THEN** the system SHALL refuse the change and the old password SHALL keep working

#### Scenario: New password too short
- **WHEN** a staff member submits a new password of 11 characters
- **THEN** the system SHALL refuse the change as invalid

### Requirement: Anti-forgery protection
Every state-changing admin request made within a session SHALL carry an anti-forgery token bound to that session; a state-changing request with a missing or wrong token SHALL be refused and SHALL change nothing. Read-only requests SHALL NOT require the token.

#### Scenario: Request without the token is refused
- **WHEN** a signed-in browser sends a state-changing admin request without the anti-forgery token
- **THEN** the system SHALL refuse it as forbidden and SHALL NOT apply the change

#### Scenario: Token from another session is refused
- **WHEN** a state-changing request carries a valid session together with the anti-forgery token of a different session
- **THEN** the system SHALL refuse it as forbidden

#### Scenario: Reading does not need the token
- **WHEN** a signed-in browser reads the audit log without an anti-forgery token
- **THEN** the system SHALL return the entries

### Requirement: Staff access is separate from player accounts
Admin functions SHALL be reachable only with a staff session. A player access token SHALL NOT authenticate any admin request, and staff sign-in SHALL NOT create, change or require a player account.

#### Scenario: Player token is refused
- **WHEN** a request to an admin function carries a valid player access token and no staff session
- **THEN** the system SHALL refuse it as unauthenticated

#### Scenario: Player sign-in is unaffected
- **WHEN** staff accounts exist and staff are signed in
- **THEN** anonymous player accounts, Google and Apple linking, and player token refresh SHALL behave exactly as before

### Requirement: First ADMIN from deployment configuration
When no active ADMIN account exists at startup and the deployment configures a bootstrap username and password, the system SHALL create an active ADMIN with them; if a staff account with that username already exists, it SHALL instead make that account an active ADMIN with that password and clear its lock. When an active ADMIN already exists, the bootstrap configuration SHALL be ignored. A bootstrap password outside the password rules SHALL be ignored with a logged error, and the server SHALL still start.

#### Scenario: Empty system gets its first ADMIN
- **WHEN** the server starts with no staff accounts and a valid bootstrap username and password configured
- **THEN** that username SHALL be able to sign in as an active ADMIN

#### Scenario: Recovery when every ADMIN is disabled
- **WHEN** the server starts with no active ADMIN, the bootstrap username naming an existing disabled WORDER, and a valid bootstrap password
- **THEN** that account SHALL become an active ADMIN that signs in with the bootstrap password

#### Scenario: Existing ADMIN makes bootstrap a no-op
- **WHEN** the server starts with an active ADMIN and a bootstrap username and password configured
- **THEN** no account SHALL be created or changed

#### Scenario: Invalid bootstrap password does not stop the server
- **WHEN** the server starts with no active ADMIN and a bootstrap password shorter than 12 characters
- **THEN** no account SHALL be created, an error SHALL be logged, and the player API SHALL still serve requests
