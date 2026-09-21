# Spec Delta

## ADDED Requirements

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
