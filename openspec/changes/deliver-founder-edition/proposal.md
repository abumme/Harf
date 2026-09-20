# Proposal

## Why

Harf already describes Founder as a lifetime bundle of the full puzzle archive, hard mode, and a badge, but the app currently delivers only the badge. The first Founder sale should fulfill that promise and let a purchaser use the bundle on every supported platform through the same Harf account.

## What Changes

- Deliver a Founder-only archive of every published puzzle from the first public day of each language through yesterday, with replayable rounds and a separate, account-synced archive history. Archive play never changes the official daily result, statistics, streaks, or platform achievements.
- Deliver a Founder-only hard mode for daily and archived rounds. It uses the same answer and six attempts, but each accepted guess must respect all earlier green and yellow clues, including positions and repeated grapheme counts. A rule violation explains the problem and does not consume an attempt. The mode is chosen before a round starts and persists with that round.
- Make the existing Founder badge visible whenever the owning entitlement is known, even if the store is temporarily unavailable. Keep the daily game free and cosmetic themes separate.
- Make mobile Founder purchases and restores resolve to the player's Harf account, and make verified ownership available after sign-in on Android, iOS, desktop, and web. Offer Google and Apple sign-in across platforms using supported native or browser flows; email registration is outside this change because both providers support browser-based access.
- Align the mobile hosted paywall, in-app strings, legal offer, and store descriptions with the shipped bundle; provide a clear account sign-in path on platforms without in-app purchasing. Verify the lifetime product is non-consumable and restorable.
- Keep the proposed Saturday bonus mode outside this change.

## Capabilities

### New Capabilities

- `puzzle-archive`: Browse and replay published past puzzles with separately persisted and synchronized archive results.
- `hard-mode`: Enforce accumulated clue constraints at grapheme level without changing the daily answer or attempt limit.

### Modified Capabilities

- `account-auth`: Support the same Google or Apple linked account across all supported clients and handle account transitions safely.
- `entitlements`: Resolve Founder ownership from purchase-provider-verified account state on every platform, with account-scoped offline caching and error handling.
- `entitlement-gating`: Apply the lifetime gate to archive and hard mode and show the owned badge independently of store availability.
- `purchases`: Bind mobile purchases/restores to the account identity and require a restorable non-consumable Founder product.
- `paywall`: Describe the delivered Founder bundle and explain the sign-in/purchase path on clients without a store.
- `result-share`: Identify archive and hard-mode results accurately in shared output.

## Impact

- Shared UI game, navigation, daily-puzzle selection, round persistence, results, strings, settings, and purchase state in `sharedUI`; shared API models in `sharedData`.
- Backend account-scoped archive-history and entitlement endpoints, purchase-provider verification, and provider-auth callback support; database migration and access-control checks.
- Android/iOS RevenueCat configuration and purchase identity, Google/Apple provider configuration for mobile, desktop, and web, and store-side non-consumable product setup.
- RevenueCat hosted paywall, localized legal/store text, and cross-platform purchase/restore testing. Existing release and dependency-upgrade changes remain separate.
