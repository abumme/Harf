## ADDED Requirements

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
