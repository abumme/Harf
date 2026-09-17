## Why

The backend already holds the data behind the questions an ADMIN needs answered: how many people play, whether they come back, how hard each daily word was, how fast suggestions are handled, how the dictionary grows, and what each WORDER does. None of it is visible. Game results are stored as one overwritten JSON snapshot per account, so every aggregate would mean parsing all snapshots on a 1 GB box. The panel from `staff-auth-roles` gives ADMINs a place to see these numbers, and `word-catalog` and `daily-word-calendar` supply the content data.

## What Changes

- **Game results as rows.** Each stats upload also records every result it carries as one stored game result per account, language and puzzle day. Recording is idempotent, and a later snapshot that lacks a result never removes it. The upload response and the client contract stay the same. Existing synced snapshots are backfilled once.
- **Account events.** Explicit account deletions (by the player, or by an ADMIN through `player-accounts`) are counted per day, with no identity kept. Discarding an orphaned anonymous account during merge-link does not count as a deletion. New Google/Apple links record when they happened. Links made before this change have no link date.
- **Daily rollups.** Closed days are aggregated after each language's midnight by a job that catches up after downtime. Rollups hold aggregates only and are kept when accounts are later deleted. Raw per-account results are still deleted with the account.
- **ADMIN analytics dashboard.** It replaces the placeholder ADMIN home. It shows KPI tiles, charts and tables with language and date-range filters (default: last 30 days), and CSV export for every table. Metrics:
  - **Accounts:** new accounts per day; anonymous vs linked, split by provider; links per day; deletions per day; totals.
  - **Activity:** daily players and games per language; DAU/WAU/MAU across languages; a live "today so far" for players and games, marked partial.
  - **Retention:** D1/D7/D30 by cohort of first synced result day.
  - **Streaks:** distribution of current streaks (1, 2–6, 7–29, 30+) using the app's streak rule.
  - **Outcomes:** win rate, guess distribution (1–6 and losses), average attempts.
  - **Word difficulty:** for each past daily word, players, win rate and average attempts, with its manual/automatic/repeat marker.
  - **Suggestions:** submitted, auto-accepted, editor-accepted, rejected, pending backlog, median time to decision.
  - **Content:** active words, words added/removed per day by source, answer pool size, never-used eligible words left, repeat days.
  - **Staff activity:** words added/edited/removed and suggestions decided, per staff member per day.
- **Access.** Analytics is ADMIN-only. WORDERs are refused and see no navigation entry. Analytics shows aggregates only; per-player detail stays in `player-accounts`.
- **Privacy policy.** Gains a line stating that game statistics are also used in aggregated form to understand usage and improve the game.
- **Not in this change:**
  - Revenue. That is a later `analytics-revenue` change once RevenueCat is finished, and it requires the app to call `Purchases.logIn(<backend userId>)`.
  - Platform, app version, app opens and offline-only players. These need client telemetry and an app release.
  - Real-time streaming.

## Dependencies

- Requires `staff-auth-roles`: ADMIN role, staff sessions, audit log, the Kobweb `:adminWeb` panel, and the shared admin DTO convention in `:sharedData`.
- Word difficulty needs the calendar's history and markers from `daily-word-calendar`. Content and staff-activity metrics need the word catalog from `word-catalog`. Apply this change after both.
- Requires `player-accounts`: ADMIN deletions go through the same `deleteAccount` path (and counter hook), and the backend reuses the shared `Streaks` it moves to `:sharedData`. Apply order: `staff-auth-roles` → `word-catalog` → `daily-word-calendar` → `player-accounts` → `analytics-core`.

## Capabilities

### New Capabilities
- `admin-analytics`: ADMIN-only aggregated analytics over accounts, activity, retention, streaks, outcomes, word difficulty, suggestions, content and staff activity. It covers the captured game results and account events behind them, daily rollups with catch-up, freshness, privacy boundaries and CSV export.

### Modified Capabilities
<!-- none: the stats upload contract (stats-sync) and account deletion behaviour (account-auth) are unchanged for clients; recording results and counting deletions are specified under admin-analytics -->

## Impact

- **Backend** (`:backend`):
  - `SyncServerService` upserts game results on upload.
  - `AuthServerService` counts explicit deletions and records link time.
  - New `admin/analytics` package: rollup job, queries, routes and DTOs. The rollup job is wired as a supervised loop in `Application`.
- **Schema** (additive, applied by the startup migration):
  - New `game_results` table.
  - New aggregate rollup tables and an `analytics_rollup_days` bookkeeping table.
  - New `account_events_daily` table.
  - Nullable `linked_at` on `oauth_identities`.
- **`:sharedData`:** admin analytics DTOs and `AdminRoutes` entries (`uz.abumme.harfgame.data.admin.analytics`), shared by the backend and the panel; not used by the app.
- **`:adminWeb` (Kobweb):** the `/analytics` dashboard page (KPI tiles, filters, inline-SVG charts with table alternatives, tables, CSV links) becomes the ADMIN home.
- **Docs:** `docs/legal/privacy-{en,ru,uz,kk,tr}.md` gain the aggregated-analytics purpose, with a new revision date.
- **No** `:sharedUI` change, no game-client behaviour change, and no app release.
- **Tests:** backend integration tests with a fixture dataset and known expected numbers; `:adminWeb:jsTest` tests of the dashboard's pure logic plus manual UI verification. No screenshot golden impact.
