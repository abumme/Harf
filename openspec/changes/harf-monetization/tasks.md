## 1. SDK & abstraction

- [ ] 1.1 Add `purchases-kmp` to the catalog and link it into android/ios only; verify all four targets compile.
- [ ] 1.2 Define `PurchaseController` + `EntitlementRepository` interfaces and models (Offering, Product, PurchaseResult, Entitlements) in `billing/`; verify compile.
- [ ] 1.3 Implement `expect/actual` init: real RevenueCat on android/ios, no-op (Unavailable) on desktop/web; verify desktop run and each target build do not crash.
- [ ] 1.4 Provide RevenueCat API keys via `buildConfig` and register billing in Koin; verify a test resolves the controller/repository.

## 2. Entitlements

- [ ] 2.1 Implement `EntitlementRepository` exposing `StateFlow<Entitlements>` from customer info, cached via `AppSettings`; verify tests: entitlement reflects a (faked) purchase, none active by default, offline shows cached.
- [ ] 2.2 Ensure no code path grants entitlements locally; verify a test asserts entitlements only change from controller/customer-info input.

## 3. Paywall

- [ ] 3.1 Implement paywall MVI (loading/available/owned/unavailable states) rendering offerings with localized prices + purchase/restore; verify preview per state.
- [ ] 3.2 Wire purchase + restore through `PurchaseController` and refresh entitlements on success; verify a faked purchase moves an item to owned.
- [ ] 3.3 Add entry points from settings and result; verify the daily game is reachable without ever passing through the paywall.

## 4. Gating

- [ ] 4.1 Implement an `Entitlements`-based gate for lifetime-only extras (archive, hard mode, Founder badge); verify locked-without / unlocked-with tests, and that the daily round has no gate, no ads, and no timers.
- [ ] 4.2 Gate theme application: owned themes applicable, unowned previewable-not-applicable; verify tests for both and that selecting an unowned theme offers purchase.

## 5. Integration

- [ ] 5.1 Sandbox purchase + restore of `lifetime` and a `theme_*` on Android (and iOS via TestFlight when available); verify entitlement activates, extra/theme unlocks, and restore re-activates on a fresh install. Record store-config prerequisites for `harf-play-release`.
