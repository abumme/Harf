# Spec Delta

## REMOVED Requirements

### Requirement: Platform-scoped provider availability
**Reason**: Apple sign-in is no longer restricted to iOS; a browser flow permits access on other supported platforms.
**Migration**: Replace platform restrictions with the cross-platform provider availability requirement below.

## ADDED Requirements

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
