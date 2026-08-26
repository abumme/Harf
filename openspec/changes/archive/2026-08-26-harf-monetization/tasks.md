## 1. SDK & abstraction

- [x] 1.1 Add `purchases-kmp` to the catalog and link it into android/ios only; verify all four targets compile. — `purchases-kmp-core 1.8.5+13.38.1` in a `mobileMain` intermediate source set (android+ios). Verified: desktop, js, wasmJs compile and android APK builds (androidMain sees the real SDK). iOS uses the same `mobileMain` code; not compiled here (no Xcode).
- [x] 1.2 Define `PurchaseController` + `EntitlementRepository` interfaces and models (Offering, Product, PurchaseResult, Entitlements) in `billing/`; verify compile. — `StoreItem`/`Offerings`/`Entitlements`/`PurchaseOutcome`/`OfferingsResult` + interface; compiles all buildable targets.
- [x] 1.3 Implement `expect/actual` init: real RevenueCat on android/ios, no-op (Unavailable) on desktop/web; verify each target build does not crash. — Bound via the existing `platformModule` (per-target actual) rather than a new expect/actual: android/ios bind `RevenueCatPurchaseController`, desktop/js/wasm bind `NoOpPurchaseController`. A blank key also yields Unavailable on mobile (no crash).
- [x] 1.4 Provide RevenueCat API keys via `buildConfig` and register billing in Koin; verify a test resolves the controller/repository. — `REVENUECAT_ANDROID_KEY`/`REVENUECAT_IOS_KEY` buildConfig fields (blank default, real keys store-side in harf-play-release); `EntitlementRepository` + `PurchaseController` in DI; `EntitlementTest`/`PaywallViewModelTest` construct/resolve them.

## 2. Entitlements

- [x] 2.1 Implement `EntitlementRepository` exposing `StateFlow<Entitlements>` from customer info, cached via `AppSettings`; verify tests: entitlement reflects a (faked) purchase, none active by default, offline shows cached. — `EntitlementTest`: `none_active_by_default`, `entitlement_reflects_a_purchase`, `offline_shows_cached`.
- [x] 2.2 Ensure no code path grants entitlements locally; verify a test asserts entitlements only change from controller/customer-info input. — `apply()` is private and only called from `refresh`/`applyFromController`, both guarded by `controller.isAvailable`; `no_local_granting_when_controller_unavailable` asserts refresh grants nothing when unavailable.

## 3. Paywall

- [x] 3.1 Implement paywall MVI (loading/available/owned/unavailable states) rendering offerings with localized prices + purchase/restore; verify preview per state. — `PaywallViewModel` (`PaywallPhase` Loading/Ready/Unavailable, per-item owned) + `PaywallScreen`; states covered by `PaywallViewModelTest` (Ready, Unavailable, owned-after-purchase). Roborazzi previews deferred (screen composes off-main in the harness, same limit as nav).
- [x] 3.2 Wire purchase + restore through `PurchaseController` and refresh entitlements on success; verify a faked purchase moves an item to owned. — `purchase_moves_item_to_owned`.
- [x] 3.3 Add entry points from settings and result; verify the daily game is reachable without ever passing through the paywall. — `Paywall` route + entries from `SettingsScreen` ("Support Harf") and the result view; `Home → Game` never routes through Paywall (start destination is Home, game launches directly).

## 4. Gating

- [x] 4.1 Implement an `Entitlements`-based gate for lifetime-only extras (archive, hard mode, Founder badge); verify locked-without / unlocked-with tests, and that the daily round has no gate, no ads, and no timers. — `EntitlementGate.lifetimeExtrasUnlocked` + a Founder badge in Settings; `lifetime_extras_gate` test. Per decision, archive/hard-mode are gate-ready hooks (the gate exists; those full features are separate future changes). The game feature calls no gate — the free round is ungated by construction; no ad/timer code exists.
- [x] 4.2 Gate theme application: owned themes applicable, unowned previewable-not-applicable; verify tests for both and that selecting an unowned theme offers purchase. — `EntitlementGate.canApplyTheme` (free palette + owned only when purchases are available; never locked where IAP is absent); Home cycles only applicable palettes; paywall lists themes for purchase; `gate_free_and_owned_only_when_purchases_available` test.

## 5. Integration

- [x] 5.1 DEFERRED → `harf-play-release` task 4.1 (store config is its prerequisite; sandbox verification happens there). Original: Sandbox purchase + restore of `lifetime` and a `theme_*` on Android (and iOS via TestFlight when available); verify entitlement activates, extra/theme unlocks, and restore re-activates on a fresh install. Record store-config prerequisites for `harf-play-release`. — BLOCKED here: needs real RevenueCat public keys + Play/App Store product ids + a store test account, none of which exist in this environment. Store-config prerequisites recorded for harf-play-release: create products `lifetime` (non-consumable) and `theme_*` (non-consumable), a RevenueCat offering exposing them, entitlements `lifetime` and `theme_*`, regional price tiers (lifetime ~$1.49-eq, themes ~$0.99-eq), and paste the public SDK keys into the buildConfig fields.
