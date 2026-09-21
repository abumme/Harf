## Context

See proposal.md for why. Current state that shapes the approach:

- **Players.** `users(id, created_at, name)`, `oauth_identities(user_id, provider, provider_subject)` and `refresh_tokens` (30 days, rotation). Rotation inserts a new row and marks the old one `replaced_by` without revoking it.
  - Access tokens are stateless HMAC JWTs with `sub` only, valid 15 minutes.
  - No player email is stored. `oauth_identities` has a unique index on `(provider, provider_subject)` but no index on `user_id`; `users` has no index on `created_at`.
- **Account deletion.** `AuthServerService.deleteAccount(userId)` revokes Apple tokens for linked Apple identities and then deletes the `users` row. The delete cascades to identities, refresh tokens and `user_stats`, and sets `word_suggestions.suggested_by` to NULL. `logout(userId)` revokes all refresh tokens.
- **App refresh failure.** In `KtorServices.executeWithAuthRetry`, a 401 triggers a refresh. When the refresh fails the app clears its session (`sessionStore.clear()`), and the next `ensureSession()` creates a fresh anonymous account. Local results stay on the device and upload to that new account. A linked player who signs in again is merge-linked back to the original account (the fresh anonymous one is discarded).
- **Stats.** `user_stats.data` is the full `List<ResultRecordDto{language, puzzleDay, won, attempts}>` of the latest accepted snapshot. `updated_at` is that snapshot's device timestamp, accepted only when not more than 5 minutes ahead of server time.
- **App stats rules.** `Streaks.streak/stats` live in `:sharedUI` (`data/stats/Streaks.kt`) over `ResultRecord`:
  - current streak is live only while the last won day is today or yesterday
  - games, wins and win rate come from the records
  - "today" is the language's puzzle day from `DailyPuzzleProvider`'s private timezone map: uz/en Asia/Tashkent, kk Asia/Almaty, ru Europe/Moscow
- **Suggestions.**
  - Every non-2xx suggestion response makes the current app show `suggest_failed` ("Couldn't send, try again" / "Не удалось отправить, повторите" / "Yuborilmadi, qayta urining").
  - The suggestion call does not refresh on 401.
  - `SuggestionServerService.authorLabel` reads `users.name`, falling back to "Аноним".
- **Privacy policy.** `docs/legal/privacy-{en,ru,uz,kk,tr}.md` §2.2 says name, email and photo are not stored. The display name confirmed at link time *is* stored in `users.name`, and word suggestions are not listed at all.
- **From `staff-auth-roles`:**
  - `staff-session` authentication provider
  - `Permission` enum with `Role.permissions` (ADMIN = all)
  - route-scoped `RequirePermission`
  - `AuditLog.record(...)` inside the mutation's transaction
  - `ApiErrorResponse` error envelope and XSRF on mutating routes
  - the Kobweb panel `:adminWeb` (Compose HTML + Silk): app shell with permission-filtered navigation, the `/auth/me` session state, the `AdminApi` fetch client (credentials, `X-XSRF-TOKEN`, 401 → login with `?next=`, 403 → forbidden view) and the shared server-paged table component
  - admin DTOs and `AdminRoutes` in `:sharedData` under `uz.abumme.harfgame.data.admin.<area>`, compiled into both the backend and the panel, so the types themselves are the API contract

## Goals / Non-Goals

**Goals:**
- One place for ADMINs to find a player and apply the four support/moderation actions safely.
- The backend's stats and streak numbers are exactly the app's, by sharing the code.
- A block takes effect on the next suggestion without an app release.

**Non-Goals:**
- Device or network bans. A blocked player can reinstall and get a new anonymous account; blocks attach to an account only.
- Editing player stats, merging or unlinking accounts, renaming a player to a chosen name, or content rules for display names.
- Blocking sign-in, sync or play for a player (the game is offline-first).
- Invalidating already-issued access tokens (would need a server-side token check on every player request).
- Auditing views and searches.
- Player-facing notice of a block.
- Any `:sharedUI` behavior change.

## Decisions

### Admin API: `/api/v1/admin/players`
Backend code in `backend/src/main/kotlin/uz/abumme/harfgame/backend/admin/players/{PlayersRoutes,PlayersService}.kt`. DTOs and route paths live in `:sharedData` (`sharedData/src/commonMain/kotlin/uz/abumme/harfgame/data/admin/players/PlayersDtos.kt`, paths added to `AdminRoutes`), so the backend and the Kobweb panel compile against the same types. New permissions `PLAYERS_READ` and `PLAYERS_WRITE`, granted to ADMIN only; WORDER gets 403 from `RequirePermission`. Mutations go through the XSRF check from `staff-auth-roles`.

| Method & path | Permission | Result |
|---|---|---|
| `GET /players?q&type&createdFrom&createdTo&blocked&cursor&size` | `PLAYERS_READ` | `200 PlayerSearchPageDto {items: [PlayerSummaryDto], nextCursor?}`; `422 validation_failed` |
| `GET /players/{id}` | `PLAYERS_READ` | `200 PlayerDetailDto`; `404 not_found` |
| `POST /players/{id}/delete` `{confirmAccountId}` | `PLAYERS_WRITE` | `204`; `422 validation_failed` on mismatch; `404` |
| `POST /players/{id}/end-sessions` | `PLAYERS_WRITE` | `200 {revoked: n}`; `404` |
| `PUT /players/{id}/suggestion-block` | `PLAYERS_WRITE` | `200 PlayerDetailDto` (idempotent, keeps the original who/when); `404` |
| `DELETE /players/{id}/suggestion-block` | `PLAYERS_WRITE` | `200 PlayerDetailDto` (idempotent); `404` |
| `DELETE /players/{id}/display-name` | `PLAYERS_WRITE` | `200 PlayerDetailDto` (idempotent); `404` |

**DTOs:**
- `PlayerSummaryDto {id, displayName?, providers: [GOOGLE|APPLE], createdAt, suggestionsBlocked}`
- `PlayerDetailDto {id, createdAt, displayName?, providers, lastStatsSnapshotAt?, activeSessions, languages: [{lang, games, wins, winRate, currentStreak, bestStreak}], suggestionCounts {pending, accepted, rejected}, recentSuggestions: [{id, lang, word, status, createdAt, decidedAt?}] (20 newest), suggestionBlock? {blockedAt, blockedBy {staffId, username}}}`

Request and small result bodies are shared DTOs too (`DeletePlayerRequest {confirmAccountId}`, `EndSessionsResultDto {revoked}`). The search query parameters are defined once by a shared `PlayerSearchQuery` data class with `toQueryParameters()` / `fromQueryParameters()`, so the panel and the route build and parse them identically. Timestamps use `kotlinx.datetime.Instant`, which serializes the same on JVM and JS.

No DTO has a field for provider subjects or tokens, so they cannot leak by accident. Because every DTO is also compiled into the browser bundle, that property is what keeps secrets out of the panel's data path.

**Why deletion confirms in the body.** The account id must be sent again in the body, so a stray or replayed request with only the path cannot delete. `POST …/delete` rather than `DELETE` with a body, because some intermediaries drop DELETE bodies.

*Alternatives rejected*:
- A single `PATCH /players/{id}` for all actions: one audit action per endpoint is clearer, and the permission and confirmation rules differ per action.
- UI-only confirmation: doesn't protect against API mistakes.

### Search: keyset paging, no trigram index yet
- **Query shape.** `users` joined to the aggregated `oauth_identities` provider set, ordered `created_at DESC, id DESC`.
  - `cursor` is an opaque base64 of the last row's `(created_at, id)`; the next page is `WHERE (created_at, id) < (:c, :id)`.
  - Page size defaults to 50, max 100. `size + 1` rows are fetched to decide `nextCursor`.
  - Keyset paging gives the spec's "no repeat, no skip" across pages while new accounts keep arriving; offset paging would shift.
- **`q` rules.**
  - A UUID-shaped `q` becomes an exact `id =` match and ignores the name filter.
  - Otherwise `q` is trimmed, requires at least 2 characters (`422` below that), and becomes `lower(name) LIKE lower(:pattern) ESCAPE '\'`, with `%`, `_` and `\` escaped.
- **Account type.**
  - anonymous: `NOT EXISTS (identity for user)`
  - Google / Apple: `EXISTS (identity with provider)`
- **Other filters.** Blocked: `suggestions_blocked_at IS NOT NULL` or `IS NULL`. Created range: `createdFrom` inclusive, `createdTo` exclusive; ISO dates are interpreted as Asia/Tashkent midnights, the editors' clock also used by the daily report.
- **New indexes** (additive):
  - `idx_users_created_at` on `users(created_at)`, for ordering and the range filter
  - `idx_oauth_user` on `oauth_identities(user_id)`, for the type filters, provider aggregation and the delete cascade

**Why a plain `LIKE`.** Only linked accounts have a name. Admin search volume is a few queries a minute. A sequential filter over a narrow `users` table stays in the tens of milliseconds up to roughly a million rows, and it needs no extension.

*Alternatives rejected*:
- `pg_trgm` GIN index on `lower(name)`: needs `CREATE EXTENSION` and an expression index, which the `MigrationUtils` diff does not manage. Revisit when name search exceeds ~500 ms.
- Offset paging: repeats or skips rows as accounts are created.
- Full-text search: names are not prose.

### Stats summary: move the app's rules into `:sharedData`
- **Move.** `Streaks` moves to `:sharedData` (package `uz.abumme.harfgame.data.stats` unchanged), with `streak(records, language, today)` and `stats(records, language)` over `ResultRecordDto`.
  - The `:sharedUI` `ResultRecord` maps with its existing `toDto()`.
  - `StatsScreen`, `GamesServices` and the tests keep calling `Streaks` with mapped records.
- **Timezones.** The per-language timezone map lives in `:sharedData` as `PuzzleDays.epochDay(languageId, instant)`, moved there by `daily-word-calendar` (applied earlier); `DailyPuzzleProvider.epochDay` delegates to it.
  - If an earlier-applied change (e.g. `daily-word-calendar`) has already moved this map, reuse that one instead.
- **Server side.**
  - `PlayersService` decodes `user_stats.data` for one player on demand.
  - Per language it computes games, wins, win rate and streaks, with `today = PuzzleDays.epochDay(lang, now)`.
  - `lastStatsSnapshotAt` is `user_stats.updated_at`; the spec calls it the latest accepted snapshot.

*Alternatives rejected*:
- Re-implementing the rules in `:backend` with copied fixtures: two definitions drift.
- Waiting for `analytics-core`'s `game_results`: this change must not depend on it, and a single player's JSON is small.

### Active sessions
`COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL AND replaced_by IS NULL AND expires_at > now()`. Each device holds one chain head, so this approximates signed-in devices.

### Reuse the player auth paths for delete and end-sessions
- **Delete.** `PlayersService.delete` calls `AuthServerService.deleteAccount(userId)`, so admin deletion is the self-deletion code path: Apple revocation first, then the cascading delete.
  - The confirmation check and the 404 check run before revocation.
  - The audit entry `PLAYER_DELETED` (target `PLAYER`, id; details `{providers: [...]}`) must commit together with the delete. `deleteAccount` gains an optional `inTransaction: () -> Unit` hook, called inside its delete `dbQuery`, which `PlayersService` uses to call `AuditLog.record`.
- **End sessions.** `endSessions` wraps `AuthServerService.logout(userId)` in the same way, returning the number of rows revoked, with audit `PLAYER_SESSIONS_ENDED` (details `{revoked: n}`).

*Alternatives rejected*:
- A separate admin delete implementation: a second path that could forget Apple revocation.
- Auditing after the delete in a new transaction: a crash between the two loses the entry, which the audit spec forbids.

### Block: two nullable columns and a first check in `suggest`
- **Schema.** `users.suggestions_blocked_at timestamp NULL` and `users.suggestions_blocked_by varchar(36) NULL` (the staff id; no FK, staff are never deleted).
  - Block: `UPDATE … SET … WHERE id = ? AND suggestions_blocked_at IS NULL`, then read back. A second block changes nothing and keeps the original who/when.
  - Unblock clears both columns.
  - Audit actions `PLAYER_SUGGESTIONS_BLOCKED` / `PLAYER_SUGGESTIONS_UNBLOCKED` are written only when a row changed.
- **In `suggest`.** `SuggestionServerService.suggest` first reads `users.suggestions_blocked_at` for the caller and returns a new `SuggestOutcome.Blocked`, before trimming, validation, the duplicate check, the cap count or insert. `SuggestionRoutes` maps it to `403 ApiErrorResponse("suggestions_blocked", "Suggestions are blocked for this account")`.
- **Why 403 works with the current app.** `KtorSuggestionService` treats any non-2xx as `ApiResult.Error`, so the app shows `suggest_failed`: truthful, no false "sent for review", no client release. A 403 does not trigger the app's 401 refresh path. A future client can show a specific message by matching `suggestions_blocked`.

*Alternatives rejected*:
- `202 PENDING` silently dropped (shadow block): tells the player "sent for review" when it was not, and contradicts "refused".
- `400 rejected`: same app message, but wrong semantics; the word is not the problem.
- `429`: implies waiting helps.
- A separate `player_blocks` table: one flag per account needs no history table, and the audit log keeps the history.

### Clear display name
`UPDATE users SET name = NULL WHERE id = ?`, with audit `PLAYER_DISPLAY_NAME_CLEARED` (details `{}`; the removed name is personal data and is not kept). The review worker resolves `authorLabel` when it sends, so later Telegram messages show "Аноним". Messages already sent are not edited.

### Panel: Players list and detail (Kobweb pages in `:adminWeb`)
- **Access.** Two `@Page`s: `/players` and the dynamic route `/players/{id}`. Both render inside the shell's permission check for `PLAYERS_READ`; a WORDER opening either URL directly sees the forbidden view. The "Игроки" entry is added to the shell's permission → navigation map only for `PLAYERS_READ`. Mutating actions are shown only with `PLAYERS_WRITE`.
- **Dynamic route and export.** The static export skips `/players/{id}` because it is a dynamic route. A hard refresh on `/harf/admin/players/<uuid>` reaches the backend's `staticFiles` fallback (`default("index.html")`), and the Kobweb router renders the detail page client-side from the URL. The id comes from the page context's route parameters; a malformed id shows "not found" without calling the API.
- **List page (`/players`).**
  - Search `TextInput`: a UUID means exact id, otherwise name (at least 2 characters, mirroring the API's 422).
  - Filters: account type as a row of Silk `Button` toggles (all / anonymous / Google / Apple), creation range as two native `<input type="date">` fields, blocked as a Silk `Switch`.
  - Table: the shared server-paged table component from `staff-auth-roles`, in cursor mode — short id with a copy button, name, provider badges, created, blocked badge. A "Показать ещё" button appends the next page from `nextCursor`; changing any filter resets the cursor state.
  - Filters are kept in the URL query string (through `PlayerSearchQuery`) so a search can be reloaded or shared. Navigation uses Kobweb's base-path-aware routing, so links resolve under `/harf/admin/` in production.
- **Detail page (`/players/{id}`).**
  - Header: id with copy, created, name, providers, last snapshot, active sessions.
  - Per-language stats table (Compose HTML `<table>` styled with a Silk `CssStyle`).
  - Suggestion counts and a recent suggestions table.
  - Block status line (who/when).
  - Action buttons, each opening a confirmation dialog built on Silk `Overlay`:
    - "Очистить имя"
    - "Заблокировать предложения" / "Разблокировать"
    - "Завершить все сессии" (notes that access lasts up to 15 minutes)
    - "Удалить аккаунт": the dialog's `TextInput` must equal the full account id before the submit `Button` is enabled; afterwards the panel navigates back to `/players` with a notice
  - After a successful action the page replaces its state with the returned `PlayerDetailDto`; a 404 (account deleted meanwhile) shows "not found".
- **Pure logic kept testable.** Filter ↔ query mapping (`PlayerSearchQuery`), the cursor paging state (append, reset on filter change, end when `nextCursor` is null) and the delete-confirmation match (trimmed input exactly equals the account id, case-sensitive) are plain Kotlin functions in `:adminWeb`, covered by `:adminWeb:jsTest`. The composables only wire them to Silk widgets; the UI itself is verified manually against a running backend.
- **Plumbing.** Russian strings go in the panel's central strings object. Requests go through the shell's `AdminApi` client with the shared DTOs, which supplies the XSRF header, the 401 redirect and error-message parsing.

### Privacy policy wording
In every locale file `docs/legal/privacy-{en,ru,uz,kk,tr}.md`:
- **§2 (data processed):**
  - fix §2.2: the display name the user confirms when signing in *is* stored
  - add word suggestions (word, language, time, decision) linked to the account until it is deleted
- **§5 (storage and security):** add "Authorized administrators of the Application may access account data (account identifier, sign-in provider type, display name, game statistics and word suggestions) only for support, moderation and abuse prevention; administrator access is limited to administrator accounts and their actions are logged."
- **§6.1:** deletion may also be carried out by an administrator at the user's request.

Coordinate with `add-legal-web-pages`: its HTML pages are built from these sources, so whichever lands second must regenerate or re-check the HTML text.

## Risks / Trade-offs

- **[Accidental deletion of the wrong account]** → the panel requires typing the full id, the API requires a matching `confirmAccountId`, and every deletion is audited with the acting ADMIN. Deletion is irreversible by design (it mirrors the player's own right to deletion).
- **[An ended or deleted session keeps working for up to 15 minutes]** → the spec and panel copy both state it. Refresh fails immediately, which is what matters for a stolen long-lived session.
- **[Access token still valid after deletion]** → a stats upload or suggestion in that window hits the missing `users` row, fails with an FK violation (500), and stores nothing. The app keeps its pending marker and, after the token expires, starts a fresh anonymous account. Mapping this to 401 is a later nicety, not a data risk.
- **[Linked player surprised by end-sessions]** → the app silently falls back to a new anonymous account; signing in again merge-links back and restores server stats. The panel copy says the player must sign in again.
- **[Block evasion by reinstalling]** → accepted non-goal. Blocks target repeat submitters on one account; the per-account daily cap and dictionary verification still apply to new accounts.
- **[A blocked anonymous account merge-links into a pre-existing account]** → the anonymous account and its block are deleted; the target account is not blocked. Rare; the ADMIN can block the target too.
- **[Plain LIKE name search slows down at scale]** → keyset paging bounds the row count per page. Add `pg_trgm` when search exceeds ~500 ms.
- **[Moving `Streaks` and the timezone map touches client code]** → a pure move with delegating call sites. The existing `StreaksTest` / `StatsIntegrationTest` and `:sharedUI:jvmTest` must stay green; no screenshot goldens are affected.
- **[Privacy text drifts from the hosted HTML]** → explicit coordination task with `add-legal-web-pages`.

## Migration Plan

1. Deploy through CI as usual. The startup migration adds `users.suggestions_blocked_at` and `users.suggestions_blocked_by` (nullable, no backfill) and the two indexes; no existing row changes. Players see no difference.
2. Smoke test on production with the ADMIN account:
   - search a known test account by id and by part of its name
   - open its detail
   - block it, then try a suggestion from that test device: it shows "couldn't send", and no Telegram message appears
   - unblock it
   - end its sessions: after 15 minutes the app is on a new anonymous account
   - check the audit log entries
3. **Rollback.** The previous image's schema diff sees unmapped columns and indexes and refuses the DROPs (the same guard observed in `suggestion-auto-accept`). Before redeploying it, run:
   ```sql
   DROP INDEX IF EXISTS idx_users_created_at;
   DROP INDEX IF EXISTS idx_oauth_user;
   ALTER TABLE users DROP COLUMN suggestions_blocked_at, DROP COLUMN suggestions_blocked_by;
   ```
   Blocks are lost; audit entries remain.

## Open Questions

- Default page size and the "recent suggestions" count (50 / 20) are tunable at implementation without changing specs or approach.

## Implementation notes (apply, 2026-09-17)

Details the design left open or got wrong, decided while implementing. None narrows a spec requirement.

- **Times are epoch milliseconds**, not `kotlinx.datetime.Instant`: every admin DTO since `staff-auth-roles` uses `Long` epoch ms, and the panel's formatters take them. Search dates stay ISO `LocalDate`s.
- **Shared contract** (`:sharedData`, `data.admin.players.PlayersDtos.kt`): providers are the existing `data.auth.OAuthProvider`; `SuggestionBlockDto.blockedBy` is the existing `data.admin.words.StaffRefDto` (`id`, `username`, `displayName?`); `PlayerLanguageStatsDto.winRate` is the app's own `Float`; `activeSessions` and the suggestion counts are `Long`. `PlayerSearchQuery.fromQueryParameters(Map<String, String>)` returns `ParsedPlayerSearch(query, invalid)`: the route answers the first malformed parameter with `422 <name>: invalid`, the panel just ignores it. `problems()` holds the rules both sides check (`q: too_short` under 2 characters, `size: invalid` outside 1..100); `accountId` (a UUID-shaped `q`, lowercased) and `namePart` split the search text. The `type` values in URLs are lowercase (`anonymous`, `google`, `apple`), parsed case-insensitively. `PlayerReasons` has `too_short` and `mismatch` (`confirmAccountId: mismatch`). Paths: `AdminRoutes.PLAYERS`, `player(id)`, `playerDelete`, `playerEndSessions`, `playerSuggestionBlock`, `playerDisplayName`. The suggestion endpoint's new code is `SuggestionErrors.BLOCKED` (`data.suggestion`).
- **Search.** Provider types come from a second query over the page's account ids (`idx_oauth_user`), not a join. The name match is `lower(name) LIKE '%…%' ESCAPE '\'` with `%`, `_` and `\` escaped (Exposed `LikePattern.ofLiteral`); Postgres' `lower()` folds Cyrillic under the `en_US.utf8` collation the official image uses. The cursor is base64url of `epochSecond:nano:id` of the page's last row; the keyset condition is `created_at < c OR (created_at = c AND id < id)`. A malformed cursor is `422 cursor: invalid`.
- **Auth hooks.** `AuthServerService.logout(userId, inTransaction: (revoked: Int) -> Unit = {}): Int` now revokes only tokens not revoked yet and returns their number (it used to re-stamp `revoked_at` on already revoked rows; refresh behaves the same). `deleteAccount(userId, inTransaction: () -> Unit = {})` runs the hook only when the `users` row was deleted, inside the delete's transaction. `main` builds one `AuthServerService` (`oauthVerifiersFromEnv()`) for the player API and `AdminBackend(playerAuth = …)`; `Application.module` passes its `authService` to its default `AdminBackend`.
- **Actions.** `PlayersService` (`search`, `detail` → null when unknown, `delete`, `endSessions`, `blockSuggestions`, `unblockSuggestions`, `clearDisplayName`) takes an `AuditActor.Staff`. Deletion checks existence (404) and then the confirmation (422, exact and case-sensitive) before `deleteAccount`; its entry is `{"providers": [...]}`. Ending sessions checks existence inside the logout transaction (a 404 rolls the empty revocation back) and is audited even when 0 tokens were revoked (`{"revoked": n}`). Block, unblock and clear-name run at READ COMMITTED as conditional updates and are audited only when a row changed, so clearing an account that has no name writes nothing either. Audit target `AuditTargets.PLAYER` carries no label; actions `PLAYER_DELETED`, `PLAYER_SESSIONS_ENDED`, `PLAYER_SUGGESTIONS_BLOCKED`, `PLAYER_SUGGESTIONS_UNBLOCKED`, `PLAYER_DISPLAY_NAME_CLEARED` (`AuditActions.PLAYER_PREFIX`); the panel's audit filter offers them only with `PLAYERS_READ`.
- **Blocked suggestions.** `SuggestOutcome.Blocked` is checked with one read of `users.suggestions_blocked_at` before anything else; an unknown account id is not blocked. Verified in the app code: `KtorSuggestionService` maps any non-2xx to `ApiResult.Error`, `SyncManager.suggestWord` does not refresh, and `GameScreen` shows `suggest_failed` for every error.
- **Stats rules.** `Streaks`, `StreakStats` and `PlayerStats` moved to `:sharedData` (`data.stats`, over `ResultRecordDto`); `StreaksTest` and `StatsIntegrationTest` stay in `:sharedUI` and map with `toDto()`, assertions unchanged. `PuzzleDays` was already shared by `daily-word-calendar` and is reused (a 23:59/00:00 test was added). `:sharedUI:verifyRoborazziJvm` fails locally on every screenshot with text (font rasterisation, as before this change); `stats_screen`'s compare image shows identical values, and that test builds its entries without `Streaks`.
- **Panel.** Pure state in `players/PlayersState.kt`: `PlayerFilters` (wraps `PlayerSearchQuery`; the "Создан по" date input shows the last included day while the query holds the exclusive next day), `PlayerListState` (first request without cursor, `append` drops a late page for changed filters, `nextRequest()` null at the end), `TypeFilter`, `deleteConfirmationMatches`, `deleteRequestFor`, `accountIdFromRoute`, `shortAccountId`; covered by `PlayersStateTest`. Pages `pages/Players.kt` (`/players`) and `pages/players/Player.kt` (`@Page("{id}")`). The blocked filter is Silk's `Switch` (on = only blocked; `blocked=false` from a URL is ignored), themed through the palette's `switch` colors in `AppEntry`; its hidden checkbox gets the label as `aria-label`. The account-type toggles are the panel's own `ActionButton`s with `aria-pressed`, like every other button in the panel, rather than Silk `Button`s. `DataTable` gained a `footer` slot (the "Показать ещё" row) and `ConfirmDialog` a `confirmEnabled` flag (the delete dialog). A deletion returns to `/players?deleted=1`, which shows the notice once. Ids are copied with `navigator.clipboard`.
- **Manual check** ran against the static export behind a local Caddy `/harf` proxy (not the dev server), with a WORDER and seeded accounts: search by id, name, type, both dates and blocked; load more; detail by direct load; block → the player's token gets `403 suggestions_blocked` and nothing is stored, stats upload still 200; unblock → `202`; clear name; end sessions → refresh `401`; delete with the typed id → list with notice, the id then shows "not found"; a malformed id requests nothing; WORDER has no Players entry and gets the forbidden view on both URLs and `403` on every player API route. `AdminWebServingTest` also asserts `/admin/players/<id>` serves `index.html`.
- **Privacy text.** Word suggestions became a new §2.7 (appended so §2.3–2.6 keep their numbers) and administrator access a new §5.3; the revision date is unchanged (the owner sets it when publishing). Not changed, but worth a follow-up: suggestion words and display names reach the editors' Telegram chat, and Telegram is not listed among the third parties in §4.
- **Test fixtures.** `AuthFixtures.kt` (`RecordingAppleRevoker`, `FixedOAuthVerifier`; `AppleRevokeOnDeleteTest` uses them) and `admin/players/PlayersTestSupport.kt` (`resetPlayerData`, `insertPlayer`, `insertStats`, `insertRefreshToken`, `userRow`).
