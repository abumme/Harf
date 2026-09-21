## Context

**Names verified in the applied sibling changes (task 1.1, 2026-09-17).** `staff-auth-roles`, `word-catalog`, `daily-word-calendar` and `player-accounts` are applied in this worktree; the queries below use these real names:
- **Catalog.** `words(id, lang, text, status ACTIVE|REMOVED, source BUNDLED|SUGGESTION|AUTO|STAFF, suggestion_id, daily_eligible, created_by_staff_id, created_at, updated_by_staff_id, updated_at, removed_by_staff_id, removed_at)`; the Kotlin property of `source` is `WordsTable.wordSource`. An edit renames the row in place and inserts a REMOVED tombstone of the old spelling that copies `source` and `created_at`.
- **Audit actions** (`staff_audit_log`, `actor_kind` STAFF|SYSTEM|TELEGRAM): `WORD_ADDED` (details `{text, source}`), `WORD_RESTORED` (`{text}`), `WORD_EDITED` (`{text: {from, to}}`), `WORD_REMOVED` (`{text}`), SYSTEM `WORD_CATALOG_IMPORTED` (`{count, invalid, packVersion}`) and `WORD_BUNDLED_MERGED` (`{count, words ≤ 200}`), and `SUGGESTION_DECIDED` (`{word, status, via}`). There is **no bulk entry**: a bulk add writes one `WORD_ADDED`/`WORD_RESTORED` per word, so staff counts are entry counts. An accepted suggestion writes `WORD_ADDED` (source SUGGESTION or AUTO) or `WORD_RESTORED` under the decision's actor: STAFF for the panel, TELEGRAM (with the linked staff id or none) for Telegram, SYSTEM for automatic acceptance. `decided_by` is `staff:<id>` for panel decisions; `decided_via` AUTO|EDITOR|NULL.
- **Calendar.** `daily_words(calendar en|ru|kk|uz, day date, word_id, lexeme_id, text, text_cyrl, source MANUAL|AUTO|LEGACY, is_repeat, picked_by_staff_id, picked_at)`, `lexeme_pairs`, `calendar_state(calendar, initialized_on)`. Timezones: `uz.abumme.harfgame.data.wordpack.PuzzleDays` (`zoneOf`, `epochDay`; the calendar id `uz` falls back to Asia/Tashkent), backend `CalendarDays.today(calendar, java.time.Instant)`. Pool: `CalendarReconciler.eligible(calendar)` (active eligible words, complete Uzbek pairs); never used: `CalendarUsage.load(...).isUnused(key)` from `CalendarReconciler.historyStart` (`DAILY_HISTORY_START`, else `calendar_state.initialized_on`).
- **Guard.** `requirePermission(Permission...)` under `authenticate(STAFF_SESSION_AUTH)` in `AdminBackend.adminRoutes`; ADMIN gets every `Permission` entry, so the new `ANALYTICS_READ` is ADMIN-only by construction.
- **Deletion.** `AuthServerService.deleteAccount(userId, inTransaction)` is the one path for the player's and the ADMIN's deletion; merge-link keeps its direct `UsersTable.deleteWhere`.

See proposal.md for why. The current state that shapes the approach:

- **Stats storage.** `user_stats` holds one JSON snapshot of `ResultRecordDto{language, puzzleDay, won, attempts}` per account. It is replaced wholesale by a newer upload (last-write-wins). An upload whose timestamp is not newer returns `stored_snapshot_kept` and its records are dropped today. An upload more than 5 minutes in the future is rejected. Anonymous players sync too. Players who never go online are invisible to the server.
- **Deletion.**
  - `AuthServerService.deleteAccount` revokes Apple tokens, then deletes the `users` row. Identities, tokens and `user_stats` go with it via FK cascade.
  - Merge-link removes an orphaned anonymous account with a direct `UsersTable.deleteWhere` inside `linkAccount`, not through `deleteAccount`.
  - `oauth_identities` has no timestamp.
- **Suggestions.** `word_suggestions` carries `created_at`, `decided_at`, `decided_via` (AUTO | EDITOR | NULL for legacy rows), `decided_by` and `status`. `suggested_by` is set NULL on account deletion, so suggestion history survives.
- **Background work.** `superviseForever` loops. `DailyReportScheduler` is the model for an idempotent per-minute tick that settles a day once and catches up after downtime. `DailyReport.kt` holds the Asia/Tashkent date helpers.
- **Streak rule.** `Streaks.streak` (in `:sharedData` since `player-accounts`) is a pure replay: the run of consecutive won days ending at the latest win, live only if that win is today or yesterday. Stats use attempts of wins; `maxAttempts = 6`.
- **Box.** A 1 GB Oracle shape. Postgres runs with `shared_buffers=64MB` and `max_connections=20`, HikariCP pool 10, one backend instance.
- **Assumed from sibling changes:**
  - **`staff-auth-roles`:** ADMIN-only guard, staff session auth, `staff_audit_log` with `lang` and `actor_kind`, the Kobweb `:adminWeb` panel shell (Silk, `AdminApi` client with XSRF handling, session/permission state, placeholder ADMIN home) and the shared admin DTO + `AdminRoutes` convention in `:sharedData` (`uz.abumme.harfgame.data.admin.<area>`).
  - **`word-catalog`:** `words(lang, text, status, source BUNDLED|SUGGESTION|AUTO|STAFF, created_at, removed_at …)`, `WORD_*` and `SUGGESTION_*` audit actions attributed to staff, including Telegram decisions by linked staff; `decided_by = staff:<id>` for panel decisions.
  - **`daily-word-calendar`:** `daily_words(calendar, day, text, text_cyrl, source MANUAL|AUTO|LEGACY, is_repeat)`, `lexeme_pairs`, `daily_eligible`, `DAILY_HISTORY_START`, calendars `en|ru|kk|uz`, and the per-language timezone map (`PuzzleDays` in `:sharedData`).
  - **`player-accounts`:** the ADMIN deletion path.

## Goals / Non-Goals

**Goals:**
- Every metric in the spec is served from precomputed aggregates, so a dashboard request never scans raw per-account data (except the live "today so far").
- One recompute path covers first computation, late syncs, catch-up after downtime and repair. No incremental counters that can drift.
- Aggregates never contain player identifiers, and final days survive account deletion.

**Non-Goals:**
- Revenue: the later `analytics-revenue` change. It needs RevenueCat finished and the app calling `Purchases.logIn(<backend userId>)`.
- Platform, app version, app opens and offline-only players (need client telemetry and a release).
- Real-time streaming or push updates to the dashboard.
- Per-player drill-down (lives in `player-accounts`).
- Rewriting final days after the settle window, or history-correction tooling.
- Changing the stats sync contract or the game client. (`:sharedData` only gains the admin analytics DTOs and routes, which the app never calls.)

## Decisions

### Game results: a normalized table written on upload
`game_results(user_id varchar36 FK users ON DELETE CASCADE, lang varchar16, puzzle_day long, won bool, attempts int, received_at timestamp, PK(user_id, lang, puzzle_day))`, with indexes `(lang, puzzle_day)` and `(puzzle_day)`.

- **When records are written.** `SyncServerService.uploadStats` records the incoming records after the timestamp check passes, for both `Success` and `StoredSnapshotWon` outcomes. A `Rejected` upload records nothing.
- **How.** `batchInsert` with ignore semantics (`INSERT … ON CONFLICT DO NOTHING`). The first recorded result for a key wins, which matches the client's append-only, one-record-per-language-day log. Records are never deleted by a later snapshot.
- **Plausibility filter** (drops individual records, never fails the upload):
  - the language must be one with a word pack (cached list)
  - attempts must be in `1..6`
  - `puzzleDay` must be ≤ the language's current puzzle day in its fixed timezone
- **Failure handling.** Recording runs in its own transaction after reconciliation. A failure is logged and swallowed, so the upload response is unchanged. Records from accepted snapshots that failed to record are recovered by the sweep below. Records from a `stored_snapshot_kept` upload whose recording failed are lost; that loss is accepted.

*Alternatives rejected*:
- **Querying the JSON snapshots on demand.** Parses every snapshot per request, loses records dropped by last-write-wins, and can't keep aggregates past deletion.
- **Recording inside the reconciliation transaction.** An analytics failure would turn a successful upload into a 500.
- **"Latest upload wins" for a key.** Closed days would flap when two devices disagree.

### Backfill and repair are one sweep
`analytics_meta(key varchar64 PK, value text)` stores `results_sweep_watermark`, an `(updated_at, user_id)` cursor.

- **What it does.** Each rollup tick first reads stored snapshots with `updated_at` after the watermark, oldest first, 500 per transaction. It inserts their valid records with the same ignore semantics and advances the watermark.
- **Backfill.** On first deployment the watermark is absent, so the first ticks backfill every existing snapshot in batches.
- **After that.** Only snapshots changed since the last tick are re-read, so the backfill never repeats. A missed upload-time insert of an accepted snapshot is repaired within one tick.
- **Load.** The sweep yields between batches and caps each tick (e.g. 20 batches), so a large backfill spreads over several ticks instead of loading the box at startup.
- **`received_at`** for swept records is the snapshot's `updated_at`.

### Account events
- **Deletions.** `account_events_daily(date date PK, deletions int)`. `AuthServerService.deleteAccount` increments today's row with `INSERT … ON CONFLICT (date) DO UPDATE SET deletions = deletions + 1`. The date is Asia/Tashkent, and the increment runs in the same transaction as the `users` delete, so only a successful delete counts. The ADMIN deletion in `player-accounts` must go through `deleteAccount`, or call the same hook. The merge-link orphan cleanup keeps its direct delete, so it is not counted.
- **Links.** `oauth_identities.linked_at` is a nullable timestamp set when `linkAccount` inserts a new identity. Re-linking an identity the account already owns inserts nothing and is not a new link. Rows from before deployment stay NULL: they count as linked in totals, and links per day for dates before deployment is reported as unavailable (null), not zero.

### Day keys and closure
- **Result metrics use the puzzle day.** Play time is not stored, and the puzzle day is exactly the day the player experienced.
  - A per-language puzzle day `d` closes at the end of `d` in that language's timezone. The timezone comes from the calendar's shared map: uz/en Asia/Tashkent, kk Asia/Almaty, ru Europe/Moscow.
  - Cross-language result metrics (DAU/WAU/MAU, retention) close `d` when it has ended in every language, i.e. at Moscow midnight.
- **Event metrics use the Asia/Tashkent date of the event instant.** These are accounts, links, deletions, suggestions, content and staff activity.
  - Tashkent is the team's clock and the zone of the largest audience (uz, en).
  - It is the clock the Telegram daily report already uses.
  - A single date axis keeps event charts aligned. Per-language event dates would put one suggestion on different days in different charts.
- **Helpers.** `closedPuzzleDays(lang, now)` and `closedEventDates(now)` are pure and tested at 23:59:59 and 00:00:01 in each zone.

### Rollups: recompute whole days from raw data inside a settle window
**Bookkeeping.** `analytics_rollup_days(grp varchar32, day long, computed_at timestamp, final bool, PK(grp, day))`, where `day` is an epoch day. Groups:
- `lang:<lang>` (per puzzle day)
- `global` (cross-language puzzle day)
- `cohort` (cohort day)
- `word:<calendar>` (puzzle day)
- `events` (Tashkent date)

**The tick.** `AnalyticsRollupJob.runOnce(now)` runs every 15 minutes through `superviseForever`, which meets the one-hour freshness requirement. For each group it:
1. Lists closed days from the group's first data day to the latest closed day that are missing or not `final`.
2. For each (capped at 60 group-days per tick: never-computed days first, oldest first; then provisional days whose window has passed; then provisional days last computed at least an hour ago), recomputes the whole day from raw tables in one transaction. It replaces the rollup rows with delete + insert, sets `computed_at`, and sets `final = true` once `now` is past close + 7 days. A day computed for the first time after its window has passed goes straight to final. (Recomputing every provisional day on every tick would exceed the cap — about 12 groups × 7 days plus the 38-day cohort window — and starve the newest days; see Implementation notes.)

**What each group computes:**
- `lang` — `analytics_lang_day(lang, puzzle_day, players, games, wins, losses, won_1..won_6, attempts_sum_won, streak_1, streak_2_6, streak_7_29, streak_30_plus)`. `players` equals `games` because the key is one result per account/language/day; both are kept for clarity. Win rate and average attempts are derived at read time.
- `global` — `analytics_global_day(puzzle_day, dau, wau, mau)`: distinct `user_id` over `d`, `[d-6, d]` and `[d-29, d]`.
- `cohort` — `analytics_cohort_day(cohort_day, size, d1 int null, d7 int null, d30 int null)`.
  - A CTE computes `first_day = min(puzzle_day)` per user.
  - `dN` is the count of cohort members with any result on `cohort_day + N`, filled only once that target day has closed in the `global` sense.
  - The cohort row is final when `cohort_day + 30` is final.
- `word:<calendar>` — `analytics_word_day(calendar, puzzle_day, word, word_cyrl null, source null, is_repeat bool, players, wins, attempts_sum_won)`.
  - Uzbek sums `uz-latn` and `uz-cyrl` results.
  - Word and marker come from `daily_words`. A `LEGACY` entry keeps its stored word (the migration copied it from the schedule the app used) without a marker; with no entry, the word is taken from the published pack schedule with `WordPackSchedule.answerFor` and there is no marker.
- `events` — for Tashkent date `D`:
  - `analytics_accounts_day(date, new_accounts, links_google null, links_apple null, total, linked, google_linked, apple_linked)`. `new_accounts` counts `users.created_at` in `D`; accounts later removed by merge-link drop out while the day is provisional. Links count `linked_at` in `D`, and are null for dates before the column existed.
  - `analytics_suggestions_day(date, lang, submitted, auto_accepted, editor_accepted, rejected, backlog_end, median_auto_seconds null, median_editor_seconds null)`.
    - Decisions are counted by `decided_at`. AUTO counts as automatic; EDITOR, NULL and staff/panel decisions count as editor.
    - Backlog is `created_at ≤ end(D) AND (decided_at IS NULL OR decided_at > end(D))`, which is exact for any past day.
    - Medians use `percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM decided_at - created_at))`.
  - `analytics_content_day(date, lang, active_words null, added_bundled, added_suggestion, added_auto, added_staff, removed, restored)`. Added counts `words.created_at` in `D` by `source`. Removed and restored count the catalog's removal/restore audit entries by `lang` and date.
  - `analytics_pool_day(date, calendar, pool_size null, unused_left null)`.
  - `analytics_staff_day(date, staff_key varchar64, lang, added, edited, removed, decided)`.
    - The source is `staff_audit_log`. Counts are words: the catalog writes one entry per word, a bulk add included, so an entry is a word. Added is `WORD_ADDED` with source STAFF plus `WORD_RESTORED` not caused by an accepted suggestion (restores count as added); a word that enters through a suggestion decision counts only as that decision.
    - Decisions are `SUGGESTION_*` decision entries: an `actor_staff_id` makes the key that staff id; `actor_kind = TELEGRAM` with no staff makes the key `telegram-unlinked`. `SYSTEM` entries (automatic acceptance) are excluded.
    - Staff activity starts with the audit log (the `word-catalog` deployment); earlier dates have no rows.
- **State snapshots vs flows.** End-of-day states (`total/linked/google_linked/apple_linked`, `active_words`, `pool_size`, `unused_left`) can't be reconstructed later from current tables. They are captured on the first computation of a date — provided it happens within a day of the date's end, which in steady state is minutes — and kept as-is on recomputation. Flows (new accounts, links, suggestions, added, removed) are recomputed. State history therefore starts on the first date the job ran; earlier dates hold NULL states, shown as unavailable.

**Streak buckets.** For `lang` and day `d`:
- Take won results in `[d-30, d]`. A current streak reaches bucket 30+ at exactly 30, so nothing older is needed.
- Keep only accounts whose latest win in that window is `d` or `d-1`.
- Compute the run ending there with the gaps-and-islands trick (`puzzle_day - row_number()`), capped at 30.
- Bucket the result.
- The fixture test cross-checks the SQL buckets against the shared `Streaks.streak` from `:sharedData` (moved there by `player-accounts`) on the same records, so the rollup and the app cannot drift.

**Why recompute instead of maintaining counters:**
- Recomputing a whole day from raw rows is idempotent by construction: several recomputations equal one.
- It absorbs late syncs within the window.
- The same code handles catch-up.
- Incremental counters double-count on retries and can't subtract a deleted account.

*Alternatives rejected*:
- **Postgres materialized views.** They refresh wholesale, so a cascade delete would change days that must be final.
- **Computing everything at request time.** It blows the 1 GB box's budget on year-long ranges.
- **No settle window.** Offline players' results would never count.

### Privacy boundary
- **Identifiers.** Rollup tables hold no `user_id`. `game_results` is the only analytics table with account identity, and it cascades with the account.
- **Final days.** Deletion never touches final rollups. While a day is provisional, a deleted account simply isn't in the next recomputation.
- **Staff activity** is keyed by staff id and resolved to username/display name at read time. Staff are never deleted, only disabled.
- **API responses and CSV** carry no player ids, names or provider subjects.

### Admin API
- **Routes.** Under `/api/v1/admin/analytics`, gated by a new `ANALYTICS_READ` permission (ADMIN only) through the `staff-auth-roles` `RequirePermission` guard; a WORDER gets `403 forbidden`, no session gets `401`:

```
GET  /overview      KPI tiles (last closed day values + deltas, today-so-far players/games)
GET  /accounts      ?from&to
GET  /activity      ?from&to&lang       per-language players/games, DAU/WAU/MAU, today partial
GET  /retention     ?from&to            cohort rows with size and d1/d7/d30 (null = not yet available)
GET  /streaks       ?from&to&lang
GET  /outcomes      ?from&to&lang       per day + range totals
GET  /words         ?from&to&calendar&sort=players|winRate|avgAttempts
GET  /suggestions   ?from&to&lang
GET  /content       ?from&to&lang       + current values
GET  /staff         ?from&to&lang
GET  /{table}.csv   same query parameters as the JSON endpoint of that table
```

- **Parameters.**
  - `from`/`to` are ISO dates, inclusive. When absent, the server applies the last 30 closed days.
  - A span over 366 days, or `from > to`, returns `422 validation_failed` before any query runs.
  - `lang` is optional (all languages). Cross-language payloads include `languageFiltered: false` so the UI can label them.
- **Row markers.** Every day row carries `provisional` (not final). Today's partial values carry `partial: true`. Unavailable values are `null`, never `0`.
- **Today so far.** Live `COUNT(*)` on `game_results` for the current puzzle day per language, served by the `(lang, puzzle_day)` index.
- **Current content and pool values** come live from the catalog and calendar services.
- **Query budget.**
  - Read queries touch rollup tables only, at most 366 rows per series, plus the two live counts.
  - Analytics transactions run with `SET LOCAL statement_timeout = '30s'`.
  - The rollup job holds one connection at a time.
- **CSV.**
  - Produced server-side from the same DTOs, so values and markers match the dashboard and the WORDER refusal is enforced once.
  - UTF-8 with a BOM, so Excel opens Cyrillic correctly.
  - Header row. `Content-Disposition: attachment; filename="harf-<table>-<from>-<to>.csv"`. A GET, so no XSRF header is needed.
- **DTOs and routes.** `@Serializable` DTOs live in `:sharedData` under `uz.abumme.harfgame.data.admin.analytics`, and the paths (including the per-table CSV paths) are added to `AdminRoutes`. `:backend` (`backend/.../admin/analytics`: rollup job, queries, routes) and `:adminWeb` compile against the same types, so there is no separate contract document to keep in sync.

*Alternative rejected*: client-side CSV from JSON. Formatting would diverge, and export access control would depend on the UI.

### adminWeb dashboard (Kobweb)
- **Page.** A Kobweb `@Page` at `/analytics` in `:adminWeb`, gated on the `ANALYTICS_READ` permission from the session state. It becomes the ADMIN home: the post-login redirect and the root page send an ADMIN here instead of the `staff-auth-roles` placeholder. The navigation entry is shown only when the permission is present; a WORDER opening the URL directly sees the panel's forbidden view (and the API refuses with 403 regardless).
- **Filter bar.** Two native date inputs (`<input type="date">`) defaulting to the last 30 closed days, a 366-day limit checked before any request with an inline message, and the panel's `<select>` wrapper for language with "all". The filter state maps to query parameters in one pure function shared by every section request and every CSV link.
- **Sections:**
  - Overview KPIs (Silk `SimpleGrid` of tiles; today-so-far values carry a "partial" badge)
  - Accounts
  - Activity
  - Retention
  - Streaks
  - Outcomes
  - Word difficulty
  - Suggestions
  - Content
  - Staff activity
- **Charts.** The panel's own inline-SVG chart components, drawn with the Compose HTML SVG API and styled with Silk `CssStyle`s and color-mode tokens: line (time series), bar (streak buckets, guess distribution), stacked bar (anonymous/linked split per day, words added by source), histogram. No chart JS library. If `staff-auth-roles` did not already add them, this change adds them under the `adminWeb` components, keeping the math (domain, nice ticks, bar geometry, gaps for nulls) in pure functions separate from the composables.
  - Accessibility: every chart has an accessible name (`role="img"` plus an `aria-label` summarizing the series), text axis labels, and a "show as table" toggle that renders the same data through the panel's data table, so no number is available only as a graphic.
  - Provisional days are drawn dimmed (reduced opacity) with a legend note; the partial "today" point is badged; unavailable values leave a gap in charts and show "—" in tables, never 0.
- **Tables.** The panel's reusable data table component (from `staff-auth-roles`) for word difficulty (sortable; a sort change changes the requested `sort`; both Uzbek spellings; manual/automatic/repeat markers), suggestions, content (bundled-source toggle, current values) and staff activity. Retention is a table whose percentage cells are shaded by value through a `CssStyle` background scale, with "—" for values not yet available.
- **Limits accepted.** No heatmaps, zoom or rich hover tooltips: the SVG charts show a plain `<title>` tooltip per point or bar, and retention stays a shaded table. A dedicated chart library would be a separate follow-up if these prove insufficient; not part of this change.
- **CSV.** Each table's "Export CSV" is a plain anchor with `download` whose `href` is the base-path-aware URL of that table's CSV path from `AdminRoutes` plus the current filter query. The browser sends the session cookie on the same-origin GET and the server names the file, so there is no client-side blob handling.
- **Copy.** Russian, from the panel's central strings object, as in the rest of the panel.

### Privacy policy wording
§3 "Purposes of Processing" in `docs/legal/privacy-{en,ru,uz,kk,tr}.md` gains one bullet with the revision date updated:
- **English wording:** "Analyzing game statistics in aggregated form, without identifying individual users, to understand how the Application is used and to improve it."
- **Other languages:** equivalent translations.
- **Retention.** §5.2 notes that aggregated statistics that identify no one may be kept after an account is deleted.
- **Hosted pages.** If `add-legal-web-pages` has already produced HTML, the same text goes there.

## Risks / Trade-offs

- **Timezone-boundary errors in rollups** → Pure closure helpers with boundary tests per zone. A fixture dataset has results on both sides of each midnight. Cross-language days close at the latest zone.
- **Results synced more than 7 days late are never counted in final days** → Documented, and the UI shows provisional days. Raw `game_results` still hold them, so a future recompute tool remains possible.
- **A cohort's first day can move earlier after it was finalized** (a very late sync of an older result) → Accepted. This is rare, the effect is one account, and final cohorts stay stable.
- **The initial backfill and catch-up load a 1 GB box** → Batched sweep (500 snapshots per transaction, capped per tick), rollups capped at 60 group-days per tick, a statement timeout, and everything runs in the background loop rather than at startup.
- **The content chart spikes on the day `word-catalog` imported the bundled dictionaries** (tens of thousands of BUNDLED words) → Source split in the chart, with a toggle to hide the bundled source.
- **Players who never sync are invisible, so numbers undercount** → UI labels player metrics as "synced players". Offline telemetry is out of scope.
- **Parallel changes may name tables or audit actions differently than assumed** → Apply after `word-catalog` and `daily-word-calendar`. The first task verifies the actual names and adjusts the queries before writing tests.
- **Postgres-specific SQL** (`percentile_cont`, `ON CONFLICT`, window functions) → The database is Postgres in production and in tests. Queries are kept in one service.
- **ADMIN deletion added later bypasses the counter** → The proposal and design name the hook, and a test in this change asserts that `deleteAccount` increments it; `player-accounts` must route through it.

## Migration Plan

1. **Deploy the backend image.** The additive schema is applied by the startup migration: `game_results`, `analytics_meta`, `analytics_rollup_days`, rollup tables, `account_events_daily`, nullable `oauth_identities.linked_at`.
2. **Upload-time recording starts immediately.** On its first ticks, the rollup loop sweeps existing snapshots (backfill), then computes rollups from the earliest data day forward, capped per tick until caught up.
3. **The ADMIN dashboard** fills as rollups complete. Days within 7 days show as provisional.
4. **Update the privacy policy texts** in the same release.
5. **Rollback:** the previous image's schema diff sees the unmapped `oauth_identities.linked_at` column and its DROP guard refuses to start, so first run `ALTER TABLE oauth_identities DROP COLUMN linked_at;` (link dates recorded so far are lost), then redeploy the previous image. The new tables are ignored by old code; deletion counts made while rolled back are missed. Dropping the analytics tables is a manual, reviewed step if the feature is abandoned.

## Open Questions

- Whether the content chart hides the bundled-import source by default or only offers the toggle (UI default only, decided when the dashboard is built). Decided: hidden by default, with a switch to show it.

## Implementation notes (apply, 2026-09-17)

Details the design left open or got wrong, decided while implementing. None narrows a spec requirement.

- **Code layout.** `backend/.../admin/analytics/`: `AnalyticsDays` (pure day and closure helpers), `GameResults.kt` (`ResultPlausibility`, the cached `PackLanguages`, `GameResultRecorder`), `ResultsSweep`, `AccountEvents`, `AnalyticsRollupJob` (with `RollupGroup`), `RollupComputations` (the SQL per group), `AnalyticsService` (reads, `AnalyticsQuery.parse`), `AnalyticsCsv`, `AnalyticsRoutes`, and `AnalyticsSql`, a small helper for plain SQL with `:named` parameters bound through Exposed's column types. `AdminBackend` exposes `analytics` and `analyticsRollup`; `main` runs `analyticsRollup.runOnce(Instant.now())` every 15 minutes, outside the Telegram check. `AuthServerService` gained a `clock` (deletion date, link time). `PuzzleDays.allZones` is new in `:sharedData`.
- **Timestamps in SQL.** Exposed writes `timestamp` columns as wall-clock time in the JVM's default zone, so the SQL never converts zones: day boundaries are computed as instants in Kotlin and bound through Exposed's `JavaInstantColumnType`, and durations (`decided_at - created_at`) do not depend on the zone.
- **Plausibility.** As specified: a pack language (the list is cached for 5 minutes), attempts 1..6, puzzle day ≤ today in the language's zone; duplicates of one language and day within a snapshot keep the first. Records are not bounded below, but no rollup covers a day before the schedule anchor (2026-01-01, `AnalyticsDays.HISTORY_START_DAY`), so a bogus early puzzle day cannot make the job backfill decades.
- **Sweep.** The watermark is `"<updated_at ISO instant>|<user id>"` in `analytics_meta.results_sweep_watermark`; 500 snapshots per transaction, at most 20 per run. `user_stats.updated_at` is the client's snapshot time, so a first upload whose client clock lies before the watermark is not re-read; the upload-time recording covers it, and only a failed recording of such a snapshot stays unrepaired.
- **Tick order (design corrected above).** Recomputing every provisional day on every tick needs more than the 60 group-day cap (about 12 groups × 7 days, plus cohorts provisional for 38 days), and oldest-first would never reach the newest days. The job takes never-computed days first (oldest first), then provisional days past their window (their last computation marks them final), then provisional days last computed at least an hour ago, so a newly closed day appears within one tick. Catch-up stays capped as designed: on the local manual check, 60 days of synthetic data (690 group-days) took 12 ticks, about three hours in production after the first deployment, during which the newest days appear last.
- **Groups.** `lang:<pack language>` for every language with a pack, `global`, `cohort`, `word:en|ru|kk|uz`, `events`. A group's days run from its first data day (for `events`: the earliest account, suggestion, word, audit entry or deletion count) to its last closed day, so days without activity get zero rows. Cohort rows are final once `cohort_day + 30` is final.
- **State snapshots.** Captured on a date's first computation only while that happens within a day of the date's end (normally minutes); otherwise they stay NULL rather than recording today's totals as an old date's. `analytics_accounts_day` also stores the day's `deletions`, copied from `account_events_daily`.
- **Links per day.** `analytics_meta.links_recorded_since` is set on the job's first run (seconds after the migration adds `linked_at`); a date starting before it reports null links, so the deployment date itself is null.
- **Word difficulty.** Uzbek players are the results of both scripts (an account that played both counts in each). A LEGACY calendar day keeps its stored text (the migration copied it from the schedule the app used) without a marker, instead of re-reading today's pack schedule, whose anchor may no longer cover old days; a day without a calendar row takes `WordPackSchedule.answerFor` of the published pack; `word` is empty when neither exists.
- **Content added.** Counted from `words.created_at` by source. An edit keeps its row and inserts a REMOVED tombstone that copies `created_at` and source, so each `WORD_EDITED` whose target word was created that date is subtracted again. Removals and restores come from the audit log (any actor).
- **Staff activity (design corrected above).** There is no bulk audit entry: a bulk add writes one entry per word. Added is `WORD_ADDED` with source STAFF plus `WORD_RESTORED` without a `suggestion` fact; decided is `SUGGESTION_DECIDED` by STAFF or TELEGRAM (`telegram-unlinked` without a staff id); SYSTEM is excluded. To tell a staff restore from one caused by accepting a suggestion, `WordCatalogService.restoreRow` now adds `suggestion: <id>` to the latter's `WORD_RESTORED` details (the audit view labels it "по предложению"). A word entering through a decision counts only as the decision.
- **Pool values.** Pool size is `CalendarReconciler.eligible` (active eligible words, complete Uzbek pairs) and never-used is `CalendarUsage.isUnused` over them, as the answer pool shows it; both are null before a calendar's first run. Past repeat days in the content view are read live from `daily_words` (at most 366 rows per calendar, through its primary key), as are upcoming repeats.
- **API details.** `from`/`to` default to the 30 days ending with the last day closed in every zone. Refusals: `422 to: range_too_long` (more than 366 days), `422 to: before_from`, `422 from|to: invalid`, `422 lang: unknown` (not a pack language), `422 calendar: unknown`, `422 sort|dir: invalid`; the range is checked before any database read. Word difficulty takes `calendar`, `sort` (`players|winRate|avgAttempts`; default newest day first) and `dir` (`desc` by default); rows without a rate sort last. Outcome totals add a `lang = null` row for all languages when unfiltered. Cross-language payloads (`AccountsDto`, `RetentionDto`, the `global` part of `ActivityDto`) carry `languageFiltered = false`. Flags are DTO fields without defaults, so the JSON always carries them. Days are ISO dates; these DTOs need no epoch milliseconds.
- **CSV.** `text/csv; charset=UTF-8`, BOM, CRLF, RFC 4180 quoting, rates as fractions with up to four decimals, markers `manual|auto`, booleans `true|false`, unknown values empty. Tables with two kinds of rows share one sheet: activity (`language` is a pack language, `all` for DAU/WAU/MAU, today's rows have `partial = true`), outcomes (day rows, then `total` rows), content (catalog rows by language, then `calendar:<id>` pool rows).
- **Panel.** Pure logic in `analytics/AnalyticsState.kt` (`AnalyticsFilter` with `parameters`/`queryFor` shared by requests and CSV links; `lastClosedDay` via `Intl`, for which `calendar/CalendarGrid.dateInZone` was extracted; `csvHref`; `wordSortParameters`/`nextWordSort`; formatting; `retentionShade`) and `components/charts/ChartMath.kt` (`niceDomain`, `lineSegments`, `lineRuns`, `bars`, `stackedBars`, `labelIndices`). Composables: `components/charts/Charts.kt` (`LineChart`, `BarChart`, `Histogram`, `StackedBarChart`, drawn with Kobweb's SVG DSL, `<title>` through `GenericTag`) and `pages/Analytics.kt`. Tests: `AnalyticsStateTest`, `ChartMathTest`, `NavigationTest`. `homeRedirect` now answers `/analytics` (ANALYTICS_READ), `/words` (WORDS_READ) or `/account`, and sign-in goes straight there (`Routes.afterLogin(next, home)`); `NavSection.DASHBOARD` became `ANALYTICS`. Chart colors are the new `--harf-chart-1..6` tokens. Today's partial values show in the overview and the activity section; the histogram is the guess distribution, the bar chart the streak buckets of the range's last day.
- **Tests.** `AnalyticsFixture` (with `AnalyticsExpected` next to it) is loaded once per class by `AnalyticsMetricsTest` and `AnalyticsRoutesTest`; also `AnalyticsAccountsAndPrivacyTest`, `RollupFrameworkTest`, `ResultsSweepTest`, `AccountEventsTest`, `ResultPlausibilityTest`, `AnalyticsDaysTest`, additions to `StatsSyncIntegrationTest` and `DatabaseSchemaTest`, and `AdminAnalyticsDtoSerializationTest` in `:sharedData`. `resetAnalyticsData()` leaves tiny packs that are not carried into the catalog.
- **Manual check** (2026-09-17) ran against the static export behind a local Caddy `/harf` proxy on a fresh `harf_admin` database with 140 synthetic accounts, 2,609 results over 60 days, 70 suggestions, deletion counts, a WORDER's audit entries and calendar markers. Every section rendered in light and dark mode, with provisional days dimmed and unknown values as "—"; KPI values matched SQL (DAU 68, WAU 129, 73 games, 80.8% win rate); sorting, the language filter (with its "Без фильтра по языку" labels), the chart/table toggle, the 366-day refusal (no request sent) and a CSV download (BOM, 30 Russian rows, Cyrillic intact) worked; at 390 px the page does not scroll sideways. A WORDER had no Analytics entry, got the forbidden view on `/analytics` and `403` from the JSON and CSV endpoints; `/` sent the ADMIN to `/analytics`.
