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

### Requirement: Cross-platform provider availability
The system SHALL offer Google sign-in on Android, iOS, JVM desktop, and web, and Apple sign-in natively on iOS, when the respective provider is configured. Apple sign-in on Android, JVM desktop, and web is a later phase and SHALL be treated as an unconfigured provider until it ships. The client SHALL use a supported native or browser-based flow for each available provider on its platform. An unavailable provider SHALL be reported clearly rather than silently failing, and the free daily game SHALL remain usable.

#### Scenario: Google sign-in available on every platform
- **WHEN** a configured Android, iOS, JVM desktop, or web client offers account sign-in
- **THEN** the player can choose Google and reach the corresponding existing Harf account

#### Scenario: Apple sign-in available on iOS
- **WHEN** a configured iOS client offers account sign-in
- **THEN** the player can choose Apple and complete native sign-in to the same Harf account

#### Scenario: Provider configuration is missing
- **WHEN** a provider has not been configured for a client deployment, including Apple on Android, desktop, or web in this release
- **THEN** the client explains that sign-in method is unavailable and still allows free daily play

### Requirement: A player can link both providers to one Harf account
An authenticated player SHALL be able to attach a second verified Google or Apple identity to the current Harf account when that identity is not already owned by another account. A conflicting linked identity SHALL NOT silently merge or delete two established accounts.

#### Scenario: Add a second provider
- **WHEN** a player signed in with Apple links a Google identity that belongs to no other Harf account
- **THEN** both identities authenticate the same account and its Founder access

#### Scenario: Conflicting established accounts
- **WHEN** a linked account tries to attach an identity already owned by a different linked account
- **THEN** the system rejects the link without moving purchases, archive history, or official statistics between accounts

### Requirement: Account transitions isolate purchased access
After sign-out, account switch, or confirmed deletion, the client SHALL stop presenting the previous account's Founder access and private archive history. A purchased product SHALL remain recoverable by signing back into its owning account or through the original mobile store's supported restore flow, subject to purchase-provider ownership.

#### Scenario: Sign out of a Founder account
- **WHEN** a Founder owner signs out on a shared device
- **THEN** the next account cannot see the former account's Founder extras or archive history

#### Scenario: Sign back in
- **WHEN** the owner signs back into the same Harf account
- **THEN** verified Founder access and synchronized archive history become available again
