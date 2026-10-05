## Purpose

Publicly hosted HTML pages that render the Harf Privacy Policy and Public Offer in every listing locale, reachable at the canonical URLs the app's Settings screen and the Google Play listing link to. The pages are served by the `lazydevs.uz` landing site (Angular SSG), rendering text that mirrors the source markdown in this repo's `docs/legal/`.

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

Each legal page SHALL present its content in all locales the app ships (uz, ru, en, kk, tr) from a single URL, offering a language toggle for the locales the document provides, with a sensible default.

#### Scenario: Default locale on a direct visit

- **WHEN** a visitor opens a legal page directly (a pasted URL, the in-app link, or the Play listing — not by navigating within the site)
- **THEN** the page shows the content in the visitor's browser language (`navigator.language`, first two letters) when it is one of uz/ru/en/kk/tr, otherwise in the fallback locale (ru)
- **AND** this default is applied on the client after hydration, so the prerendered/SSR markup (rendered in ru) stays stable and hydration does not mismatch

#### Scenario: Language chosen on the site is kept

- **WHEN** a visitor reaches a legal page by navigating within the `lazydevs.uz` site
- **THEN** the page opens in the language the visitor is already reading the site in, mapped to a language the document provides (an explicit on-site language choice takes priority over the browser default)

#### Scenario: Switching language

- **WHEN** the visitor selects a different available language via the on-page control
- **THEN** the full legal text updates to that language without navigating to a different URL

#### Scenario: Only available languages are offered

- **WHEN** a document exists in fewer than all five locales
- **THEN** the page offers a language button only for each locale that document actually provides

### Requirement: No unresolved placeholders in published pages

A published legal page SHALL NOT contain any template placeholder or editorial fill-in marker; all legally required identifiers MUST be present.

#### Scenario: Placeholders are filled

- **WHEN** any published legal page or its source markdown is inspected
- **THEN** it contains no `{{...}}` tokens and no "FILL IN BEFORE PUBLISHING" block, and the operator/seller name, contact email, canonical URLs, and per-document revision date are concrete values

#### Scenario: Source and page stay in sync

- **WHEN** the source markdown in `docs/legal/` is compared with the rendered page for the same document and locale
- **THEN** the legal text and the filled-in identifiers match (the current revisions are Privacy Policy `2026-09-17` and Public Offer `2026-09-14`)

### Requirement: Consistency with the app's data practices

The Privacy Policy content SHALL accurately reflect what the app actually collects, so it stays consistent with the Play Data Safety form.

#### Scenario: Policy matches actual collection

- **WHEN** the Privacy Policy is reviewed against the app's data behavior (anonymous account UUID; optional Google/Apple sign-in storing the provider+subject id and the display name confirmed at sign-in, but not email or profile photo; game stats sync; optional word suggestions; RevenueCat entitlement status; local settings; authorized-administrator access for support/moderation with a logged action trail; aggregated, non-identifying analytics; no location/contacts/mic/camera/ads)
- **THEN** the policy describes exactly those categories and no others
