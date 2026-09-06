## Purpose

Publicly hosted HTML pages that render the Harf Privacy Policy and Public Offer in every listing locale, reachable at the canonical URLs the app's Settings screen and the Google Play listing link to.

## ADDED Requirements

### Requirement: Canonical legal URLs resolve

The Privacy Policy and Public Offer SHALL each be reachable at a stable public URL that matches the constants the app opens (`LegalLinks.PRIVACY`, `LegalLinks.OFFER` in `SettingsScreen.kt`) and the URL declared on the Play listing.

#### Scenario: Privacy URL is reachable

- **WHEN** an unauthenticated visitor requests the canonical Privacy Policy URL over HTTPS
- **THEN** the server returns HTTP 200 with an HTML document rendering the Privacy Policy (no 404, no auth wall)

#### Scenario: Offer URL is reachable

- **WHEN** an unauthenticated visitor requests the canonical Public Offer URL over HTTPS
- **THEN** the server returns HTTP 200 with an HTML document rendering the Public Offer

#### Scenario: In-app links open the live pages

- **WHEN** a user taps "Privacy Policy" or "Terms / Offer" in the app Settings screen
- **THEN** the device browser opens the matching canonical URL and shows the corresponding legal document

### Requirement: Every listing locale is served

Each legal page SHALL present its content in all locales the app ships (uz, ru, en) from a single URL, with a user-selectable language and a sensible default.

#### Scenario: Default locale on first load

- **WHEN** a visitor opens a legal page without a stored language preference
- **THEN** the page shows the content in the visitor's browser language when it is one of uz/ru/en, otherwise in the fallback locale (ru)

#### Scenario: Switching language

- **WHEN** the visitor selects a different available language via the on-page control
- **THEN** the full legal text updates to that language without navigating to a different URL

### Requirement: No unresolved placeholders in published pages

A published legal page SHALL NOT contain any template placeholder or editorial fill-in marker; all legally required identifiers MUST be present.

#### Scenario: Placeholders are filled

- **WHEN** any published legal page or its source markdown is inspected
- **THEN** it contains no `{{...}}` tokens and no "FILL IN BEFORE PUBLISHING" block, and the operator/seller name, contact email, canonical URLs, and revision date are concrete values

#### Scenario: Source and page stay in sync

- **WHEN** the source markdown in `docs/legal/` is compared with the rendered HTML page for the same document and locale
- **THEN** the legal text and the filled-in identifiers match

### Requirement: Consistency with the app's data practices

The Privacy Policy content SHALL accurately reflect what the app actually collects, so it stays consistent with the Play Data Safety form.

#### Scenario: Policy matches actual collection

- **WHEN** the Privacy Policy is reviewed against the app's data behavior (anonymous account UUID, optional Google/Apple sign-in storing only provider+subject, game stats sync, RevenueCat entitlement status, local settings; no location/contacts/mic/camera/ads)
- **THEN** the policy describes exactly those categories and no others
