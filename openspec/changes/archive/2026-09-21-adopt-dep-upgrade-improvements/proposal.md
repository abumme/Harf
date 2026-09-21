## Why

A mass dependency bump just landed in `gradle/libs.versions.toml` (Kotlin 2.4.20, Ktor 3.6.0, Compose 1.12.0, AGP 9.4.1, purchases-kmp 3.9.0, nimbus-jose-jwt 10.10, HikariCP 7.1.0, and more). Several of these versions unlock APIs that let us delete per-platform boilerplate, close security gaps, and simplify build config — and one (purchases-kmp 3.x) has a **required** iOS setup change without which the iOS build breaks. This change captures the reviewed changelog findings as concrete adoption work so the codebase actually benefits from the bump instead of just tracking newer numbers.

Findings were gathered by scanning each dependency's GitHub releases / changelog for its exact version range. Tier 4 (speculative: OIDC auto-discovery, Swift export, Karma→Playwright, predictive-back) is explicitly out of scope.

## What Changes

**Mandatory (upgrade is incomplete without it):**
- **BREAKING (iOS build):** Remove `PurchasesHybridCommon` + `PurchasesHybridCommonUI` SPM packages from `iosApp/iosApp.xcodeproj` (Package Dependencies, Frameworks phase, `XCRemoteSwiftPackageReference`). purchases-kmp 3.0 sources the iOS native dep through Gradle (kn-core/kn-ui); the leftover SPM packages double-link / mismatch.
- Dashboard-only: confirm the lifetime product is **non-consumable** in the RevenueCat dashboard (Play Billing 8 no longer restores consumed one-time products).

**Core simplifications (Tier 1):**
- **Ktor common client engine.** Replace per-platform engine deps (OkHttp on android/jvm, Darwin on ios) with `io.ktor:ktor-client-engine-defaults` in `commonMain`. Ktor auto-selects an engine per target; removes the last per-platform client wiring. Keep an explicit engine on any target where the default is unsuitable (web coverage to be verified).
- **buildConfig expect/actual.** Use buildConfig 6.0 KMP support to split `REVENUECAT_ANDROID_KEY` + `REVENUECAT_IOS_KEY` into one `expect`-ed field actual-ized per source set, so neither store's key leaks into `commonMain`.
- **nimbus JWKS modernization.** Replace deprecated `RemoteJWKSet(url)` with `JWKSourceBuilder.create(url).build()` in the Apple/Google OAuth verifiers (adds caching + outage tolerance) and drop the now-redundant manual `exp` check.

**Considered improvements (Tier 2):**
- Ktor typesafe JWT auth DSL (`jwt<Principal> { }` + `authenticateWith` + typed `call.principal`) to remove untyped principal casts in auth routes.
- Kotlin/Native incremental compilation (`kotlin.incremental.native=true`) for faster iOS/Native debug builds.
- KSafe `KSafePlain` view for non-secret keys (palette / onboarded / entitlement mirror) to drop first-frame decrypt cost; one-time migrate from encrypted values.
- Rely on Compose 1.12 stable web Noto font fallback for Uzbek Cyrillic (verify bundled font coverage first).
- AGP 9.3 `optimization { }` DSL for the release block.
- pgjdbc `reWriteBatchedInserts=true` for the bundled word-pack seed inserts.

**Verification-only (Tier 3), no or trivial code:**
- pgjdbc security roll-up (CVE-2025-49146, SCRAM DoS/downgrade) — free on bump; optionally set `channelBinding=require`.
- Confirm SLF4J ≥ 2.0.18 for logback 1.6.x.
- Audit `androidApp/proguard-rules.pro` against AGP 9.2 stricter `-keepattributes` (RevenueCat + kotlinx.serialization are the blast radius).
- Re-record any Roborazzi golden containing a popup/menu inside a dialog (1.74 position fix).
- Verify activity 1.13 auto edge-to-edge does not fight the manual `ThemeChanged` composable.

## Capabilities

### New Capabilities
- _None._ This is a dependency-adoption, tooling, and internal-refactor change; it introduces no new user-facing capability.

### Modified Capabilities
- _None._ No spec-level behavior requirements change. The client HTTP engine, key injection, and JWKS retrieval are implementation details; the web font fallback and paywall locale items are correctness/polish, not new declared requirements. This change sets `skip_specs: true`.

## Impact

- **Build:** `gradle/libs.versions.toml` (already bumped), `sharedUI/build.gradle.kts` (Ktor engine deps, buildConfig block), `androidApp/build.gradle.kts` (optimization DSL), `gradle.properties` (incremental native flag).
- **iOS:** `iosApp/iosApp.xcodeproj` (SPM package removal — required).
- **Backend:** Apple/Google OAuth verifiers (nimbus JWKS), auth routes (typesafe JWT DSL), JDBC URL (`reWriteBatchedInserts`, `channelBinding`), `logback.xml` / SLF4J version check, `proguard-rules.pro` audit.
- **Client:** `di/Koin.kt` (HttpClient), `PlatformModule.*.kt` (RC key), `AppSettings` / KSafe wrappers (plain view), web font resources.
- **Tests/CI:** Roborazzi goldens (re-record if popup-in-dialog affected).
- **External (no code):** RevenueCat dashboard product configuration.
