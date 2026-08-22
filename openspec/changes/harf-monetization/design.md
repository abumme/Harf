## Context

See proposal.md — Why. Builds on `harf-foundation` (DI, settings, MVI). RevenueCat is the mandated monetization layer. Android + iOS are the real IAP targets; desktop/web must still compile and run. Config values (API keys) via `buildConfig` (in catalog).

## Goals / Non-Goals

**Goals:**
- Two offline-launchable streams live: lifetime unlock + cosmetic themes.
- RevenueCat as the entitlement source of truth; no client-side granting.
- No hard paywall; thin iosMain.

**Non-Goals:**
- Streak gifting / consumables / webhooks (needs backend — post-launch proposal).
- The theme token system (owned by foundation/cell-styles); this change only gates and applies purchased themes.
- Gems, season pass.

## Decisions

**SDK: `purchases-kmp` behind a `PurchaseController` + `EntitlementRepository`.**
Common interfaces in commonMain; `expect/actual` init links the real RevenueCat SDK only into android/ios, and a no-op actual into desktop/web (returns Unavailable). All feature code depends on the interfaces, never the SDK directly — keeps desktop/web compiling and iosMain thin. Alternative: platform billing libs directly — rejected (RevenueCat is required and unifies both stores).

**Entitlements from customer info, cached in settings.**
`EntitlementRepository` exposes `StateFlow<Entitlements>` derived from RevenueCat customer-info updates; last known value cached via `AppSettings` for offline display. The client never writes an entitlement as "granted" — it only mirrors RevenueCat. Alternative: local flags — rejected (insecure, and violates "client never self-grants").

**Products: non-consumables `lifetime`, `theme_*`, from offerings.**
Paywall renders from RevenueCat offerings (localized prices). Product ids created store-side (tracked in `harf-play-release`). No consumables in this change.

**Gating: a single `Entitlements` check at feature edges.**
Lifetime-only extras (archive, hard mode, Founder badge) and theme application check `entitlements` before enabling. The daily round path has no gate at all — enforced by keeping gate calls out of the game feature entirely. Theme selection: owned themes applicable; unowned previewable, not applicable. Cosmetic themes are separate products, not part of the lifetime bundle.

**Non-aggressive monetization posture.**
No ads, no timers/energy/lives, no forced or interstitial paywalls — the paywall is user-opened only. Central-Asia affordability is handled by **regional price tiers** (lifetime ~$1.49-equivalent, themes ~$0.99-equivalent; the $4.99 is only the default/US tier), configured store-side in `harf-play-release`. Perks are convenience/cosmetic and never pay-to-win, so low local pricing — not perk-cutting — is the affordability lever. "Founder" framing reads as supporting the developer.

**Paywall: MVI screen, reachable, never blocking.**
Opened from settings/result only. States: loading, available (list + prices + purchase/restore), owned per-item, unavailable/retry. Purchase/restore call `PurchaseController`; success refreshes entitlements.

## Risks / Trade-offs

- **Sandbox/store misconfig causes IAP to silently fail in review.** → Test in sandbox on both stores; ensure product ids + Paid Apps agreement ready (tracked in `harf-play-release`). Paywall shows explicit unavailable state to avoid a broken look.
- **Offline entitlement caching could momentarily over/under-report after a refund.** → RevenueCat customer-info refresh on foreground reconciles; cache is display-only, gating re-checks live state when reachable.
- **`purchases-kmp` API surface differs slightly per platform.** → Isolated behind the controller; only the actual init differs.

## Open Questions

- None blocking. Lifetime bundle = archive + hard mode + Founder badge; themes are separate products; pricing is regional (decided).
