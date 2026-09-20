# Tasks

All file/line references are leads from the changelog scan — confirm each against the actual source before editing.

## 1. Mandatory (unblock iOS build)

- [x] 1.1 Removed `PurchasesHybridCommon` + `PurchasesHybridCommonUI` from `iosApp/iosApp.xcodeproj`: PBXBuildFile, Frameworks phase, packageProductDependencies, packageReferences, `XCRemoteSwiftPackageReference` + `XCSwiftPackageProductDependency` blocks; also cleared the stale `Package.resolved` pins. `plutil -lint` OK.
- [x] 1.2 `xcodebuild -scheme iosApp -sdk iphonesimulator` → **BUILD SUCCEEDED**. RevenueCat links via Gradle (SharedUI.framework), no double-link/version-mismatch. Only benign warning: bundled libicu built for iOS-sim 18.5 vs linked 16.2 min (unrelated).
- [ ] 1.3 Dashboard-only: confirm the lifetime product is configured **non-consumable** so `restore()` keeps working under Play Billing 8

## 2. Verification-only sweep (cheap, de-risk the bumped build)

- [x] 2.1 Confirm SLF4J on the backend classpath is ≥ 2.0.18 (logback 1.6.x requirement) — no direct SLF4J pin in the catalog; logback-classic 1.6.3 pulls slf4j-api 2.0.18 transitively. Satisfied, no change.
- [x] 2.2 Audit `androidApp/proguard-rules.pro` against AGP 9.2 stricter `-keepattributes` — serialization keeps rely on `RuntimeVisibleAnnotations` (explicitly listed) + `-if/-keepclassmembers`; stricter rule only drops RuntimeInvisible* from wildcard matches, not needed here. No edit. ⚠️ Follow-up: proguard keeps `io.ktor.client.engine.okhttp.**`; confirm engine-defaults resolves to OkHttp on Android in a release R8 build (task 2.3).
- [ ] 2.3 Build release Android bundle and smoke-test RevenueCat + serialization after the proguard audit
- [x] 2.4 Verify activity 1.13 auto edge-to-edge does not conflict with the manual `ThemeChanged` composable in `AppActivity` — `enableEdgeToEdge()` once in onCreate + `ThemeChanged` handles in-app theme toggle; 1.13 auto-re-applies on config change only. No conflict. Runtime-verify on device (rotation/dark-mode) recommended.
- [ ] 2.5 Run `verifyRoborazziJvm` on CI; if any golden renders a popup/menu/dropdown inside a dialog, re-record from the `roborazzi-goldens` CI artifact (1.74 popup-position fix)
- [ ] 2.6 Confirm pgjdbc security roll-up is in effect (no code); optionally set `channelBinding=require` (+ `scramMaxIterations`) on the JDBC URL if the DB is reached over an untrusted network

## 3. Tier 1 — core simplifications

### Ktor common client engine
- [x] 3.1 Verified `ktor-client-engine-defaults` 3.6.0 (KTOR-9795) covers all targets incl. js/wasmJs (Maven Central + Ktor docs). Web previously wired NO engine — defaults also fixes that.
- [x] 3.2 Added `ktor-client-engine-defaults` to `commonMain` (catalog entry + dep); removed OkHttp (androidMain + jvmMain) and Darwin (iosMain) engine deps
- [x] 3.3 Web is covered by defaults — no separate web engine wired (js/wasmJs had none before)
- [x] 3.4 Compile verified: JVM, JS, WasmJs, Android (Windows) + iOS (`:sharedUI:compileKotlinIosSimulatorArm64` BUILD SUCCESSFUL on macOS). Ktor engine-defaults resolves on all targets.

### buildConfig expect/actual key split
- [x] 3.5 Replaced the two common RC key fields with one `REVENUECAT_KEY` — `expect("")` in common (blank default), per-source-set `actual` in androidMain/iosMain via buildConfig 6.1 `sourceSets.named(...)`. Added `-Xexpect-actual-classes` opt-in for the now-expect/actual BuildConfig object.
- [x] 3.6 `PlatformModule.android.kt` / `PlatformModule.ios.kt` now read `BuildConfig.REVENUECAT_KEY`.
- [x] 3.7 Blank-key ⇒ Unavailable preserved (common default `""`; generated android actual is `""` with no local key). Compiled clean on JVM, Android, WasmJs, Js, iOS; `:sharedUI:jvmTest` green.

### nimbus JWKS modernization
- [x] 3.8 Replace `RemoteJWKSet(url)` with `JWKSourceBuilder.create(url).build()` in `AppleOAuthVerifier`. NOTE: `GoogleOAuthVerifier` uses google-api-client's `GoogleIdTokenVerifier`, not nimbus — no nimbus change applies there (gets 2.9.x truststore fix automatically, Tier 3).
- [x] 3.9 Delete the manual `exp` re-check in `AppleOAuthVerifier` (DefaultJWTClaimsVerifier enforces `exp`, which is in the required-claims set)
- [x] 3.10 Ran `:backend:test` against a real Postgres 16 (docker, harf/harf_password) → **BUILD SUCCESSFUL**, full suite green (nimbus 10.10 / Hikari 7.1 / pgjdbc batched-inserts all exercised).

## 4. Tier 2 — considered improvements (each independently landable)

- [x] 4.1 Added `kotlin.incremental.native=true` to `gradle.properties`. Speedup verifiable only with a Native/iOS build (macOS).
- [ ] 4.2 Migrate the `androidApp` release block to the AGP 9.3 `optimization { }` DSL; verify minify + resource shrink still work
- [~] 4.3 SKIPPED (verified API, declined on cost/benefit). Ktor 3.6's typed `jwt<T>("name") {}` requires `authenticateWith(schemeObject)` — it does **not** work with the existing string-name `authenticate("auth-jwt")`. Adopting it would thread the scheme object through all four route functions (SyncRoutes/AuthRoutes/SuggestionRoutes/wordPackRoutes), coupling them to `Application`'s auth setup, and spread an experimental opt-in annotation. The current `call.principal<JWTPrincipal>()?.payload?.subject` is decoupled via string name, non-experimental, and correct. Marginal cast-removal doesn't justify the coupling + experimental churn. Reconsider once the API stabilizes.
- [~] 4.4 SKIPPED (verified mechanism, declined on verifiability). KSafe 3.2 has no `KSafePlain` type — plain storage is the `KSafeWriteMode.Plain` / boolean overload of get/putDirect (default = encrypted). A safe idempotent migration is writable (re-read each key encrypted → rewrite plain, gated by a plain migration flag). BUT its actual purpose — preserving pre-existing *encrypted* values across the switch on Android/iOS — cannot be verified on this JVM/macOS box: desktop KSafe has no hardware keystore, so plain vs encrypted reads are indistinguishable and `:sharedUI:jvmTest` would not exercise the real migration path. Benefit (synchronous first-frame palette read) is already mitigated by the off-main async seed in `AppSettings.init`. Not shipping an unverifiable prefs-storage migration (data-stranding risk called out in design.md). Do on a device with a pre-populated encrypted store, or accept the async seed.
- [x] 4.5 Bundled fonts are GolosText / PT Serif / Lora — all ParaType/Cyrillic-complete families, so Uzbek Cyrillic is already covered; no per-glyph workaround exists to remove. Compose 1.12 web Noto fallback is free automatic insurance. No change needed.
- [x] 4.6 Added `addDataSourceProperty("reWriteBatchedInserts", "true")` to the HikariConfig in `DatabaseFactory.kt` (works regardless of env-provided URL). Compiles; runtime speedup exercised by the seed path against a real Postgres.

## 5. Close-out

- [ ] 5.1 Full compile-check across targets (JVM → Android → Wasm/JS → iOS) and run `:backend:test` + `:sharedUI:jvmTest`
- [ ] 5.2 Update `CLAUDE.md` if the Ktor engine wiring note or buildConfig key convention changed
