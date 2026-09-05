# purchases Specification

## Purpose
Purchases wraps RevenueCat so the app can list offerings and buy or restore products uniformly across platforms, with a safe no-op where in-app purchases don't apply.

## Requirements

### Requirement: Cross-platform purchase abstraction
The app SHALL expose a common purchase interface (initialize, fetch offerings, purchase a product, restore) whose real implementation runs on Android and iOS via RevenueCat, and which is a safe no-op on desktop and web so those targets still build and run.

#### Scenario: Mobile executes a real purchase flow
- **WHEN** a purchase is initiated on Android or iOS for an available product
- **THEN** the RevenueCat purchase flow runs and its result (success/cancel/error) is returned

#### Scenario: Desktop/web no-op does not crash
- **WHEN** the app runs on desktop or web
- **THEN** purchase calls return an unavailable/no-op result and the app does not crash

### Requirement: Offerings from RevenueCat
The app SHALL present products from RevenueCat offerings (lifetime unlock and cosmetic themes) rather than hard-coded product data.

#### Scenario: Offerings drive the paywall
- **WHEN** offerings are fetched successfully
- **THEN** the available products (lifetime, themes) and their localized prices come from RevenueCat

#### Scenario: Offering fetch failure is handled
- **WHEN** offerings cannot be fetched (e.g. offline)
- **THEN** the app shows a graceful unavailable state rather than failing

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
