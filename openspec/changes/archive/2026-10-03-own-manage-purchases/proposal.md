## Why

"Manage purchases" in Settings opens RevenueCat's hosted Customer Center (`HostedCustomerCenter` →
`purchases-kmp-ui`). It is the one screen in Harf we do not draw. Three problems follow from that:

- **It does not match the app.** The Customer Center follows the device's appearance, so on a dark
  device it renders on a black ground with its own buttons while every Harf surface is newsprint
  light. The dashboard only exposes an accent colour and the promotional-offer views, so the ground
  cannot be forced. `dark-editions` will give Harf its own dark editions, which makes the mismatch
  worse, not better: our dark is a tuned paper, not the system's black.
- **It is a platform bug surface.** On iOS the composable wraps a UIKit view controller: without an
  explicit size it measured to zero and painted an empty screen, which cost a day to find (fixed in
  `2c63ca3` with `Modifier.fillMaxSize()`). The Compose-over-UIKit seam stays for as long as we host
  someone else's screen.
- **It is the last user of the dependency.** The hosted paywall it came with is already gone —
  `HostedPaywall` has no call site since the custom `PaywallScreen` was adopted. Drawing this screen
  ourselves lets `purchases-kmp-ui` leave the build entirely.

What the hosted screen actually does for Harf is small: list what was bought, restore purchases, and
point at support. Subscription management, cancellation surveys and promotional offers do not apply —
Harf sells non-consumables only, and those options were turned off in the dashboard.

## What Changes

- **A Harf-drawn "Manage purchases" screen.** Same route as today (Settings → Manage purchases),
  drawn with the app's own kit and palette: what the player owns (Founder, each owned theme), a
  restore action, and a support link. Reads `EntitlementRepository` and `PurchaseController`, the same
  sources the paywall already uses, so ownership can never disagree between the two screens.
- **Refunds keep a path.** The hosted screen offered Apple's native refund request; the replacement
  links to Apple's "Report a Problem" on iOS and Google Play's order history on Android, which is
  where a refund is actually granted.
- **`purchases-kmp-ui` is removed**, together with `HostedPaywall`, `HostedCustomerCenter` and the
  `HostedBillingUi` expect/actual. `hostedBillingUiSupported` is what the paywall and settings really
  ask — "does this platform have a store" — so it is renamed accordingly and moved next to
  `PurchaseController`, keeping desktop and web on their honest no-store copy.

Non-goals: subscription management (Harf sells none); promotional offers and cancellation surveys;
changing what is sold or how entitlements are granted.

## Capabilities

### Modified Capabilities
- `paywall`: add a purchase-management surface the app draws itself — owned purchases, restore, and a
  platform-appropriate refund route — replacing the hosted Customer Center, and require it to follow
  the active Harf edition rather than the system appearance.

## Impact

- `sharedUI/.../feature/purchases/ManagePurchasesScreen.kt` (new) + its ViewModel or state holder.
- `sharedUI/.../navigation/AppNavHost.kt`: the `CustomerCenter` route renders the new screen.
- `sharedUI/.../billing/HostedBillingUi.kt` and `HostedBillingUi.mobile.kt` / `.jvmMain` / `.jsMain` /
  `.wasmJsMain`: deleted; the store-support flag moves to `PurchaseController`.
- `sharedUI/build.gradle.kts` + `gradle/libs.versions.toml`: drop `purchases-kmp-ui`.
- `sharedUI/.../feature/settings/SettingsScreen.kt`, `feature/paywall/PaywallScreen.kt`: use the
  renamed flag.
- Screenshot goldens gain the new screen (record from CI, per the project's golden rules).
- `docs/store/apple-review-reply.md` mentions "Manage purchases" — the wording stays true, the screen
  behind it changes.

Depends on nothing; `dark-editions` will theme this screen along with the rest once it lands.
