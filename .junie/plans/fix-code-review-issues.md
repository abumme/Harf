---
sessionId: session-260912-113550-b6gh
---

# Requirements

### Overview & Goals
Following the project-wide code review, this plan addresses technical debt, concurrency vulnerabilities, deprecated APIs, and incomplete platform features across the `sharedUI`, `backend`, `sharedData`, and platform app modules. The primary goal is to elevate the codebase to production readiness while strictly adhering to the "KMP First" and "thin wrapper" architectural principles.

### Scope
#### In Scope
- **Auth Concurrency (P1):** Prevent token refresh race conditions and invalidation caused by parallel 401 responses.
- **Native iOS Sign-in (P1):** Replace placeholder `NoOpOAuthClient` with native Sign in with Apple using `AuthenticationServices`.
- **Backend Optimization (P2):** Modernize database transactions and streamline snapshot upserts in stats synchronization.
- **Platform Hardening (P2):** Eliminate deprecated UIKit APIs (`keyWindow`, `setStatusBarStyle`) and replace unsafe Activity casting in Android Compose views.
- **Code Modernization (P3):** Replace deprecated Exposed `newSuspendedTransaction` and update `Instant` imports from Kotlin 2.x standard library.

#### Out of Scope
- Full Google Sign-In SDK integration on iOS (requires CocoaPods/SPM integration with Google SignIn framework; kept as `isGoogleSupported = false` on iOS).
- Migration from Exposed to another ORM.
- Visual redesign of existing Compose UI screens.

### User Stories
- As a player playing across multiple devices or returning after session expiration, I want my token refresh to succeed seamlessly without accidental logouts from duplicate rotation requests.
- As an iOS player, I want to sign in with my Apple ID using native iOS sheets so that my stats sync securely across sessions.
- As an Android player, I want the theme change and share dialogs to never crash regardless of view hierarchy context wrappers.

### Functional Requirements
- **Token Refresh Mutex:** When multiple HTTP calls simultaneously detect an expired access token, only the first call must invoke the backend refresh endpoint. Subsequent callers must await the new tokens and reuse them without making redundant network calls.
- **Sign in with Apple:** On iOS, tapping "Sign in with Apple" must present `ASAuthorizationController`, generate a bound nonce, and return a verified identity token and optional display name to link the account.
- **Safe Activity Context:** Android theme updates must safely locate the root `Activity` window using `tailrec` Context traversal.
- **Exposed Transactions:** All database queries must run through non-deprecated `suspendTransaction`.

### Non-Functional Requirements
- **Zero Deprecation Warnings:** Remove compiler warnings for `Instant` and `newSuspendedTransaction`.
- **Target Compatibility:** Ensure all changes compile across JVM, Android, and iOS targets (`iosArm64`, `iosSimulatorArm64`).

# Technical Design

### Current Implementation
- `KtorAuthService` in `sharedUI` executes `refreshToken()` without synchronization, while `KtorSyncService` maintains its own local mutex.
- `PlatformModule.ios.kt` provides `NoOpOAuthClient(isGoogleSupported = true, isAppleSupported = true)` returning `OAuthResult.NotConfigured`.
- `PlatformModule.ios.kt` queries `UIApplication.sharedApplication.keyWindow` which has been deprecated since iOS 13.
- `DatabaseFactory.kt` calls deprecated `newSuspendedTransaction(Dispatchers.IO, database)`.
- `AppActivity.kt` directly casts `(view.context as Activity)` which risks `ClassCastException` in wrapped Compose trees.

### Key Decisions
1. **Centralized Refresh Synchronization in `KtorAuthService`:**
   - *Chosen Approach:* Place a `Mutex` directly in `KtorAuthService` around token refresh logic.
   - *Rationale:* Ensures that any consumer (`KtorSyncService`, account deletion retries, background jobs) shares the same mutual exclusion and token reuse cache.
2. **Pure Kotlin/Native Sign in with Apple:**
   - *Chosen Approach:* Implement `IosAppleOAuthClient` in `sharedUI/src/iosMain` using Kotlin/Native bindings for `platform.AuthenticationServices`.
   - *Rationale:* Conforms to the project guideline that all logic belongs in `sharedUI`, keeping `iosApp` as a thin wrapper and avoiding extra Swift bridging layers.
3. **Atomic Upsert in `SyncServerService`:**
   - *Chosen Approach:* Use Exposed `UserStatsTable.upsert` inside `suspendTransaction`.
   - *Rationale:* Eliminates read-then-write race conditions where concurrent snapshots could overwrite newer updates.

### Proposed Changes

#### 1. `sharedUI/src/commonMain/kotlin/uz/abumme/harfgame/data/network/KtorServices.kt`
- Add `private val refreshMutex = Mutex()` in `KtorAuthService`.
- Check if stored tokens were already updated before making the POST request to avoid double-rotation:
  ```kotlin
  override suspend fun refreshToken(request: RefreshRequest): ApiResult<RefreshResponse> = refreshMutex.withLock {
      val current = sessionStore.get()
      if (current.refreshToken != null && current.refreshToken != request.refreshToken && current.accessToken != null) {
          return@withLock ApiResult.Success(
              RefreshResponse(TokensDto(current.accessToken!!, current.refreshToken!!))
          )
      }
      // Execute HTTP POST and update sessionStore...
  }
  ```

#### 2. `sharedUI/src/iosMain/kotlin/uz/abumme/harfgame/data/auth/IosAppleOAuthClient.kt`
- Implement `OAuthClient`:
  - `override val isAppleSupported = true`, `override val isGoogleSupported = false`.
  - Generate raw nonce and compute SHA-256 using iOS `CC_SHA256`.
  - Build `ASAuthorizationAppleIDProvider().createRequest()`.
  - Handle result in coroutine continuation via `suspendCancellableCoroutine<OAuthResult>`.
  - Return `OAuthResult.Token(idToken, nonce = rawNonce, suggestedName)`.

#### 3. `backend/src/main/kotlin/uz/abumme/harfgame/backend/db/DatabaseFactory.kt`
- Replace `newSuspendedTransaction` with `suspendTransaction(Dispatchers.IO, database) { block() }`.

#### 4. `sharedUI/src/iosMain/kotlin/uz/abumme/harfgame/di/PlatformModule.ios.kt` & `main.kt`
- Replace `keyWindow` with scene-based window retrieval:
  ```kotlin
  val window = UIApplication.sharedApplication.connectedScenes
      .filterIsInstance<UIWindowScene>()
      .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
      ?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow }
  ```

#### 5. `androidApp/src/main/kotlin/uz/abumme/harfgame/androidApp/AppActivity.kt`
- Add context helper:
  ```kotlin
  private tailrec fun Context.findActivity(): Activity? = when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
  }
  ```

### Architecture Diagram

```mermaid
graph TD
    subgraph Client [sharedUI Multiplatform]
        V[ViewModel / Screen] --> S[KtorSyncService]
        V --> A[KtorAuthService]
        S -->|refresh mutex| A
        A -->|Tokens| SS[SessionStore]
        P[PlatformModule] -->|iOS| AppleClient[IosAppleOAuthClient]
        P -->|Android| GoogleClient[AndroidGoogleOAuthClient]
    end

    subgraph Backend [Ktor Server]
        AuthRoute[/api/auth/*] --> AuthServer[AuthServerService]
        SyncRoute[/api/sync/*] --> SyncServer[SyncServerService]
        AuthServer --> DB[(DatabaseFactory / Exposed)]
        SyncServer --> DB
    end

    AppleClient -->|ASAuthorization| iOSKit[iOS AuthenticationServices]
    GoogleClient -->|CredentialManager| AndroidKit[Android Credentials API]
    A -->|HTTPS /api/auth| AuthRoute
    S -->|HTTPS /api/sync| SyncRoute
```

### File Structure Changes
```
sharedData/
  └── src/commonMain/.../data/sync/SyncModels.kt          (edit: Instant import)
sharedUI/
  └── src/commonMain/.../data/network/KtorServices.kt     (edit: token mutex)
  └── src/iosMain/.../data/auth/IosAppleOAuthClient.kt     (new: Apple Sign In)
  └── src/iosMain/.../di/PlatformModule.ios.kt            (edit: wire Apple client, keyWindow fix)
  └── src/iosMain/.../main.kt                             (edit: modernize status bar)
  └── src/androidMain/.../di/PlatformModule.android.kt    (edit: context-aware sharer)
androidApp/
  └── src/main/.../androidApp/AppActivity.kt              (edit: safe findActivity)
backend/
  └── src/main/.../backend/db/DatabaseFactory.kt          (edit: suspendTransaction)
  └── src/main/.../backend/service/SyncServerService.kt   (edit: atomic stats upsert)
```

# Testing

### Validation Approach
Verification relies on the project's automated test suites across JVM, Android, and iOS compilation pipelines:
1. **JVM Unit Tests:** Ensure core logic, serialization, and server flows remain intact.
2. **Android Compilation:** Verify that context unwrapping compiles cleanly in `:androidApp:assembleDebug`.
3. **iOS Native Compilation:** Verify that Kotlin/Native bindings (`AuthenticationServices`, UIKit window scenes) compile in `:sharedUI:compileKotlinIosSimulatorArm64`.

### Key Scenarios
- **Concurrent Token Refresh:** Verify that when multiple coroutines invoke `refreshToken()` simultaneously, only one network request is made and both callers receive valid tokens.
- **Apple Nonce & Token Verification:** Verify that nonces created by `IosAppleOAuthClient` match the format expected by backend's `AppleOAuthVerifier`.
- **Database Transactions:** Verify that all backend tests running transactional queries succeed without connection leaks or deprecation warnings.
- **Stats Snapshot Upsert:** Verify that `SyncServerService.uploadStats` rejects older timestamps and successfully writes newer ones.

### Test Changes
- Run `./gradlew :sharedData:jvmTest :sharedUI:jvmTest`
- Run `./gradlew :backend:test`
- Run `./gradlew :androidApp:assembleDebug`
- Run `./gradlew :sharedUI:compileKotlinIosSimulatorArm64`

# Delivery Steps

### ✓ Step 1: Synchronize token refresh and update Instant deprecations
Safe concurrent token rotation in `KtorAuthService` without redundant network calls, and modern `Instant` usage in `sharedData`.

- Add a private `refreshMutex = Mutex()` to `KtorAuthService` in `sharedUI/src/commonMain/kotlin/uz/abumme/harfgame/data/network/KtorServices.kt`.
- Wrap token refresh logic in `refreshMutex.withLock` and check if `sessionStore.get().refreshToken != request.refreshToken` before issuing network requests; return existing fresh tokens when already rotated by a concurrent call.
- Align `KtorSyncService.refreshOnce` with the centralized mutex behavior to eliminate race conditions between background sync and user-initiated API calls.
- Migrate `import kotlinx.datetime.Instant` to `import kotlin.time.Instant` in `sharedData/src/commonMain/kotlin/uz/abumme/harfgame/data/sync/SyncModels.kt` and `sharedData/src/commonTest/kotlin/uz/abumme/harfgame/data/SyncSerializationTest.kt`.
- Run `:sharedData:jvmTest` and `:sharedUI:jvmTest` to verify that serialization and auth flow tests pass cleanly without deprecation warnings.

### ✓ Step 2: Modernize backend database transactions and stats sync
Non-deprecated transaction lifecycle and race-free stats synchronization in backend services.

- Replace `newSuspendedTransaction` with `suspendTransaction` from `org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction` in `backend/src/main/kotlin/uz/abumme/harfgame/backend/db/DatabaseFactory.kt`.
- Refactor `uploadStats` in `backend/src/main/kotlin/uz/abumme/harfgame/backend/service/SyncServerService.kt` to use atomic `upsert` or single-transaction update semantics against `UserStatsTable`.
- Verify database migrations and schema consistency in `DatabaseFactory.init` to ensure no schema regressions.
- Execute `./gradlew :backend:test` to confirm that `StatsSyncIntegrationTest`, `DatabaseSchemaTest`, and authentication tests pass.

### ✓ Step 3: Harden platform bridges for Android and iOS
Safe Activity resolution on Android and modern scene-aware window presentation on iOS.

- Add a safe recursive context unwrapper `tailrec fun Context.findActivity(): Activity?` in `androidApp/src/main/kotlin/uz/abumme/harfgame/androidApp/AppActivity.kt` to prevent `ClassCastException` when `view.context` is a `ContextWrapper`.
- Update `AndroidSharer` in `sharedUI/src/androidMain/kotlin/uz/abumme/harfgame/di/PlatformModule.android.kt` to use `CurrentActivityProvider.current()` when available, avoiding unnecessary `FLAG_ACTIVITY_NEW_TASK` flags.
- Replace deprecated `UIApplication.sharedApplication.keyWindow` in `sharedUI/src/iosMain/kotlin/uz/abumme/harfgame/di/PlatformModule.ios.kt` with modern `UIWindowScene` foreground window lookup.
- Modernize status bar appearance handling in `sharedUI/src/iosMain/kotlin/main.kt` to avoid deprecated `setStatusBarStyle` calls.
- Verify Android build with `./gradlew :androidApp:assembleDebug`.

### ✓ Step 4: Implement native Sign in with Apple on iOS
Full native Sign in with Apple integration on iOS complying with App Store guidelines.

- Create `IosAppleOAuthClient` implementing `OAuthClient` in `sharedUI/src/iosMain/kotlin/uz/abumme/harfgame/data/auth/IosAppleOAuthClient.kt` using `platform.AuthenticationServices`.
- Generate cryptographically secure random nonces and calculate SHA-256 hashes matching the backend `AppleOAuthVerifier` verification protocol.
- Implement `ASAuthorizationControllerDelegateProtocol` and `ASAuthorizationControllerPresentationContextProvidingProtocol` to handle credential response, cancellation, and errors.
- Wire `IosAppleOAuthClient` into `platformModule` in `sharedUI/src/iosMain/kotlin/uz/abumme/harfgame/di/PlatformModule.ios.kt` with `isAppleSupported = true`.
- Verify iOS compilation via `./gradlew :sharedUI:compileKotlinIosSimulatorArm64`.