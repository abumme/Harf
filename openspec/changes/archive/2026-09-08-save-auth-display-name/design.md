## Context

See proposal.md — Why. Today `SettingsScreen.linkWith` runs sign-in and `SyncManager.linkAccount` back-to-back with no user interaction in between; the provider name is discarded at every layer and `users` stores only `{id, createdAt}`. This change inserts a user-confirmation step between sign-in and link, and persists the confirmed name.

Provider name availability drives the client-side prefill:

| Provider | Suggested name source (client) | Present when |
|---|---|---|
| Google | Credential Manager `GoogleIdTokenCredential.displayName` | every sign-in |
| Apple | native authorization `fullName` | **first sign-in ever**, once |

## Goals / Non-Goals

- **Goals**: a confirm dialog after sign-in with the name prefilled from the provider; user confirms/edits; the confirmed (non-blank) name is persisted server- and client-side and shown in Settings.
- **Non-Goals**: standalone name editing later (outside the link flow), name uniqueness/validation beyond non-blank, exposing name over sync/leaderboard APIs, a dedicated profile screen, persisting email.

## Decisions

- **Confirm UI is a dialog inside the Settings account section**, not a new screen/route. The flow is a short two-step interaction anchored to the existing account block; a dialog avoids new navigation wiring and an MVI screen. Revisit only if profile fields multiply.
  - *Alternative rejected*: full MVI screen via mk-screen — more code and a new route for one field.

- **The confirmed name is client-authoritative; the server stores `request.displayName` verbatim.** The user explicitly typed/approved it, so there is no reason to prefer a token claim. The backend does **not** read the Google `name` claim — the verifier is untouched.
  - *Alternative rejected*: server reads Google name and prefers it — pointless once the user confirms, and adds verifier plumbing.

- **Name is read client-side for the prefill.** Android: `GoogleIdTokenCredential.displayName` (already parsed by Credential Manager). iOS: Apple `fullName` from the authorization. Carried out of the client as `OAuthResult.Token.suggestedName`.

- **Prefill precedence: provider suggestion → existing stored name → empty.** This covers a second Apple sign-in (no provider name) by showing the current name for re-confirmation, replacing any server-side set-if-null logic.

- **Name required (confirm disabled while blank).** Keeps the account from ending up with an empty string; a linked account either has a real name or, if the user never links, none at all.

- **Split `linkWith` into two steps.** Sign-in returns `Token(idToken, nonce, suggestedName)`; the UI opens the dialog seeded with the prefill; on confirm, `SyncManager.linkAccount(provider, idToken, nonce, displayName)` runs. Cancel = no link.

- **Merge-link applies the confirmed name.** The user confirmed a name this session, so it wins on the adopted account (consistent with "the user's explicit choice"). Stats still follow the existing "pre-existing account's data wins" rule — name is a separate, user-driven field.

- **New DTO fields optional/nullable, default-absent** — old clients/servers keep working; no version bump.

## Risks / Trade-offs

- **Apple name is once-only** → if the iOS client misses `fullName` on the first sign-in, the dialog opens blank and the user types it manually. Acceptable: the dialog still works, just without a suggestion. Capture point is introduced by this change (no iOS OAuth client exists yet).
- **Client-supplied name is unverified** → a user can type anything. Accepted: cosmetic display name for the user's own account, not an identity/authz input.
- **Extra tap in the link flow** → one dialog between sign-in and linked state. Intended: the whole point is user ownership of the name.
- **DB migration on live `users`** → one nullable column, additive/non-locking on Postgres; existing rows get NULL.

## Migration Plan

1. Ship shared DTO + `users.name` column together (backend tolerates absent `displayName`).
2. Add the nullable column via the project's migration path; existing rows unaffected (NULL).
3. Client change (confirm dialog, `OAuthResult.suggestedName`, `SessionData.displayName`, Settings UI) ships in the app; older app versions never send a name and keep working.
4. Rollback: the column can stay unused if the client reverts; nullable additive column needs no cleanup.

## Open Questions

- Where the confirmed name is echoed after login (only Settings for now) — safe to expand later without spec change.
