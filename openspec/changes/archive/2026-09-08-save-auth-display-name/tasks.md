## 1. Shared DTOs (:sharedData)

- [x] 1.1 Add optional `displayName: String? = null` to `LinkAccountRequest` and `LinkAccountResponse` in `AuthModels.kt`; verify `:sharedData` compiles.
- [x] 1.2 Extend `AuthSerializationTest.kt` with a round-trip covering `displayName` present and absent; run `./gradlew :sharedData:jvmTest` green.

## 2. Backend persistence (:backend)

- [x] 2.1 Add a nullable `name` column to `UsersTable` in `DatabaseTables.kt`; verify `DatabaseSchemaTest` passes (`./gradlew :backend:test --tests "*DatabaseSchemaTest"`).
- [x] 2.2 Provide the schema migration for the new nullable column; verify a fresh DB and an existing-row DB both start (existing rows get NULL).
- [x] 2.3 `AuthServerService.linkAccount` persists `request.displayName` on the new-identity insert path and returns it in `LinkAccountResponse.displayName`.
- [x] 2.4 On the merge-link/adopt path, apply the confirmed `request.displayName` to the adopted account and return it.
- [x] 2.5 Extend `AuthIntegrationTest`/`AccountLinkMergeTest`: confirmed name persisted and returned on a fresh link; confirmed name applied on merge-link. Run `./gradlew :backend:test` green.

## 3. Client: provider suggested name (:sharedUI)

- [x] 3.1 Add `suggestedName: String? = null` to `OAuthResult.Token` in `OAuthClient.kt`; verify `:sharedUI:jvmTest` compiles and `OAuthClientTest` passes.
- [x] 3.2 `AndroidGoogleOAuthClient` reads `GoogleIdTokenCredential.displayName` into `OAuthResult.Token.suggestedName`.
- [ ] 3.3 **DEFERRED** — iOS OAuth client captures Apple `fullName` into `OAuthResult.Token.suggestedName`. Blocked: iOS binds `NoOpOAuthClient` (no real Apple Sign In flow exists yet). The `suggestedName` contract is plumbed end-to-end and the dialog prefills from it, so this is a one-liner once an `ASAuthorizationController`-based iOS Apple client is built. Track under a future "iOS Apple Sign In" change.

## 4. Client: confirm dialog & link (:sharedUI)

- [x] 4.1 Split the settings sign-in flow: sign-in returns the token + `suggestedName`; on `Token`, open a name-confirmation dialog instead of linking immediately.
- [x] 4.2 Dialog holds a text field seeded with `suggestedName ?: existing session displayName ?: ""`, a confirm button disabled while the field is blank, and a dismiss/cancel that leaves the session unchanged.
- [x] 4.3 On confirm, call `SyncManager.linkAccount(provider, idToken, nonce, displayName = confirmedName)`; add the `displayName` argument through `SyncManager` into `LinkAccountRequest`.
- [x] 4.4 Add `displayName: String? = null` to `SessionData` and persist the response name via `SessionStore.saveSession`; verify a `SyncManagerTest`/store test round-trips the name.
- [x] 4.5 Show the stored display name in the `SettingsScreen` account section ("Signed in as X") when present; add the needed string resource(s).
- [x] 4.6 Record updated Roborazzi goldens if the account section/dialog changed the rendered layout (`./gradlew :sharedUI:recordRoborazziJvm`) and commit them.

## 5. Verification

- [x] 5.1 Backend suite green: `./gradlew :backend:test`.
- [x] 5.2 Shared + client tests green: `./gradlew :sharedData:jvmTest :sharedUI:jvmTest`.
- [x] 5.3 `openspec validate save-auth-display-name --strict` passes.
