## Why

The daily word today comes from an 800-day schedule generated once from a small answer list (en 128, ru 49, kk 23, uz 5 pairs), so words cycle every few weeks and nobody can choose a word for a particular day. The team wants to hand-pick words, have every other day filled automatically, and never repeat a word — which needs a server-owned calendar with a curated answer pool, built on the staff panel and the word catalog.

## What Changes

- **Answer pool (ADMIN only).** ADMINs mark catalog words as daily-eligible, one at a time or in bulk, and unmark them. Uzbek eligibility is a lexeme pair — an Uzbek Latin word and its Uzbek Cyrillic counterpart — with the Cyrillic spelling suggested by transliteration and confirmed by the ADMIN. Each calendar shows how many eligible words have never been used. Today's answers and the Uzbek lexeme pairs start eligible.
- **Daily-word calendar (ADMIN only).** One calendar each for `en`, `ru`, `kk` and `uz` (Uzbek publishes the same lexeme in both scripts), in each language's existing timezone.
  - An ADMIN picks the word for any day from the day after tomorrow; unpicking returns the day to automatic.
  - Every other day gets an automatic pick: a random eligible word that has never been used and is not scheduled on another day.
  - Words never repeat. When no unused eligible word remains, the least-recently-used word is reused and the day is marked as a repeat — no notification anywhere. A repeat is replaced as soon as a never-used word becomes eligible.
  - Past days, today and tomorrow are locked: nothing — manual or automatic — changes them.
  - Removing, editing or un-marking a word (including by a WORDER) never changes a locked day; future automatic days re-pick silently, and a future manual pick of a word that is gone is replaced and shown to ADMINs as a notice.
  - Calendar (month grid) and table views; every staff action is audited. WORDERs cannot see or reach any of it.
- **Publishing.** Calendar and pool changes publish instantly through the word-catalog pipeline: pack answers are the eligible words and the schedule is rebuilt from the calendar, reaching at least 60 days ahead and extended daily. The schedule's anchor stays 2026-01-01, so the app's day indexing is unchanged, and the existing schedule is kept as frozen history up to tomorrow.
- **History starts at launch.** A configured launch date (`DAILY_HISTORY_START`) decides which days count as "used"; words played during the test period are free again at launch.
- **App.** A fresh install with no cached pack waits up to about 2 seconds for its first pack sync before resolving today's word, then falls back to the bundle silently. Release builds bundle a snapshot of the published calendar, so an offline fresh install agrees with the server for every day published at build time.
- **BREAKING (pre-launch only):** the generated schedule stops being the source of truth for future days; devices that never sync play the build's snapshot, not the server's later picks.

## Capabilities

### New Capabilities
- `daily-answer-pool`: the ADMIN-curated set of daily-eligible words per calendar — marking, unmarking, Uzbek lexeme pairs, the never-used counter, and publication as pack answers.
- `daily-word-calendar`: the per-language calendar of daily words — manual and automatic picks, no-repeat with least-recently-used reuse, the today-and-tomorrow lock, reaction to catalog changes, calendar and table views, and ADMIN-only access.

### Modified Capabilities
- `daily-puzzle`: the daily word comes from the published calendar (cached pack, else the build's bundled snapshot, else the generated baseline); words do not repeat while unused words remain; an update never changes a past, current or next day; a fresh install waits briefly for its first sync.
- `word-pack-distribution`: a pack update never changes any day up to and including tomorrow in the language's timezone; release builds bundle a snapshot of the published calendar.

## Impact

- **Depends on** `word-catalog` (catalog words, `daily_eligible`, instant publish, shared pack validator) and therefore `staff-auth-roles` (ADMIN sessions, permissions, audit log, the Kobweb `:adminWeb` panel, admin DTOs and `AdminRoutes` in `:sharedData`). Apply after both.
- **`:backend`**: new `admin/answerpool` and `admin/calendar` areas (routes, services, pure calendar engine, periodic tick); new tables `lexeme_pairs`, `daily_words`, `calendar_notices`, `calendar_state` (additive); the word-catalog publish rebuilds answers and schedule from the pool and calendar; startup migration imports the stored schedule as frozen history; new env `DAILY_HISTORY_START`.
- **`:sharedData`**: the Uzbek Latin→Cyrillic transliterator moves here from `tools/wordlists` (also used client-side by the panel); answer-pool and calendar admin DTOs and their `AdminRoutes` paths.
- **`:sharedUI`** (ships with the first public release): bounded first-sync wait in the daily round path; bundled calendar snapshot resources preferred over the generated baseline; a Gradle task refreshes the snapshot from production before a release build.
- **`:adminWeb`** (Kobweb): `/calendar` (month grid) with its `/calendar/table` view and `/answer-pool` pages, ADMIN navigation only.
- **Public API**: `GET /api/v1/wordpacks/{lang}` keeps its shape; answers and schedule content change as the calendar is curated.
- **Docs/CI**: `.env.example` (`DAILY_HISTORY_START`), deploy notes, the release checklist gains the snapshot refresh step; screenshot goldens may change if a test renders a bundled daily word.
