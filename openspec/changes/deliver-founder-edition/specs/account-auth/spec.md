# Spec Delta

## REMOVED Requirements

### Requirement: Platform-scoped provider availability
**Reason**: Apple sign-in is no longer restricted to iOS; a browser flow permits access on other supported platforms.
**Migration**: Replace platform restrictions with the cross-platform provider availability requirement below.

## ADDED Requirements

### Requirement: Cross-platform provider availability
The system SHALL offer Google and Apple sign-in on Android, iOS, JVM desktop, and web when the respective provider is configured. The client SHALL use a supported native or browser-based flow for each provider on its platform. An unavailable provider SHALL be reported clearly rather than silently failing, and the free daily game SHALL remain usable.

#### Scenario: Apple sign-in available on desktop
- **WHEN** a configured JVM desktop client offers account sign-in
- **THEN** the player can choose Apple and complete a browser-based sign-in to the same Harf account used on iOS

#### Scenario: Apple sign-in available on Android
- **WHEN** a configured Android client offers account sign-in
- **THEN** the player can choose Apple and complete sign-in to the same Harf account used on iOS

#### Scenario: Both providers available on web
- **WHEN** a configured web client offers account sign-in
- **THEN** the player can sign in with either Google or Apple and reach the corresponding existing Harf account

#### Scenario: Provider configuration is missing
- **WHEN** a provider has not been configured for a client deployment
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
