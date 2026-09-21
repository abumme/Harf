## Context

The dependency versions are already bumped in the catalog. This design covers only the non-trivial decisions in adopting the new APIs — the mechanical bumps and verification items need no design. All findings come from a changelog scan of each dependency's exact version range; file/line references in tasks are leads to confirm, not verified locations.

## Goals

- Delete per-platform boilerplate the new versions make unnecessary (Ktor engine, buildConfig keys).
- Modernize security-sensitive glue (nimbus JWKS, typed JWT auth) without behavior regressions.
- Land the one mandatory iOS edit so the purchases-kmp 3.x bump actually builds.

## Non-Goals

- Tier 4 speculative work (OIDC auto-discovery, Kotlin Swift export, Karma→Playwright, predictive-back transitions).
- Changing any user-facing behavior or declared requirement (hence `skip_specs: true`).

## Decisions

### 1. Ktor common engine vs. explicit per-platform engines

**Decision:** Adopt `io.ktor:ktor-client-engine-defaults` in `commonMain` and remove the OkHttp (android/jvm) and Darwin (ios) engine deps, **conditional on verifying web (js/wasmJs) coverage** and accepting the loss of explicit engine choice.

- Trade-off: the defaults facade lets Ktor pick the engine per target. We give up explicit OkHttp/Darwin selection and their engine-specific config.
- Guardrail: if any platform needs a specific engine (e.g. OkHttp interceptors, connection tuning), keep an explicit engine on that source set only — the facade allows this. Verify web is actually covered before deleting the JS engine dep.
- If web is not covered by defaults, keep an explicit web engine and still commonize the JVM+Android+iOS trio.

### 2. buildConfig key split (expect/actual)

**Decision:** Declare a single `expect`-ed RevenueCat key field, actual-ized per source set via buildConfig 6.0 `KotlinSourceSet.buildConfig` overloads, replacing the two common fields.

- Removes the cross-platform leak of each store's key into `commonMain`.
- `PlatformModule.android.kt` / `PlatformModule.ios.kt` read the single actual constant instead of picking one of two.
- Keep the blank-key ⇒ Unavailable behavior intact (no crash on missing key).

### 3. nimbus JWKS source

**Decision:** Replace `RemoteJWKSet(url)` with `JWKSourceBuilder.create(url).build()` in both Apple and Google verifiers; delete the manual `exp` re-check (DefaultJWTClaimsVerifier enforces it).

- Gains caching + outage tolerance so a transient JWKS fetch failure no longer fails every login.
- Behavior-equivalent for valid tokens; verify the existing test suite still passes.

### 4. KSafe plain view migration

**Decision:** Move non-secret keys (palette, onboarded, entitlement mirror) to a `KSafePlain` view; keep secrets (refresh token / session) encrypted.

- Requires a one-time read-old-encrypted → write-plain migration, since existing values were written encrypted.
- Enables the first-frame palette read to be synchronous without the decrypt penalty its current comment calls out.

## Risks

- **R8/proguard (AGP 9.2 stricter `-keepattributes`):** wildcard rules no longer match runtime-invisible annotations — RevenueCat + kotlinx.serialization are the blast radius. Audit before shipping release build.
- **Ktor web engine gap:** if defaults doesn't cover js/wasmJs, removing the JS engine dep breaks the web build. Verify first.
- **Roborazzi golden shift:** 1.74 popup-in-dialog fix changes pixels; re-record affected goldens from CI, not locally.
- **KSafe migration:** dropping mode without migrating strands old encrypted values. Migrate on read.

## Rollout / order

1. Mandatory iOS SPM removal + dashboard check (unblocks iOS build).
2. Verification-only sweep (security, SLF4J, proguard audit) — cheap, de-risks the build.
3. Tier 1 simplifications (Ktor engine, buildConfig split, nimbus).
4. Tier 2 improvements, each independently landable.

Compile-check order per CLAUDE.md: JVM → Android → Wasm/JS → iOS.
