# Tasks

All file/line references are leads from the changelog scan — confirm each against the actual source before editing.

## 1. Mandatory (unblock iOS build)

- [ ] 1.1 Remove `PurchasesHybridCommon` + `PurchasesHybridCommonUI` from `iosApp/iosApp.xcodeproj`: Package Dependencies, Frameworks build phase, and the `XCRemoteSwiftPackageReference` block
- [ ] 1.2 Build the iOS app and confirm RevenueCat links via Gradle (kn-core/kn-ui) with no double-link/version-mismatch error
- [ ] 1.3 Dashboard-only: confirm the lifetime product is configured **non-consumable** so `restore()` keeps working under Play Billing 8

## 2. Verification-only sweep (cheap, de-risk the bumped build)

- [ ] 2.1 Confirm SLF4J on the backend classpath is ≥ 2.0.18 (logback 1.6.x requirement)
- [ ] 2.2 Audit `androidApp/proguard-rules.pro` against AGP 9.2 stricter `-keepattributes` (wildcards no longer match runtime-invisible annotations); check RevenueCat + kotlinx.serialization paths, add explicit attributes if relied upon
- [ ] 2.3 Build release Android bundle and smoke-test RevenueCat + serialization after the proguard audit
- [ ] 2.4 Verify activity 1.13 auto edge-to-edge (status-bar re-apply on config change) does not conflict with the manual `ThemeChanged` composable in `AppActivity`
- [ ] 2.5 Run `verifyRoborazziJvm` on CI; if any golden renders a popup/menu/dropdown inside a dialog, re-record from the `roborazzi-goldens` CI artifact (1.74 popup-position fix)
- [ ] 2.6 Confirm pgjdbc security roll-up is in effect (no code); optionally set `channelBinding=require` (+ `scramMaxIterations`) on the JDBC URL if the DB is reached over an untrusted network

## 3. Tier 1 — core simplifications

### Ktor common client engine
- [ ] 3.1 Verify whether `ktor-client-engine-defaults` covers the js/wasmJs web targets; record the finding
- [ ] 3.2 Add `io.ktor:ktor-client-engine-defaults` to `sharedUI` `commonMain`; remove the OkHttp (android/jvm) and Darwin (ios) engine deps
- [ ] 3.3 If web is not covered, keep an explicit web engine only; otherwise remove the JS engine dep too
- [ ] 3.4 Confirm `HttpClient { }` in `di/Koin.kt` builds and runs on JVM → Android → Wasm/JS → iOS; run existing Ktor client tests

### buildConfig expect/actual key split
- [ ] 3.5 In `sharedUI/build.gradle.kts`, replace the two common RC key fields with one `expect`-ed field actual-ized per source set (buildConfig 6.0 `KotlinSourceSet.buildConfig` overloads)
- [ ] 3.6 Update `PlatformModule.android.kt` / `PlatformModule.ios.kt` to read the single actual constant
- [ ] 3.7 Confirm blank-key ⇒ purchases Unavailable (no crash) still holds; build android + ios

### nimbus JWKS modernization
- [ ] 3.8 Replace `RemoteJWKSet(url)` with `JWKSourceBuilder.create(url).build()` in the Apple and Google OAuth verifiers
- [ ] 3.9 Delete the manual `exp` re-check (DefaultJWTClaimsVerifier enforces it)
- [ ] 3.10 Run `:backend:test`; confirm Google/Apple id-token verification tests pass

## 4. Tier 2 — considered improvements (each independently landable)

- [ ] 4.1 Add `kotlin.incremental.native=true` to `gradle.properties`; confirm iOS/Native debug build succeeds and rebuild is faster
- [ ] 4.2 Migrate the `androidApp` release block to the AGP 9.3 `optimization { }` DSL; verify minify + resource shrink still work
- [ ] 4.3 Adopt the Ktor typesafe JWT auth DSL (`jwt<Principal> { }` + `authenticateWith` + typed `call.principal`) in the backend auth routes; remove untyped principal casts; run `:backend:test`
- [ ] 4.4 Move non-secret KSafe keys (palette, onboarded, entitlement mirror) to a `KSafePlain` view with a one-time read-old-encrypted → write-plain migration; keep secrets encrypted; run sharedUI tests
- [ ] 4.5 Verify bundled fonts' Uzbek Cyrillic coverage; if incomplete, rely on Compose 1.12 stable web Noto fallback and remove any per-glyph web workaround
- [ ] 4.6 Set `reWriteBatchedInserts=true` on the JDBC URL; verify the bundled word-pack seed insert path still works and is faster

## 5. Close-out

- [ ] 5.1 Full compile-check across targets (JVM → Android → Wasm/JS → iOS) and run `:backend:test` + `:sharedUI:jvmTest`
- [ ] 5.2 Update `CLAUDE.md` if the Ktor engine wiring note or buildConfig key convention changed
