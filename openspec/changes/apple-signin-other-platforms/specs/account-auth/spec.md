# Spec Delta

## MODIFIED Requirements

### Requirement: Cross-platform provider availability
The system SHALL offer Google sign-in on Android, iOS, JVM desktop, and web, and Apple sign-in on Android, iOS, JVM desktop, and web, when the respective provider is configured. Apple sign-in SHALL use a native flow on iOS and a supported browser-based flow with server-validated callbacks on Android, JVM desktop, and web. The client SHALL use a supported native or browser-based flow for each available provider on its platform. An unavailable provider SHALL be reported clearly rather than silently failing, and the free daily game SHALL remain usable.

#### Scenario: Google sign-in available on every platform
- **WHEN** a configured Android, iOS, JVM desktop, or web client offers account sign-in
- **THEN** the player can choose Google and reach the corresponding existing Harf account

#### Scenario: Apple sign-in available on every platform
- **WHEN** a configured Android, iOS, JVM desktop, or web client offers account sign-in
- **THEN** the player can choose Apple and reach the same Harf account as native iOS Apple sign-in

#### Scenario: Apple callback is cancelled or invalid
- **WHEN** an Apple browser sign-in returns a cancelled response or invalid audience, state, or nonce data
- **THEN** the client leaves the current session unchanged and grants no account access

#### Scenario: Provider configuration is missing
- **WHEN** a provider has not been configured for a client deployment
- **THEN** the client explains that sign-in method is unavailable and still allows free daily play
