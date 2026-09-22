# paywall Specification

## Purpose
The paywall presents Harf's optional purchases — a Founder lifetime unlock and cosmetic themes — without ever blocking the free daily game.

## Requirements

### Requirement: Non-blocking, non-aggressive paywall
The paywall SHALL be reachable from settings and the result screen and SHALL NEVER block access to the daily game; the core game is always playable for free. The app SHALL NOT show ads, timers/energy/lives, or forced/interstitial paywalls; the paywall is only shown when the user opens it.

#### Scenario: Daily game never gated by the paywall
- **WHEN** the user has made no purchase
- **THEN** the full daily round remains playable and the paywall is only shown when the user opens it

#### Scenario: No forced monetization surfaces
- **WHEN** the user plays normally without opening the paywall
- **THEN** no ad, timer/energy gate, or interstitial paywall is presented

### Requirement: Present lifetime and themes with prices
The paywall SHALL display the lifetime unlock and available cosmetic themes with their localized prices from offerings, plus purchase and restore actions.

#### Scenario: Products and prices shown
- **WHEN** the paywall opens with offerings available
- **THEN** it lists lifetime and themes with localized prices and offers purchase and restore

### Requirement: Reflect ownership and availability states
The paywall SHALL show owned products as owned (not purchasable again) and SHALL show a graceful state when offerings are unavailable.

#### Scenario: Owned product shown as owned
- **WHEN** the user already owns the lifetime unlock or a theme
- **THEN** that item is shown as owned and cannot be repurchased

#### Scenario: Unavailable offerings state
- **WHEN** offerings cannot be loaded
- **THEN** the paywall shows an unavailable/retry state instead of an empty or broken screen

### Requirement: Founder offer describes the delivered bundle
The Founder offer shown before purchase SHALL accurately state that it is a one-time lifetime unlock for the full published puzzle archive, hard mode, and the Founder badge, with cosmetic themes sold separately. User-visible offer text SHALL be localized for the supported app languages and consistent across the custom in-app paywall, legal offer, and store listing surfaces.

#### Scenario: Mobile buyer sees complete terms
- **WHEN** a mobile player views the Founder offer before purchase
- **THEN** the offer states the three delivered extras, lifetime nature, and locally formatted store price

#### Scenario: Ownership is already active
- **WHEN** the current account already owns Founder
- **THEN** the product is shown as owned rather than as another purchase opportunity

### Requirement: Platforms without in-app purchases explain access
On desktop and web, the Founder entry point SHALL offer account sign-in to an existing owner and SHALL clearly state where a new purchase can be made without presenting an unusable purchase action. The free daily game SHALL remain available.

#### Scenario: Existing owner on desktop or web
- **WHEN** an unauthenticated desktop or web player opens the Founder entry point
- **THEN** the app offers sign-in to restore access through the same Harf account

#### Scenario: New buyer on desktop or web
- **WHEN** a desktop or web player without Founder views the offer
- **THEN** the app describes the supported mobile purchase path and does not present a nonfunctional local purchase button
