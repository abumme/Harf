## 1. Shared stats rules (:sharedData, :sharedUI)

- [x] 1.1 Move `Streaks` (`streak`, `stats`, `StreakStats`, `PlayerStats`) from `:sharedUI` `data/stats/Streaks.kt` to `:sharedData`, same package `uz.abumme.harfgame.data.stats`, operating on `ResultRecordDto`. Update `StatsScreen`, `GamesServices` and the existing tests to map `ResultRecord` with `toDto()`. Verify `./gradlew :sharedData:jvmTest :sharedUI:jvmTest` is green with `StreaksTest` and `StatsIntegrationTest` unchanged in assertions, and `:sharedUI:verifyRoborazziJvm` shows no diff.
- [x] 1.2 Move the per-language puzzle timezone map to `:sharedData` as `PuzzleDays.epochDay(languageId, instant)`, with `DailyPuzzleProvider.epochDay` delegating. Reuse the existing shared helper instead if an earlier-applied change already moved it. Add a `:sharedData` test for the 23:59/00:00 rollover in Asia/Tashkent, Asia/Almaty and Europe/Moscow, and verify `:sharedUI:jvmTest` stays green.

## 2. Schema and player service (:backend)

- [x] 2.1 Add nullable `suggestions_blocked_at` / `suggestions_blocked_by` to `UsersTable`, `idx_users_created_at` on `users(created_at)` and `idx_oauth_user` on `oauth_identities(user_id)`. Extend `DatabaseSchemaTest` to assert the columns and indexes exist on a fresh database, that a second `init` emits no statements, and that an existing user row reads back unblocked.
- [x] 2.2 Add `PLAYERS_READ` / `PLAYERS_WRITE` to `Permission`, granted only through ADMIN. Extend `AccessControlTest` to assert a WORDER has neither permission.
- [x] 2.3 Implement `PlayersService.search`:
  - keyset cursor over `(created_at DESC, id DESC)`, size default 50 / max 100, `size + 1` fetch for `nextCursor`
  - a UUID `q` means exact id; otherwise case-insensitive escaped `LIKE` on the name with a 2-character minimum
  - type filters anonymous / Google / Apple
  - created range as Asia/Tashkent dates (from inclusive, to exclusive)
  - blocked filter
  - provider types aggregated per row

  `PlayerSearchTest` covers each filter alone and combined; `ali` matching `Alisher` and `Vali`; a name containing `%`/`_` matched literally; a Google+Apple account matching both types; a 1-character `q` giving `422`; and paging that neither repeats nor skips when a new account is created between page 1 and page 2.
- [x] 2.4 Implement `PlayersService.detail`:
  - providers, `lastStatsSnapshotAt`, active sessions (`revoked_at IS NULL AND replaced_by IS NULL AND expires_at > now`)
  - per-language summary via the shared `Streaks` with `today = PuzzleDays.epochDay(lang, now)`
  - suggestion counts and the 20 newest suggestions
  - block who/when with the staff username

  `PlayerDetailTest` (injectable clock) asserts: the streak values for a three-day run ending yesterday; a lapsed streak gives 0 while best is kept; no stats gives an empty summary; a rotated token is not counted as active; an unknown id gives `null`/404; and the serialized DTO JSON contains no provider subject or token string.

## 3. Player actions (:backend)

- [x] 3.1 Add an optional in-transaction hook to `AuthServerService.deleteAccount` and `logout` (existing callers unchanged). Implement `PlayersService.delete(id, confirmAccountId, actor)`: 404 and confirmation check before Apple revocation, then `PLAYER_DELETED` audit (providers only) committed with the delete. `PlayerDeleteTest` with the recording Apple revoker from `AppleRevokeOnDeleteTest` asserts:
  - identities, refresh tokens and stats are gone
  - Apple revocation happened for the linked subject
  - suggestions keep `suggested_by = NULL`
  - refresh with the old token is rejected
  - a mismatched confirmation gives 422 with nothing deleted, no revocation and no audit entry
  - the audit entry has no subject
- [x] 3.2 Implement `PlayersService.endSessions` via `logout` with audit `PLAYER_SESSIONS_ENDED {revoked}`. The test asserts refresh with every previous token is rejected, the active session count becomes 0, and identities, stats, name and suggestions are unchanged.
- [x] 3.3 Implement block / unblock (conditional updates, idempotent, audit only when a row changed) and clear display name (audit `PLAYER_DISPLAY_NAME_CLEARED` with empty details). Tests assert:
  - a second block keeps the original staff id and time and writes no second entry
  - unblock clears both columns
  - pending suggestions stay `PENDING` after a block
  - after clearing, `authorLabel` returns "Аноним", a name search for the old name no longer returns the account, and the audit details do not contain the old name
- [x] 3.4 In `SuggestionServerService.suggest`, check `suggestions_blocked_at` before any trimming, validation, duplicate or cap logic and return `SuggestOutcome.Blocked`; `SuggestionRoutes` maps it to `403 ApiErrorResponse("suggestions_blocked", …)`. Extend `SuggestWordsTest`:
  - a blocked account's valid word gives 403 with no row stored, nothing queued for the review worker, and the daily cap count unchanged
  - an unblocked account's next valid word gives 202 PENDING
  - a blocked account's stats upload still succeeds

## 4. Admin API (:backend)

- [x] 4.0 Add the player admin DTOs (`PlayerSummaryDto`, `PlayerSearchPageDto`, `PlayerDetailDto` and its nested types, `DeletePlayerRequest`, `EndSessionsResultDto`), the shared `PlayerSearchQuery` with `toQueryParameters()` / `fromQueryParameters()`, and the player paths in `AdminRoutes` to `:sharedData` under `uz.abumme.harfgame.data.admin.players`. A `:sharedData` commonTest round-trips each DTO through JSON and a query through its parameters; verify `./gradlew :sharedData:jvmTest` passes and `./gradlew -PadminWebOnly :adminWeb:compileKotlinJs` still compiles.
- [x] 4.1 Implement `PlayersRoutes` per design.md under `authenticate("staff-session")` with `RequirePermission` (`GET /players`, `GET /players/{id}`, `POST /players/{id}/delete`, `POST /players/{id}/end-sessions`, `PUT`/`DELETE /players/{id}/suggestion-block`, `DELETE /players/{id}/display-name`) and wire them in `Application.module`. `PlayersAdminIntegrationTest` asserts:
  - ADMIN happy paths
  - WORDER gets 403 on every route with data unchanged
  - no session gives 401
  - a valid player JWT gives 401
  - a mutation without the XSRF header gives 403
  - an unknown id gives 404
- [x] 4.2 Make the routes decode `PlayerSearchQuery` and encode responses with the shared `:sharedData` DTOs only (no backend-local copies). `PlayersAdminIntegrationTest` deserializes every response body into the shared types, proving the panel can read them; verify it is green.

## 5. Players pages (:adminWeb)

- [x] 5.1 Register the `/players` and dynamic `/players/{id}` Kobweb `@Page`s inside the shell's permission check for `PLAYERS_READ`, and add the "Игроки" entry to the permission → navigation map. A `:adminWeb:jsTest` test asserts the entry is absent for a WORDER `me` and present for an ADMIN, and that the page-access check yields the forbidden view for a WORDER; verify `./gradlew -PadminWebOnly :adminWeb:jsTest` is green.
- [x] 5.2 Build the Players list page:
  - search `TextInput` (UUID means id, else name)
  - account-type toggle buttons, two native date inputs and a blocked `Switch`, all kept in the URL query through `PlayerSearchQuery`
  - the shared server-paged table component in cursor mode, with copyable short id, name, provider badges, created and blocked badge
  - "Показать ещё" loading via `nextCursor`, reset when a filter changes
  - Russian strings in the panel's strings object

  `:adminWeb:jsTest` asserts filters map to query parameters and are restored from the URL, and that the cursor state appends pages, resets on a filter change and stops when `nextCursor` is null. Verify `./gradlew -PadminWebOnly :adminWeb:jsTest` is green, then check manually in `:adminWeb:kobwebStart` against a local backend with seeded accounts: search by id and by name, each filter, and load more.
- [x] 5.3 Build the Player detail page at `/players/{id}`:
  - header facts, per-language stats table, suggestion counts and recent suggestions, block status
  - Silk `Overlay` confirmation dialogs: clear name; block/unblock; end sessions, stating up to 15 minutes of remaining access; delete, with the submit button disabled until the typed text equals the full account id, then navigating back to `/players` with a notice
  - a malformed id or a 404 shows "not found"

  `:adminWeb:jsTest` asserts the delete-confirmation match rejects a partial, differently-cased or empty id and accepts the exact (trimmed) id, and that the delete call builds `DeletePlayerRequest` with that id. Verify `./gradlew -PadminWebOnly :adminWeb:jsTest` is green and the `:adminWeb:kobwebExport` static export succeeds. Then verify manually against a local backend: each action updates the page, delete returns to the list, and a hard refresh on a `/players/<uuid>` URL served from the export renders the detail page through the `index.html` fallback.

## 6. Privacy policy (docs)

- [x] 6.1 In `docs/legal/privacy-{en,ru,uz,kk,tr}.md`:
  - correct §2.2 (the confirmed display name is stored)
  - add word suggestions to §2
  - add the administrator-access clause to §5
  - note administrator deletion on request in §6.1

  Verify by reading all five files that each locale carries the same four changes and no other text changed.
- [x] 6.2 Coordinate with `add-legal-web-pages`: if `docs/legal/privacy.html` exists, update its embedded locales to match; otherwise add a note to that change's task 2.4 to render the updated sources. Verify the HTML text (or the recorded note) matches the markdown.

## 7. Verification

- [x] 7.1 Full suites green: `./gradlew :backend:test` (CI Postgres), `./gradlew :sharedData:jvmTest :sharedUI:jvmTest`, `./gradlew -PadminWebOnly :adminWeb:jsTest`, and the `:adminWeb:kobwebExport` static export succeeds.
- [x] 7.2 `openspec validate player-accounts --strict` passes.
- [ ] 7.3 After deploy, run the smoke test on production with the ADMIN account and a test device:
  - find the test account by id and by part of its name
  - open its detail
  - block it and confirm a suggestion from the device shows "couldn't send" with no Telegram message
  - unblock it and confirm a suggestion is sent
  - end its sessions and confirm the device is on a new anonymous account after at most 15 minutes
  - confirm the audit log shows each action with no provider subject or name
