# Spec Delta

## MODIFIED Requirements

### Requirement: Entitlements sourced from RevenueCat
Active purchase entitlements (lifetime and owned themes) SHALL be derived from purchase-provider-verified customer state. Mobile clients SHALL read verified customer state from RevenueCat; authenticated desktop and web clients SHALL obtain account-scoped ownership from a backend that verifies the linked RevenueCat customer. A client SHALL NOT self-grant an entitlement or accept an arbitrary client-supplied purchase flag as proof.

#### Scenario: Entitlement reflects a purchase
- **WHEN** a purchase or restore completes successfully for a linked Harf account
- **THEN** the corresponding entitlement becomes active from verified purchase-provider state on that account's signed-in devices

#### Scenario: No local granting
- **WHEN** no purchase has been made
- **THEN** no entitlement is active regardless of local app state

#### Scenario: Desktop and web resolve account ownership
- **WHEN** a Founder owner signs into the same Harf account on desktop or web
- **THEN** the client receives the verified Founder entitlement without needing an in-app purchase on that platform

### Requirement: Observable, offline-cached entitlement state
The app SHALL expose entitlement state as observable and SHALL cache the last successfully verified state for the current account so unlocked content remains available during a temporary connectivity or purchase-provider failure. A confirmed ownership change SHALL update the cache, and cached state SHALL NOT be reused for a different account.

#### Scenario: Offline shows last known entitlements
- **WHEN** the app is offline after a prior successful verification for the current account
- **THEN** previously unlocked content remains available based on that account's cached entitlement state

#### Scenario: UI updates on entitlement change
- **WHEN** verified entitlement state changes during a session
- **THEN** dependent UI (paywall, gated features) updates without a restart

#### Scenario: Temporary refresh failure preserves access
- **WHEN** an entitlement refresh fails because of connectivity or provider error
- **THEN** the last verified ownership state remains in effect for the current account instead of being replaced with an empty state

#### Scenario: Account switch invalidates prior cache
- **WHEN** a device changes from account A to account B
- **THEN** account A's cached Founder entitlement is not applied to account B
