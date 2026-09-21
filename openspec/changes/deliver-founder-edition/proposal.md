# Proposal

## Why

Harf already describes Founder as a lifetime bundle of the full puzzle archive, hard mode, and a badge, but the app currently delivers only the badge. The first Founder sale should fulfill that promise and let a purchaser use the bundle on every supported platform through the same Harf account.

## What Changes

- Deliver a Founder-only archive of every published puzzle from the first public day of each language through yesterday, with replayable rounds and a separate, account-synced archive history. Archive play never changes the official daily result, statistics, streaks, or platform achievements.
- Deliver a Founder-only hard mode for daily and archived rounds. It uses the same answer and six attempts, but each accepted guess must respect all earlier green and yellow clues, including positions and repeated grapheme counts. A rule violation explains the problem and does not consume an attempt. The mode is chosen before a round starts and persists with that round.
- Make the existing Founder badge visible whenever the owning entitlement is known, even if the store is temporarily unavailable. Keep the daily game free and cosmetic themes separate.
- Require sign-in before a mobile Founder purchase so every purchase and restore resolves to a verified Harf account with no anonymous purchase state, and make verified ownership available after sign-in on Android, iOS, desktop, and web. Offer Google sign-in on all four platforms and Apple sign-in natively on iOS for this release; Apple browser sign-in on Android, desktop, and web is deferred to a later phase pending its Services ID and store configuration. Email registration is outside this change because both providers support browser-based access.
- Align a custom in-app Founder paywall, in-app strings, legal offer, and store descriptions with the shipped bundle; provide a clear account sign-in path on platforms without in-app purchasing. Verify the lifetime product is non-consumable and restorable.
- Keep the proposed Saturday bonus mode outside this change.

## Capabilities

### New Capabilities

- `puzzle-archive`: Browse and replay published past puzzles with separately persisted and synchronized archive results.
- `hard-mode`: Enforce accumulated clue constraints at grapheme level without changing the daily answer or attempt limit.

### Modified Capabilities

- `account-auth`: Support the same Google or Apple linked account across supported clients (Google on all four platforms, Apple native on iOS this release) and handle account transitions safely.
- `entitlements`: Resolve Founder ownership from purchase-provider-verified account state on every platform, with account-scoped offline caching and error handling.
- `entitlement-gating`: Apply the lifetime gate to archive and hard mode and show the owned badge independently of store availability.
- `purchases`: Require sign-in before purchase, bind mobile purchases/restores to the account identity with no anonymous purchase state, and require a restorable non-consumable Founder product.
- `paywall`: Describe the delivered Founder bundle and explain the sign-in/purchase path on clients without a store.
- `result-share`: Identify archive and hard-mode results accurately in shared output.

## Impact

- Shared UI game, navigation, daily-puzzle selection, round persistence, results, strings, settings, and purchase state in `sharedUI`; shared API models in `sharedData`.
- Backend account-scoped archive-history and entitlement endpoints, purchase-provider verification, and provider-auth callback support; database migration and access-control checks.
- Android/iOS RevenueCat configuration and account-identified purchase identity, Google provider configuration on all four platforms plus Apple native on iOS, and store-side non-consumable product setup.
- Custom in-app Founder paywall, localized legal/store text, and cross-platform purchase/restore testing. Existing release and dependency-upgrade changes remain separate.
