# account-auth Specification

## Purpose
Defines how players get a persistent identity in Harf - starting anonymously on first launch and optionally linking a Google or Apple account for cross-device access - along with how sessions are issued, refreshed, and revoked.

## Requirements

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

### Requirement: Automatic session refresh on unauthorized responses
The system SHALL return an unauthorized response that the client can decode without ambiguity, and the client SHALL automatically refresh an expired session and retry the original request rather than surfacing a network error.

#### Scenario: Server returns a decodable unauthorized response
- **WHEN** a protected endpoint is called without a valid access token
- **THEN** the server SHALL respond `401` with a body the client can distinguish as an authentication challenge (not an empty, untyped body)

#### Scenario: Expired access token triggers a transparent refresh
- **WHEN** a client calls a protected endpoint with an access token that has expired but holds a valid refresh token
- **THEN** the client SHALL detect the unauthorized response, refresh the session, and retry the original request without reporting a failure to the caller

#### Scenario: Empty or bodyless 401 does not become a network error
- **WHEN** the client receives a `401` with an empty body or no content type
- **THEN** the client SHALL treat it as unauthorized (and attempt refresh) and SHALL NOT misclassify it as a network/parse error

#### Scenario: Concurrent protected calls share a single refresh
- **WHEN** multiple protected requests hit an expired access token at the same time
- **THEN** the client SHALL perform at most one refresh and apply the resulting session to all waiting requests, never rotating the refresh token more than once for the same expiry

### Requirement: Resilient account deletion
The client SHALL preserve the account session and local data until the server confirms deletion succeeded, and SHALL surface a retryable error otherwise.

#### Scenario: Deletion failure keeps the account usable
- **WHEN** an account deletion request fails (server error, network loss, or non-success response)
- **THEN** the client SHALL keep the current session, stored tokens, and local stats/rounds intact and SHALL present a retryable error to the user

#### Scenario: Deletion success clears local state only after confirmation
- **WHEN** the server confirms the account and its data were deleted
- **THEN** the client SHALL clear its stored tokens, stats, and in-progress/finished round state, and behave as a fresh install

#### Scenario: Apple identities are revoked on deletion
- **WHEN** an account linked to an Apple identity is deleted
- **THEN** the server SHALL revoke the associated Apple tokens as part of deletion, so the provider no longer treats the app as authorized for that user

### Requirement: Merge-link discards only an anonymous account
When a linked identity already belongs to another account, the system SHALL adopt the pre-existing account without deleting any account that is not purely anonymous.

#### Scenario: Only an anonymous account is discarded on identity collision
- **WHEN** an authenticated account submits a provider identity already owned by a different account
- **THEN** the system SHALL switch the session to the pre-existing account and SHALL delete the caller's account only if that caller account is still purely anonymous (no other linked identity)

#### Scenario: An already-linked account is never silently deleted
- **WHEN** the caller's account already has its own linked provider identity and submits an identity owned by another account
- **THEN** the system SHALL NOT delete the caller's account and SHALL reject or safely no-op the conflicting link instead of destroying data

#### Scenario: Client adopts server data before uploading after a merge
- **WHEN** a link response has switched the client to a pre-existing account
- **THEN** the client SHALL download and adopt the server's stats snapshot before performing any upload, so the pre-existing account's data wins over the local mix

### Requirement: Provider token audience and nonce validation
The system SHALL verify that a provider identity token was minted for this app's audience and is bound to a nonce the server can verify, in addition to signature, issuer, and expiry.

#### Scenario: Token for another app's audience is rejected
- **WHEN** a provider identity token whose `aud` is not in the server's configured allowlist of this app's audiences is submitted
- **THEN** the system SHALL reject the request even if the signature, issuer, and expiry are valid

#### Scenario: Token missing an audience claim is rejected
- **WHEN** a provider identity token carries no `aud` claim
- **THEN** the system SHALL reject the request rather than accepting an unscoped token

#### Scenario: Nonce mismatch is rejected
- **WHEN** the nonce embedded in a provider identity token does not match the nonce the server associated with the request
- **THEN** the system SHALL reject the request, preventing replay of a token captured from another session

### Requirement: Secure production session configuration
The system SHALL refuse to run with insecure session configuration in production, protect short-lived secrets, and SHALL NOT leak internal error detail to clients.

#### Scenario: Missing JWT secret fails startup outside dev mode
- **WHEN** the server starts without a configured `JWT_SECRET` and is not in an explicit development mode
- **THEN** startup SHALL fail fast rather than silently using a known development secret

#### Scenario: Grace-window replacement token is protected and expires
- **WHEN** a refresh token is rotated and a replacement is held for the retry grace window
- **THEN** the stored replacement value SHALL be protected (not retained in plaintext beyond need) and SHALL be cleared once the grace window's TTL elapses

#### Scenario: Internal errors do not expose detail to clients
- **WHEN** an unexpected server error occurs while handling a request
- **THEN** the client-facing response SHALL NOT include internal exception detail, while the detail is retained in server logs

### Requirement: Actionable native sign-in and logout in the client
The client SHALL perform a real native sign-in that yields a provider identity token or a typed cancellation/error, and SHALL expose a logout action for the existing server endpoint.

#### Scenario: Sign-in returns a real token or a typed outcome
- **WHEN** the user taps a sign-in/link option on a platform with a supported native flow
- **THEN** the client SHALL launch the native provider flow and return either a valid provider identity token or a typed cancellation/error, and SHALL NOT silently do nothing

#### Scenario: Cancellation is surfaced, not swallowed
- **WHEN** the user cancels the native sign-in flow
- **THEN** the client SHALL report a cancellation state and leave the current session unchanged

#### Scenario: Logout is available and clears the session
- **WHEN** an authenticated user invokes logout from settings
- **THEN** the client SHALL call the logout endpoint, clear the local session and any provider credential state, and reflect the signed-out state in the UI
