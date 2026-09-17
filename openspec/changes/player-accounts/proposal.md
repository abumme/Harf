## Why

Staff can manage their own accounts (see `staff-auth-roles`), but there is still no way to act on a player account short of editing the production database by hand. Support and moderation keep needing the same few things: find an account, see its state, delete it for a player who can no longer reach the app, sign it out everywhere, stop an account that floods editors with junk suggestions, and remove an abusive display name. The name already appears to editors in Telegram as the suggestion author.

## What Changes

- ADMINs get a **Players** section in the web panel. WORDERs are refused and see no navigation for it.
- **Search** players, paged and newest first, by any combination of:
  - exact account id
  - display name (case-insensitive, matches part of the name)
  - account type (anonymous, Google, Apple)
  - creation date range
  - whether suggestions are blocked
- **Player detail** shows:
  - account id, creation time and display name
  - linked provider types only; provider subject ids and tokens are never shown
  - time of the latest accepted stats snapshot
  - number of active sessions
  - per-language stats from synced results: games, wins, win rate, and current and best streak, all computed with the app's own streak rule
  - suggestion counts by status and the most recent suggestions
  - suggestion-block status, with who set it and when
- **Actions**, each confirmed in the panel and recorded in the audit log with minimal details:
  - **Delete account**: the same effects as a player deleting their own account, including Apple token revocation. Suggestions are kept with the author cleared. The panel requires typing the account id to confirm.
  - **End all sessions**: revokes every refresh token, so the app can no longer refresh. Access tokens already issued stay valid until they expire, at most 15 minutes.
  - **Block / unblock suggestions**: pending suggestions from the account stay untouched.
  - **Clear display name**: the author label becomes anonymous from then on. Telegram messages already posted keep the old label, and the player's device may still show its locally stored name.
- A suggestion from a blocked account is refused before validation. It is never stored, verified or shown to editors. The response is a forbidden error, which the current app already reports with its existing "couldn't send" message, so no client release is needed.
- The streak and stats rule, plus the per-language puzzle timezones, move from `:sharedUI` into `:sharedData` so the backend computes exactly what the app shows. This is a refactor with no behavior change for players.
- The privacy policy states that authorized administrators may access account data for support, moderation and abuse prevention. It also now lists the display name and word suggestions, which are stored today but missing from §2.

## Capabilities

### New Capabilities
- `player-account-admin`: ADMIN-only search, detail view and administrative actions on player accounts (delete, end sessions, block/unblock suggestions, clear display name), all audited.

### Modified Capabilities
- `word-suggestions`: adds a requirement that suggestions from a blocked account are refused, never stored, never verified and never shown to editors.

## Impact

- **Depends on** `staff-auth-roles`: staff sessions, roles, the audit log, the Kobweb panel `:adminWeb` with its `AdminApi` client, and admin DTOs shared from `:sharedData`. Independent of `word-catalog` and `daily-word-calendar`. It adds a requirement to `word-suggestions` without modifying existing ones, so it applies cleanly whether or not `suggestion-auto-accept` is archived first.
- **Backend** (`:backend`):
  - new `admin/players` area (routes, service) reusing `AuthServerService` deletion and logout
  - `SuggestionServerService` / `SuggestionRoutes` check the block before anything else
- **Schema**, additive, applied by the startup migration:
  - `users.suggestions_blocked_at` and `users.suggestions_blocked_by`
  - indexes on `users(created_at)` and `oauth_identities(user_id)`
- **Shared code**:
  - `Streaks` and the puzzle-timezone map move to `:sharedData`, keeping package names
  - `:sharedUI` call sites delegate to them; the app's visible behavior is unchanged
  - player admin DTOs and route paths in `:sharedData` (`data.admin.players`, `AdminRoutes`), used by both the backend and the panel
- **Web panel** (`:adminWeb`, Kobweb): a Players list page and a dynamic player detail page, calling the backend through the shared DTOs.
- **Docs**: `docs/legal/privacy-{en,ru,uz,kk,tr}.md`, coordinated with the open `add-legal-web-pages` change (its HTML pages render these sources).
- **No app release**: API contracts the app uses are unchanged except the new forbidden response for blocked accounts, which the current app already handles.
