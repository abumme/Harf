## Why

When a player links a Google or Apple account, the provider knows the person's display name, but Harf discards it and the Settings account section can only show a generic "linked" state. We want the player to own their name: after signing in, show a confirmation dialog with a name field prefilled from the provider, let them confirm or edit it, and persist the confirmed name for a personalized account UI (and future server-side use).

## What Changes

- After a successful native sign-in, and **before** the link is finalized, the client shows a confirmation dialog: a name text field prefilled with the provider's suggested name, plus a confirm button. The user confirms or edits; the confirmed name is what gets linked.
- The provider's suggested name is read **client-side** for the prefill: Google via Credential Manager's `GoogleIdTokenCredential.displayName`; Apple via the native `fullName` (returned only on the first sign-in). Prefill falls back to the account's existing stored name, then to empty.
- A name is required to confirm: the confirm button is disabled while the field is blank.
- The confirmed name is sent in `LinkAccountRequest.displayName`; the server persists it as the account's display name (user-authoritative) and echoes it in `LinkAccountResponse.displayName`.
- The client stores the confirmed name in `SessionData.displayName` and shows "Signed in as X" in the Settings account section.
- Add a nullable `name` column to the `users` table.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `account-auth`: linking SHALL include a client-side name-confirmation step (field prefilled from the provider's suggested name, name required to confirm), and the server SHALL persist the confirmed name and return it so the client can display it.

## Impact

- **Shared DTOs** (`:sharedData`): `LinkAccountRequest.displayName?`, `LinkAccountResponse.displayName?`.
- **Backend** (`:backend`): `UsersTable` gains a nullable `name` column + migration; `AuthServerService.linkAccount` persists `request.displayName` and returns it (also on the merge-link/adopt path).
- **Client** (`:sharedUI`): `OAuthResult.Token.suggestedName?`; Android reads `GoogleIdTokenCredential.displayName`, iOS reads Apple `fullName`; the sign-in flow in `SettingsScreen` splits into sign-in → confirm dialog → link; `SyncManager.linkAccount` gains a `displayName` argument; `SessionStore`/`SessionData.displayName`; Settings shows the name.
- **Tests**: auth integration (confirmed name persisted, returned, applied on merge), serialization round-trip for new fields, client store round-trip.
- No breaking changes — all new DTO fields are optional/nullable and default-absent.
