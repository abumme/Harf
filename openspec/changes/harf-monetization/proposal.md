## Why

Harf's business model is free-forever core with light, honest monetization — and RevenueCat integration is a Shipaton requirement and the revenue backbone. The offline MVP needs the two no-backend revenue streams live at launch: a one-time Founder lifetime unlock and cosmetic board themes. There must be no hard paywall: the daily game stays fully free.

## What Changes

- Add the **RevenueCat SDK** (`purchases-kmp`) behind a small commonMain purchase abstraction, initialized per platform. Real implementations link only into Android and iOS; desktop/web get a no-op so those targets still compile.
- Add **entitlement state**: query and observe the user's active entitlements (lifetime, owned themes) via RevenueCat, cached for offline display; the client never self-grants.
- Add **products/offerings**: a non-consumable `lifetime` unlock and non-consumable `theme_*` cosmetic packs, presented from RevenueCat offerings.
- Add a **paywall screen** (not a wall): shows lifetime + themes, purchase and restore actions, reachable from settings/result — never blocking the daily game.
- Add **entitlement gating** for the extras the lifetime unlock includes — **full puzzle archive, hard mode, and a Founder badge** — and for applying purchased cosmetic themes to the board; the free daily round is never gated. (Cosmetic themes are sold separately, not bundled into lifetime.)
- Add **restore purchases** and correct display when entitlements are absent (offline or not purchased).
- **Non-aggressive posture (deliberate):** no ads, no timers/energy/lives, no forced or interstitial paywalls — the paywall is opened by the user, never pushed. Pricing uses **regional store tiers** so the lifetime unlock is a local "bus-fare" price (~$1.49-equivalent, e.g. ~15–18k UZS), not the $4.99 default; themes ~$0.99-equivalent. Central-Asia purchasing power is respected via price, not by cutting non-pay-to-win perks.

Non-goals: streak gifting ($0.49 consumable) and any server webhook entitlements — those need the backend and are a post-launch proposal; gems/season pass; the theme *token system* itself (that is `harf-foundation`/`harf-cell-styles`) — this change only gates/applies purchased themes and owns purchase logic.

## Capabilities

### New Capabilities
- `purchases`: initialize RevenueCat, fetch offerings, execute purchase and restore across platforms with safe no-op on desktop/web.
- `entitlements`: query/observe active entitlements as the source of truth for unlocked content, cached for offline, never client-granted.
- `paywall`: a non-blocking screen presenting lifetime + cosmetic themes with purchase/restore, with correct states for owned/not-owned/unavailable.
- `entitlement-gating`: gate lifetime-only extras and the application of purchased cosmetic themes, while keeping the daily game free.

### Modified Capabilities
<!-- none -->

## Impact

- `gradle/libs.versions.toml`: add `purchases-kmp` (RevenueCat).
- `sharedUI/.../billing/`: `PurchaseController`/`EntitlementRepository` (common interface) + `expect/actual` init; Koin bindings.
- `sharedUI/.../feature/paywall/`: paywall MVI screen; hooks from settings/result.
- Gating checks where lifetime extras and theme application occur (archive/hard-mode/theme apply).
- Config: RevenueCat API keys via `buildConfig` (already in catalog); product ids `lifetime`, `theme_*` created in both stores (store-side, tracked in `harf-play-release`).
- Depends on `harf-foundation`. Android/iOS real; desktop/web no-op.
