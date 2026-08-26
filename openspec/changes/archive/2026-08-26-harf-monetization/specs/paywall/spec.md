## Purpose

The paywall presents Harf's optional purchases — a Founder lifetime unlock and cosmetic themes — without ever blocking the free daily game.

## ADDED Requirements

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
