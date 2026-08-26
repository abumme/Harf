## Purpose

Defines how players get a persistent identity in Harf - starting anonymously on first launch and optionally linking a Google or Apple account for cross-device access - along with how sessions are issued, refreshed, and revoked.

## ADDED Requirements

### Requirement: Anonymous account creation
The system SHALL allow a client to obtain a valid account and session without providing any credentials.

#### Scenario: First launch creates an anonymous account
- **WHEN** a client calls the anonymous account creation endpoint with no prior account
- **THEN** the system creates a new account with an anonymous identity and returns a valid access token and refresh token for it

#### Scenario: Anonymous account is immediately usable
- **WHEN** a client has just received tokens from anonymous account creation
- **THEN** the client SHALL be able to call authenticated endpoints (e.g. stats sync) using the returned access token without any further setup

#### Scenario: Offline first launch does not block gameplay
- **WHEN** the app is launched for the first time without network connectivity
- **THEN** the client SHALL allow full offline gameplay without an account and SHALL retry anonymous account creation on a later launch or foreground with connectivity

### Requirement: Linking an anonymous account to Google or Apple
The system SHALL allow an authenticated anonymous account to be linked to a Google or Apple identity, so the account becomes accessible from other devices via that provider.

#### Scenario: Successful link to a new provider identity
- **WHEN** an authenticated anonymous account submits a valid Google or Apple identity token that is not yet linked to any account
- **THEN** the system attaches that provider identity to the account and the account is no longer purely anonymous

#### Scenario: Link to an identity already owned by another account
- **WHEN** an authenticated anonymous account submits a valid provider identity token that is already linked to a different, existing account
- **THEN** the system SHALL authenticate the session as the pre-existing account rather than creating a duplicate identity, and SHALL delete the orphaned anonymous account and its data

#### Scenario: Client adopts server data after a merge-link before uploading
- **WHEN** a link response has switched the client's session to a pre-existing account
- **THEN** the client SHALL download and adopt the server's stats snapshot before performing any stats upload, so the pre-existing account's data wins

#### Scenario: Invalid or expired provider token is rejected
- **WHEN** a client submits a Google or Apple identity token that fails signature, audience, issuer, or expiry verification
- **THEN** the system SHALL reject the link request and SHALL NOT modify any account

### Requirement: Platform-scoped provider availability
The system SHALL only offer Apple sign-in on iOS, the only platform with a supported native sign-in flow; Google sign-in SHALL be available on Android, iOS, and JVM desktop.

#### Scenario: Apple sign-in unavailable on desktop
- **WHEN** the client is running on the JVM desktop platform
- **THEN** the client SHALL NOT present Apple as a sign-in/link option, while Google sign-in remains available

#### Scenario: Apple sign-in unavailable on Android
- **WHEN** the client is running on Android
- **THEN** the client SHALL NOT present Apple as a sign-in/link option, while Google sign-in remains available

### Requirement: Access and refresh token issuance
The system SHALL issue a short-lived access token and a longer-lived refresh token together whenever an account is created, linked, or a session is refreshed.

#### Scenario: Access token expires
- **WHEN** a client calls an authenticated endpoint with an expired access token
- **THEN** the system SHALL reject the request as unauthorized

#### Scenario: Refresh exchanges a valid refresh token for a new session
- **WHEN** a client submits a valid, non-revoked refresh token
- **THEN** the system SHALL issue a new access token and a new refresh token, and SHALL invalidate the submitted refresh token for future use

### Requirement: Refresh token reuse detection with retry grace
The system SHALL detect and respond to reuse of an already-rotated (previously exchanged) refresh token as a potential compromise, while tolerating a network-level retry of a recent rotation.

#### Scenario: Retry within the grace window returns the same replacement
- **WHEN** a client re-submits a refresh token that was rotated within the grace window (e.g. the rotation response was lost in transit)
- **THEN** the system SHALL return the already-issued replacement token pair without performing another rotation or revoking anything

#### Scenario: Reused refresh token outside the grace window is rejected and session revoked
- **WHEN** a client submits a refresh token that was exchanged for a newer one earlier than the grace window allows
- **THEN** the system SHALL reject the request and SHALL revoke the entire refresh token chain for that account, requiring re-authentication

### Requirement: Logout
The system SHALL allow an authenticated client to revoke the account's refresh tokens, signing the account out of all devices.

#### Scenario: Logout revokes all refresh tokens
- **WHEN** an authenticated client calls the logout endpoint
- **THEN** the system SHALL revoke all refresh tokens belonging to the account, and subsequent refresh attempts with any of them SHALL be rejected

### Requirement: Account deletion
The system SHALL allow an authenticated client to permanently delete its account and all associated data, and the client SHALL expose this in its settings UI.

#### Scenario: Deleting an account removes all server-side data
- **WHEN** an authenticated client requests account deletion
- **THEN** the system SHALL permanently delete the account, its linked provider identities, refresh tokens, and stats, and the deleted account's tokens SHALL no longer authenticate

#### Scenario: Client returns to a fresh state after deletion
- **WHEN** account deletion succeeds
- **THEN** the client SHALL clear its stored tokens and behave as a fresh install (a new anonymous account may be created later)
