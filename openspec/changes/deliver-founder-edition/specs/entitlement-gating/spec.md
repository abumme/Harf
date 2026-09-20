# Spec Delta

## ADDED Requirements

### Requirement: Founder badge reflects ownership independently of store availability
The Founder badge SHALL be visible to the player whenever the current account's Founder entitlement is active or validly cached, including when in-app purchases are temporarily unavailable.

#### Scenario: Store unavailable after verified ownership
- **WHEN** a Founder owner's store connection is unavailable but the current account retains its verified Founder entitlement
- **THEN** the Founder badge remains visible

#### Scenario: No entitlement
- **WHEN** the current account has no verified Founder entitlement
- **THEN** the Founder badge is not shown as owned

### Requirement: Lifetime extras have visible locked entry points
The archive and hard-mode selection SHALL be discoverable without a purchase, but starting either extra SHALL require Founder ownership. A locked entry point SHALL explain the bundle and provide an optional route to its paywall without interrupting free daily play.

#### Scenario: Non-owner opens archive
- **WHEN** a non-owner selects the archive entry point
- **THEN** the app explains that archive play requires Founder and offers the paywall while leaving the daily game available

#### Scenario: Non-owner selects hard mode
- **WHEN** a non-owner selects hard mode before a round
- **THEN** hard mode remains locked and normal daily play remains available
