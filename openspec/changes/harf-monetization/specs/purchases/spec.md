## Purpose

Purchases wraps RevenueCat so the app can list offerings and buy or restore products uniformly across platforms, with a safe no-op where in-app purchases don't apply.

## ADDED Requirements

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
