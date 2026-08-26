# entitlements Specification

## Purpose
Entitlements are the single source of truth for what a user has unlocked, read from RevenueCat and cached for offline display — the client never grants entitlements itself.

## Requirements

### Requirement: Entitlements sourced from RevenueCat
Active entitlements (lifetime, owned themes) SHALL be derived from RevenueCat customer info; the client SHALL NOT self-grant any entitlement.

#### Scenario: Entitlement reflects a purchase
- **WHEN** a purchase or restore completes successfully
- **THEN** the corresponding entitlement becomes active from RevenueCat customer info

#### Scenario: No local granting
- **WHEN** no purchase has been made
- **THEN** no entitlement is active regardless of local app state

### Requirement: Observable, offline-cached entitlement state
The app SHALL expose entitlement state as observable and SHALL cache the last known state so unlocked content displays correctly while offline.

#### Scenario: Offline shows last known entitlements
- **WHEN** the app is offline after a prior successful sync
- **THEN** previously unlocked content remains available based on the cached entitlement state

#### Scenario: UI updates on entitlement change
- **WHEN** entitlement state changes during a session
- **THEN** dependent UI (paywall, gated features) updates without a restart
