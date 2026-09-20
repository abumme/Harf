# Tasks

All file/line references are leads from the changelog scan — confirm each against the actual source before editing.

## 1. Mandatory (unblock iOS build)

- [ ] 1.1 Remove `PurchasesHybridCommon` + `PurchasesHybridCommonUI` from `iosApp/iosApp.xcodeproj`: Package Dependencies, Frameworks build phase, and the `XCRemoteSwiftPackageReference` block
- [ ] 1.2 Build the iOS app and confirm RevenueCat links via Gradle (kn-core/kn-ui) with no double-link/version-mismatch error
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
- [~] 3.4 Compile verified: JVM, JS, WasmJs, Android all BUILD SUCCESSFUL. **iOS compile not verifiable on this Windows box — must confirm on macOS.**

### buildConfig expect/actual key split
- [ ] 3.5 In `sharedUI/build.gradle.kts`, replace the two common RC key fields with one `expect`-ed field actual-ized per source set (buildConfig 6.0 `KotlinSourceSet.buildConfig` overloads)
- [ ] 3.6 Update `PlatformModule.android.kt` / `PlatformModule.ios.kt` to read the single actual constant
- [ ] 3.7 Confirm blank-key ⇒ purchases Unavailable (no crash) still holds; build android + ios

### nimbus JWKS modernization
- [x] 3.8 Replace `RemoteJWKSet(url)` with `JWKSourceBuilder.create(url).build()` in `AppleOAuthVerifier`. NOTE: `GoogleOAuthVerifier` uses google-api-client's `GoogleIdTokenVerifier`, not nimbus — no nimbus change applies there (gets 2.9.x truststore fix automatically, Tier 3).
- [x] 3.9 Delete the manual `exp` re-check in `AppleOAuthVerifier` (DefaultJWTClaimsVerifier enforces `exp`, which is in the required-claims set)
- [~] 3.10 Run `:backend:test` — compiles clean with nimbus 10.10; `OAuthVerificationTest` (2 tests) passes. Full suite needs a Postgres instance (83 DB tests failed on local `ConnectException`); CI runs against Postgres.

## 4. Tier 2 — considered improvements (each independently landable)

- [x] 4.1 Added `kotlin.incremental.native=true` to `gradle.properties`. Speedup verifiable only with a Native/iOS build (macOS).
- [ ] 4.2 Migrate the `androidApp` release block to the AGP 9.3 `optimization { }` DSL; verify minify + resource shrink still work
- [ ] 4.3 Adopt the Ktor typesafe JWT auth DSL (`jwt<Principal> { }` + `authenticateWith` + typed `call.principal`) in the backend auth routes; remove untyped principal casts; run `:backend:test`
- [ ] 4.4 Move non-secret KSafe keys (palette, onboarded, entitlement mirror) to a `KSafePlain` view with a one-time read-old-encrypted → write-plain migration; keep secrets encrypted; run sharedUI tests
- [x] 4.5 Bundled fonts are GolosText / PT Serif / Lora — all ParaType/Cyrillic-complete families, so Uzbek Cyrillic is already covered; no per-glyph workaround exists to remove. Compose 1.12 web Noto fallback is free automatic insurance. No change needed.
- [x] 4.6 Added `addDataSourceProperty("reWriteBatchedInserts", "true")` to the HikariConfig in `DatabaseFactory.kt` (works regardless of env-provided URL). Compiles; runtime speedup exercised by the seed path against a real Postgres.

## 5. Close-out

- [ ] 5.1 Full compile-check across targets (JVM → Android → Wasm/JS → iOS) and run `:backend:test` + `:sharedUI:jvmTest`
- [ ] 5.2 Update `CLAUDE.md` if the Ktor engine wiring note or buildConfig key convention changed
