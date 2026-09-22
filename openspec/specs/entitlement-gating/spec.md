# entitlement-gating Specification

## Purpose
Entitlement-gating decides what unlocked content is available — the extras bundled into the lifetime unlock and the application of purchased cosmetic themes — while guaranteeing the daily game stays free.

## Requirements

### Requirement: Free daily game is never gated
Gating SHALL never restrict the daily round, its board, keyboard, or result/share; only extras are gated.

#### Scenario: Core stays free
- **WHEN** the user has no entitlements
- **THEN** the daily game and its share result are fully available

### Requirement: Lifetime-only extras gated by entitlement
The extras bundled with the lifetime unlock — full puzzle archive, hard mode, and the Founder badge — SHALL be available only when the lifetime entitlement is active. Cosmetic themes are NOT part of the lifetime bundle (they are separate products).

#### Scenario: Extra locked without lifetime
- **WHEN** the lifetime entitlement is not active and the user opens a lifetime-only extra
- **THEN** access is gated and the user is offered the paywall

#### Scenario: Extra unlocked with lifetime
- **WHEN** the lifetime entitlement is active
- **THEN** the bundled extras are available

### Requirement: Purchased themes gated on application
A cosmetic theme SHALL be selectable/applicable to the board only when its entitlement is owned; unowned themes may be previewed but not applied.

#### Scenario: Owned theme applies
- **WHEN** the user owns a theme and selects it
- **THEN** the board renders in that theme

#### Scenario: Unowned theme cannot be applied
- **WHEN** the user selects an unowned theme
- **THEN** it is not applied and the user is offered its purchase

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
