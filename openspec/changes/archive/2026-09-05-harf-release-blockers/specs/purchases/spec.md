## ADDED Requirements

### Requirement: Release builds reject non-production purchase keys
A release build SHALL NOT ship with a Test Store or placeholder RevenueCat key; the build SHALL fail when such a key is configured for a release, so a misconfigured key cannot reach production.

#### Scenario: Test Store key in a release build fails the build
- **WHEN** a release build is configured with a Test Store or otherwise non-production RevenueCat SDK key
- **THEN** the build SHALL fail with a clear error rather than producing a shippable artifact

#### Scenario: An intentionally blank key produces an unavailable-purchases build
- **WHEN** a release build is configured with a blank purchase key
- **THEN** the build SHALL succeed and purchases SHALL report Unavailable at runtime (no crash)

### Requirement: Purchase initialization never crashes the app
Initializing the purchase system SHALL degrade to an unavailable state on any configuration or SDK failure rather than throwing and crashing the app.

#### Scenario: Misconfigured key degrades to Unavailable instead of crashing
- **WHEN** the purchase SDK cannot be configured (invalid, blank, or rejected key, or a configuration exception)
- **THEN** the app SHALL continue to run with purchases reported as Unavailable and SHALL NOT crash on startup or when opening the paywall

#### Scenario: The free daily round is unaffected by purchase failure
- **WHEN** purchases are Unavailable for any reason
- **THEN** the player SHALL still be able to play the free daily round normally
