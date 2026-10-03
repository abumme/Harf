# Tasks

Context: this ships in the build that goes to App Store review, so the screen an Apple reviewer opens
is ours. The hosted Customer Center works today (`2c63ca3` gave it a size on iOS), which is the safety
net: if anything here is not ready in time, the route goes back to it and the change waits.

Because it lands before the submission, §3.2 is a gate, not a formality — the screen is on the path
the reviewer takes through Settings, and a device run has to pass before the archive.

## 1. The screen

- [x] 1.1 Add `ManagePurchasesScreen` + its state holder under `feature/purchases/`, reading owned entitlements from `EntitlementRepository` and calling `PurchaseController.restore()`; verify Founder and each owned theme are listed, and that an account owning nothing shows the empty state rather than a blank screen.
- [x] 1.2 Draw it with the shared button kit and `LocalHarfColors` so it follows the active edition; verify it renders on the light editions and, once `dark-editions` lands, on the dark ones — never on the system's ground.
- [x] 1.3 Restore reports its outcome in place (restored / nothing to restore / store unavailable), reusing the paywall's messages; verify each of the three outcomes from a fake `PurchaseController`.
- [x] 1.4 Add the refund route: Apple's Report a Problem on iOS, Play order history on Android, hidden where there is no store; verify the link opens on a device and that no dead entry is shown on desktop/web.

## 2. Swap it in

- [x] 2.1 Point the `CustomerCenter` route in `AppNavHost` at the new screen and drop `HostedCustomerCenter`; verify Settings → Manage purchases opens it on Android and iOS.
- [x] 2.2 Rename `hostedBillingUiSupported` to what its call sites actually ask (a store exists on this platform) and move it next to `PurchaseController`; verify the paywall and settings still show the no-store copy on desktop and web.
- [x] 2.3 Delete `HostedBillingUi.kt` and every actual, including the unused `HostedPaywall`, and remove `purchases-kmp-ui` from `sharedUI/build.gradle.kts` and the version catalog; verify JVM, Android, iOS and web still compile and the iOS framework no longer links RevenueCatUI (`nm -gU SharedUI.framework/SharedUI | grep CustomerCenter` is empty).

## 3. Verification

- [x] 3.1 Add a screenshot test for the screen (owned and empty states) and take the goldens from CI, per the project's golden rules; verify `verifyRoborazziJvm` is green on CI. — `ManagePurchasesScreenshotTest` (owned + empty), goldens in `0ac855f`; `verifyRoborazziJvm` green on CI run 37045571244 (`0788b2a`, main).
- [x] 3.2 Run the round trip on a device: buy on the paywall, open Manage purchases, restore, and confirm what is listed matches `EntitlementGate` — the two screens must never disagree.
