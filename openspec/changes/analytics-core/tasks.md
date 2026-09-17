## 1. Preconditions (:backend)

- [x] 1.1 Confirm `staff-auth-roles`, `word-catalog` and `daily-word-calendar` are applied. Record the actual names design.md assumes: `words` columns and `source` values, removal/restore and `SUGGESTION_*` decision audit action names and their `details` shape for bulk entries, `daily_words` columns and `source`/`is_repeat` values, the calendar timezone map and eligibility/unused-word queries, and the ADMIN-only route guard. Adjust design.md where they differ. Verify by listing the names in a short note at the top of design.md's Context.

## 2. Game results capture (:backend)

- [x] 2.1 Add `GameResultsTable` (PK user/lang/puzzle_day, FK cascade, indexes `(lang, puzzle_day)` and `(puzzle_day)`) and `AnalyticsMetaTable`, and register both in the `DatabaseFactory` migration list. Verify `DatabaseSchemaTest` passes and deleting a `users` row removes its game results.
- [x] 2.2 Implement the plausibility filter (known pack language, attempts `1..6`, puzzle day ≤ the language's current puzzle day) as a pure function. Unit tests cover attempts 0 and 7, an unknown language, tomorrow's puzzle day at 23:59 and 00:01 in Europe/Moscow, and a valid record. Green.
- [x] 2.3 In `SyncServerService.uploadStats`, record valid records with insert-ignore after reconciliation, in their own transaction, for `Success` and `StoredSnapshotWon`; a recording failure is logged and swallowed. `StatsSyncIntegrationTest` additions all pass:
  - re-uploading the same snapshot adds no rows
  - a newer snapshot lacking a record keeps it
  - an older (`stored_snapshot_kept`) snapshot still records new records
  - a far-future upload records nothing
  - an implausible record is skipped while the others are stored
  - response bodies and status codes are unchanged
- [x] 2.4 Implement the snapshot sweep: `(updated_at, user_id)` watermark in `analytics_meta`, 500 snapshots per transaction, capped batches per call, same insert-ignore and filter, `received_at` = snapshot `updated_at`. Tests all pass:
  - a fresh database with stored snapshots is backfilled
  - a second run with no snapshot changes adds nothing and leaves the watermark unchanged
  - a snapshot updated after the watermark is re-read
  - a backfill larger than one tick's cap completes over several calls

## 3. Account events (:backend)

- [x] 3.1 Add `AccountEventsDailyTable` and nullable `linked_at` on `OAuthIdentitiesTable`, registered in the migration list. Verify `DatabaseSchemaTest` passes and an existing identity row reads back with `linked_at` NULL.
- [x] 3.2 Increment today's (Asia/Tashkent) deletion count inside `deleteAccount`'s delete transaction, and set `linked_at` when `linkAccount` inserts a new identity. Tests all pass:
  - self-deletion increments by one
  - a failed or not-found deletion does not
  - a merge-link orphan cleanup does not
  - a new link sets `linked_at`
  - re-linking an owned identity inserts nothing
  - `AccountLinkMergeTest` and `LogoutAndAccountDeletionTest` stay green

## 4. Day keys and rollup framework (:backend)

- [x] 4.1 Implement pure helpers `closedPuzzleDays(lang, now)`, `closedGlobalPuzzleDay(now)` (latest zone) and `closedEventDates(now)` (Asia/Tashkent) over the calendar's timezone map. Tests at 23:59:59 and 00:00:01 in Asia/Tashkent, Asia/Almaty and Europe/Moscow pass.
- [x] 4.2 Add `AnalyticsRollupDaysTable` and the rollup tables from design.md (`analytics_lang_day`, `analytics_global_day`, `analytics_cohort_day`, `analytics_word_day`, `analytics_accounts_day`, `analytics_suggestions_day`, `analytics_content_day`, `analytics_pool_day`, `analytics_staff_day`), none with a player id column, all registered in the migration list. Verify `DatabaseSchemaTest` passes and a schema assertion finds no `user_id` column in any rollup table.
- [x] 4.3 Implement `AnalyticsRollupJob.runOnce(now)`:
  - sweep first
  - per group, list missing or non-final closed days oldest first, capped at 60 group-days per tick
  - recompute each day in its own transaction (delete + insert), with `SET LOCAL statement_timeout`
  - mark final after close + 7 days; state-snapshot columns are captured on first computation and kept on recompute
  Framework tests pass for:
  - catch-up of 3 missed days in one tick
  - no work when everything is final
  - a day recomputed twice equals one computation
  - a result added 3 days after close appears while the day is provisional
  - a result added after finalization changes nothing
- [x] 4.4 Wire the job into `Application.main` as a 15-minute `superviseForever` loop that runs without Telegram configuration. Verify the backend compiles and `ApplicationWiringTest` still starts `module()` without extra env.

## 5. Metric computations (:backend)

- [x] 5.1 Build a reusable analytics fixture with known expected numbers: accounts across providers including one linked to both, results spanning several languages and timezone boundaries, suggestions with auto/editor/legacy decisions and known decision times, catalog words by source with removals and restores, calendar days that are manual, automatic, repeat and legacy, and staff audit entries including a bulk add and an unlinked Telegram decision. Verify the fixture loads in a test database, and its expected values are written next to it and match the spec scenarios.
- [x] 5.2 `lang` and `global` groups: players, games, wins, losses, won_1..won_6, attempts sum, DAU/WAU/MAU. Fixture assertions pass: 4 English players with one also playing Russian gives DAU 4; win rate 80%, distribution 2:1 3:2 4:1, losses 1, average 3.0; the WAU/MAU window edges.
- [x] 5.3 Streak buckets over `[d-30, d]` with the app's live-if-today-or-yesterday rule. Fixture assertions pass, including the spec scenario (2–6, 7–29, not counted), a 30-day run landing in 30+, and a loss breaking a run.
- [x] 5.4 Cohort retention: first day across languages, exact-day D1/D7/D30, null until the target day is closed. Fixture assertions pass for the 10/4/2/1 cohort giving 40%/20%/10%, and D30 null before its target closes.
- [x] 5.5 Word difficulty per calendar: Uzbek combines both scripts; word and marker come from `daily_words`, else the published schedule with no marker. Fixture assertions pass for a manual pick at 20 players / 75%, an Uzbek day at 6 + 4 = 10 players with both spellings, a repeat marker, and a legacy day with no marker.
- [x] 5.6 Accounts `events` metrics: new accounts by Tashkent date, links per day (null before deployment), deletions from `account_events_daily`, and state totals captured once. Fixture assertions pass for 10 total / 5 linked / 4 Google / 2 Apple / 5 anonymous and for the 23:30 Moscow account landing on the Tashkent date.
- [x] 5.7 Suggestion `events` metrics: submitted, auto/editor/rejected by `decided_at`, exact end-of-day backlog, and separate auto and editor medians. Fixture assertions pass for the 6/3/1/1 day, the backlog across d..d+2, and a 2-hour editor median.
- [x] 5.8 Content, pool and staff `events` metrics: added by source, removed and restored from the audit log, `active_words`/`pool_size`/`unused_left` captured once, repeat days (past and upcoming), and staff counts in words with restores counted as added, `telegram-unlinked` grouping and SYSTEM excluded. Fixture assertions pass for 12 staff + 3 auto, 30 pool / 7 unused, and a WORDER at 40 added / 2 edited / 1 removed / 5 decided.
- [x] 5.9 Deletion privacy: delete a fixture account whose results feed a final day and a provisional day, then run the job. Verify the final day's rows are unchanged, the provisional day drops that account, and `game_results` holds no rows for it.

## 6. Admin API (:backend)

- [x] 6.1 Add the analytics DTOs to `:sharedData` (`uz.abumme.harfgame.data.admin.analytics`) and the JSON and CSV paths to `AdminRoutes`, then implement the `admin/analytics` routes on them:
  - overview, accounts, activity (with live today-so-far `partial`), retention, streaks, outcomes, words (with sort), suggestions, content (with current values), staff
  - default last 30 closed days
  - `422 validation_failed` for spans over 366 days or `from > to`
  - `provisional`, `partial`, `languageFiltered` and nulls for unavailable values
  Route tests with the fixture assert the payloads for one range per endpoint and the 400-day refusal. Green.
- [x] 6.2 Implement `/{table}.csv` for every table from the same DTOs: UTF-8 with BOM, header row, markers included, attachment filename. Tests assert the header and exact row count for the Russian word-difficulty export over 30 days, and intact Cyrillic after decoding. Green.
- [x] 6.3 Access control tests pass: an ADMIN session gets 200; a WORDER session gets 403 for every analytics JSON and CSV route; no session gets 401; a player access token gets 401; no response body contains a player id, display name or provider subject (scan of fixture identifiers).
- [x] 6.4 Serialization round-trip tests for the analytics DTOs in `sharedData/src/commonTest` (nulls for unavailable values, `provisional`/`partial` flags preserved). Verify `./gradlew :sharedData:jvmTest` is green and `:adminWeb` still compiles against the DTOs.

## 7. Dashboard (:adminWeb)

- [x] 7.1 Add the Kobweb `@Page` `/analytics` as the ADMIN home, replacing the `staff-auth-roles` placeholder: post-login and root redirects send an ADMIN here, the navigation entry requires `ANALYTICS_READ`, and a WORDER opening the URL gets the forbidden view. `:adminWeb:jsTest` tests of the home-route and navigation mapping assert an ADMIN's home is `/analytics` and a WORDER's permissions produce no analytics entry. Green.
- [x] 7.2 Filter bar: native date inputs defaulting to the last 30 closed days with the 366-day limit and inline message, plus a language `<select>` with "all"; one pure filter-to-query-parameter function used by every section request and CSV link. `:adminWeb:jsTest` covers the default range, over-limit validation, `from > to`, "all" omitting `lang`, and identical parameters for JSON and CSV. Green.
- [x] 7.3 Inline-SVG chart components (line, bar, stacked bar, histogram) with pure geometry separated from the composables; reuse the panel's if `staff-auth-roles` already added them. Each chart has `role="img"` plus an `aria-label`, text axis labels, a `<title>` per point or bar, and a "show as table" toggle. `:adminWeb:jsTest` covers domain and nice-tick computation (including all-zero and single-point series), bar and stack geometry, gaps for null values, and dimming flags for provisional days. Green.
- [x] 7.4 Overview KPI tiles with today-so-far partial badges; Accounts and Activity sections (line and stacked bar charts); Retention table with value-shaded cells and "—" for unavailable values; Streak buckets bar chart; Outcomes (guess distribution bar chart, win rate, average attempts); provisional days dimmed. `:adminWeb:jsTest` covers number, percent and delta formatting ("—" for null) and the retention shade mapping. Manually verify against a local backend loaded with the fixture that every section renders, including null and provisional values. Green.
- [x] 7.5 Word difficulty (sortable through the requested `sort`, both Uzbek spellings, manual/automatic/repeat markers), Suggestions, Content (bundled-source toggle, current values) and Staff activity tables on the panel's data table component. `:adminWeb:jsTest` asserts a sort change maps to the right `sort` parameter. Manually verify the tables against the fixture. Green.
- [x] 7.6 "Export CSV" on every table as a `download` anchor to the base-path-aware CSV path from `AdminRoutes` plus the current filter query. `:adminWeb:jsTest` asserts the generated `href` carries the current filters and the base path. Manually verify a download opens with Cyrillic intact. Green.
- [x] 7.7 Run `./gradlew -PadminWebOnly :adminWeb:jsTest` and the static export (`:adminWeb:kobwebExport` with `-PkobwebExportLayout=STATIC`) the same way CI does. Verify both pass locally and the exported site serves `/analytics` through the backend's `ADMIN_WEB_DIR` fallback.

## 8. Docs (docs)

- [x] 8.1 Add the aggregated-analytics purpose bullet to §3 and the aggregate-retention sentence to §5.2 in `docs/legal/privacy-{en,ru,uz,kk,tr}.md` and bump the revision date; mirror the text into any hosted HTML pages from `add-legal-web-pages`. Verify all five files contain the new bullet and matching dates.

## 9. Verification

- [x] 9.1 Full backend suite green: `./gradlew :backend:test` (CI Postgres service).
- [x] 9.2 `admin-web` CI job green (`:adminWeb:jsTest` and the static export).
- [x] 9.3 `openspec validate analytics-core --strict` passes.
- [ ] 9.4 After deploy, smoke test on the server as an ADMIN:
  - the backfill completes and rollups catch up (no rows missing from `analytics_rollup_days` up to the last closed day)
  - the dashboard shows the last 30 days with recent days provisional and today partial
  - a CSV export opens with Cyrillic intact
  - a WORDER account gets no analytics entry and a 403 on the API
