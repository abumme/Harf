## Context

See proposal.md — Why. Current state that shapes the approach:

- `word_packs` stores per language `answers`, `guesses` and `schedule` (JSON). Version 1 was seeded by `WordPackSchedule.build` / `buildOrder` from anchor 2026-01-01 over 800 days; nothing on the server ever changes the schedule afterwards. `effectiveFrom` is written but never read by the app.
- After `word-catalog`: words live in `words` (`status`, `daily_eligible` populated from the current answers), every catalog mutation rebuilds the language's published pack in the same transaction, bumps the version, and refuses a pack the shared validator in `:sharedData` rejects. That change keeps the stored schedule untouched. After `staff-auth-roles`: `Permission`/`RequirePermission`, `principal.requireLanguage`, `AuditLog.record` in the action's transaction, the Kobweb `:adminWeb` panel with a permission-filtered shell and the `AdminApi` client, with admin DTOs and `AdminRoutes` shared from `:sharedData`.
- App: `DailyPuzzleProvider.daily` picks `WordPackSchedule.answerFor(pack.schedule, pack.anchorEpochDay, day)` with `day` in the language's zone (uz/en Asia/Tashkent, kk Asia/Almaty, ru Europe/Moscow); indices past the end wrap modulo. `WordPackRepository.load` uses the cached server pack, else a bundled pack generated from resources. `buildFromDto` adds answers and schedule words to the guess set, so a scheduled word is always a valid guess even after the catalog removes it. `App` starts `WordPackSyncManager.syncAll()` fire-and-forget; `GameScreen` resolves `provider.daily(script)` right away, so a fresh install always plays the bundle on its first launch. A saved in-progress round stores rows and the day, not the answer.
- Uzbek: the server seeded 5 pairs from `uz_lexemes.tsv`; the app's bundled baseline uses 17 `UzbekDailyWords` lexemes, so bundle and server already disagree for Uzbek. `tools/wordlists` has a rule-based Latin→Cyrillic `UzbekCyrillic.transliterate`.
- One backend instance; background work runs through `superviseForever` loops (`DailyReportScheduler` is the idempotent-tick precedent). The app is not public yet.

## Goals / Non-Goals

**Goals:**
- A server-owned calendar per language whose rules (lock, no-repeat, least-recently-used reuse, stability) live in one pure, exhaustively tested function.
- Zero change to the public pack shape and to the app's day indexing; today and tomorrow of every existing device unchanged by the migration.
- Every calendar or pool change reaches clients through the existing instant publish.

**Non-Goals:**
- Hiding the future schedule from the public pack endpoint (contract: simple screens, not secrecy).
- Word difficulty or play statistics on calendar days (`analytics-core`).
- Storing the answer in the app's saved round, or handling device clocks set to the wrong date.
- Suggesting or scoring words for the pool automatically.
- Requiring equal tile counts for the two scripts of an Uzbek pair (the board follows each script's own length).

## Decisions

### Data model (additive)

```
lexeme_pairs     id varchar36 PK, latn_word_id varchar36 FK words UNIQUE, cyrl_word_id varchar36 FK words UNIQUE,
                 created_by_staff_id varchar36 NULL, created_at ts
daily_words      calendar varchar16 (en|ru|kk|uz), day date, word_id varchar36 NULL, lexeme_id varchar36 NULL,
                 text varchar64, text_cyrl varchar64 NULL, source varchar16 MANUAL|AUTO|LEGACY,
                 is_repeat boolean, picked_by_staff_id varchar36 NULL, picked_at ts,  PK(calendar, day)
calendar_notices id varchar36 PK, calendar varchar16, day date, kind varchar32 MANUAL_PICK_REPLACED,
                 word_text varchar64, reason varchar16 REMOVED|INELIGIBLE, created_at ts,
                 dismissed_at ts NULL, dismissed_by_staff_id varchar36 NULL
calendar_state   calendar varchar16 PK, initialized_on date
```

- `words.daily_eligible` (from `word-catalog`) is the pool flag for `en`/`ru`/`kk`. For `uz`, a lexeme pair *is* the eligibility; creating a pair sets `daily_eligible` on both words and removing it clears both, so the pack builder can keep reading the one flag.
- `daily_words.text`/`text_cyrl` are snapshots of what is published for that day; they are rewritten only while the day is unlocked. `word_id`/`lexeme_id` are references for re-evaluation and are NULL for LEGACY days whose word is not in the catalog.
- "Used" is decided by **normalized text** (for `uz`, the Latin text), not by id, so an edit that turns one word into another word's former spelling still counts as used.
- `calendar_state.initialized_on` records the first run; it is the history start when `DAILY_HISTORY_START` is unset (so a forgotten env var never makes the whole legacy cycle "used").
- Rollback-safe: the previous (`word-catalog`) image ignores these tables and keeps publishing the schedule stored in `word_packs`.

*Alternatives rejected*: a `daily_eligible` column on `lexeme_pairs` only (the pack builder would need two code paths); storing only ids in `daily_words` (a later edit would rewrite a day players already played).

### Calendar engine is a pure function

```
plan(
  calendar, today,                         // today in the calendar's zone
  horizonEnd = today + 60,
  historyStart,                            // DAILY_HISTORY_START ?: initialized_on
  frozen: Map<day, text>,                  // all days ≤ today + 1 (LEGACY/AUTO/MANUAL as stored)
  manual: Map<day, candidate>,             // days ≥ today + 2, any distance ≤ today + 365
  previousAuto: Map<day, text + isRepeat>, // days in [today + 2, horizonEnd]
  eligible: Map<textKey, candidate>,       // active + eligible (uz: complete pairs)
  rng,
) → Plan(days: Map<day, Assignment(text, source, isRepeat, lastUsed?)>, replacedManual: List<Notice>)
```

1. Drop manual picks whose candidate is no longer in `eligible` → notice (`REMOVED` if the word is not active, else `INELIGIBLE`); those days become automatic.
2. `used` = texts of frozen days with `day ≥ historyStart`; `taken` = `used` ∪ remaining manual texts (all distances, so a pick 200 days out is never auto-scheduled earlier).
3. `lastUse[text]` = latest frozen day with `day ≥ historyStart`.
4. For each day `d` from `today + 2` to `horizonEnd`, ascending:
   - manual → keep; `lastUse[text] = d`.
   - previous automatic still valid (in `eligible`, not in `taken`, and either not a repeat or no never-used word is left) → keep.
   - otherwise, if `eligible − taken` is non-empty → uniform random choice, `isRepeat = false`;
   - otherwise → the eligible text with the smallest `lastUse` (never-scheduled ranks oldest; ties by `rng`), `isRepeat = true`, keeping `lastUse` for the marker.
   - add the text to `taken`, set `lastUse[text] = d`.
5. A frozen day missing from `frozen` (only after more than 60 days of downtime) is filled by the same rule once and then stays frozen.

Manual picks are resolved by word/pair id (so a corrected spelling follows the pick), automatic picks by text (so any edit makes the day re-pick). "A repeat is replaced when a never-used word appears" and "taking an automatic word re-picks its day" both fall out of step 4, since the earliest invalid day re-picks first. The function has no clock, database or randomness of its own (`rng` is injected; production uses `SecureRandom`, tests a seeded `Random`), so every spec scenario is a unit test. Cost is O(horizon × pool) — trivial.

*Alternatives rejected*: extending `WordPackSchedule.build` (shuffle cycles cannot express manual picks, locks or least-recently-used reuse); deterministic seeded auto-picks shared with the app (the server now owns the calendar, so the app never recomputes it); SQL-only selection (hard to test the rule set).

### Reconciliation: one entry point, three triggers

`CalendarService.reconcile(calendars, now, cause)` loads the inputs, runs `plan`, writes the differences to `daily_words`, stores notices, and republishes the affected packs — all in the caller's transaction:

- **Staff actions**: pick, unpick, mark or unmark eligibility, create or remove a pair → reconcile that calendar.
- **Catalog mutations** (`word-catalog` add, edit, remove, restore, bulk): the catalog publish step calls `reconcile(calendarOf(lang))` before it rebuilds the pack, inside the same transaction. The WORDER's response is unchanged; notices and system audit entries are written with `actor_kind = SYSTEM`.
- **Tick**: `DailyCalendarScheduler.tick(now)` every 60 s via `superviseForever`, started always (not tied to Telegram), and once at startup after migration. It reconciles a calendar only when its horizon is short (`max(day) < today + 60`) or its `today` changed since the last tick, so a quiet minute touches nothing.

Concurrency: reconcile locks the `word_packs` rows it will publish with `SELECT … FOR UPDATE` in a fixed order (`en`, `kk`, `ru`, `uz-cyrl`, `uz-latn`) — the same lock the catalog publish takes — so a tick and a staff action cannot interleave. `today` is computed once per transaction from one `Clock`, per calendar zone; the lock check in pick/unpick uses that same value, which makes the 23:59/00:01 behavior deterministic in tests.

### Publishing: answers from the pool, schedule from the calendar

The `word-catalog` pack builder changes its answers and schedule inputs:

- `answers` = active words with `daily_eligible` (for `uz-latn`/`uz-cyrl`, the pair's word in that script), in stable text order.
- `schedule` = `daily_words` texts from `ANCHOR_EPOCH_DAY` to the calendar's last day, contiguous (`text_cyrl` for `uz-cyrl`); `anchorEpochDay` stays `WordPackSchedule.ANCHOR_EPOCH_DAY`, so `answerFor` in the app is untouched.
- `effectiveFrom` = the day after tomorrow in the language's zone on every publish.
- The pack is validated by the shared validator before it is written (unchanged from `word-catalog`). A legacy schedule word no longer in the catalog still passes, because the validator, like the app, treats schedule words as guesses.
- The version advances only when answers, guesses or schedule actually differ from the stored row, so a no-op reconcile publishes nothing. In steady state the tick extends each horizon once a day, which is one version bump per language per day.

Past the horizon (a device offline for more than 60 days) the app wraps modulo the schedule length; this is an accepted edge.

### Migration on first start

Runs at startup after `word-catalog`'s own migration, once per calendar without a `calendar_state` row, in one transaction per calendar:

1. `uz`: create a pair for each `uz_lexemes.tsv` line whose two words exist as active catalog words (they do: seeded answers are guesses), and set `daily_eligible` on both.
2. Import the stored `word_packs.schedule` for every day from the anchor through tomorrow (in the calendar's zone) as `LEGACY` rows, resolving `word_id`/`lexeme_id` by text where possible (`uz` pairs `uz-latn[i]` with `uz-cyrl[i]`).
3. Record `calendar_state.initialized_on = today`.
4. Reconcile from the day after tomorrow and publish.

Today and tomorrow therefore keep exactly the words every device already has; tests compare the migrated pack with the pre-migration pack for those two days.

### Answer-pool and calendar admin API

Under `/api/v1/admin`, new permissions `DAILY_POOL_MANAGE` and `CALENDAR_MANAGE` (ADMIN only; WORDER → `403 forbidden`); request/response DTOs live in `:sharedData` under `uz.abumme.harfgame.data.admin.answerpool` and `uz.abumme.harfgame.data.admin.calendar`, with the paths added to `AdminRoutes`; backend code stays in `backend/.../admin/{answerpool,calendar}`.

| Method & path | Result |
|---|---|
| `GET /answer-pool/{calendar}?q&page&size` | `200 {unusedLeft, page: Page<PoolWordDto{wordId|pairId, text, textCyrl?, lastUsed?, scheduledOn?}>}` |
| `GET /answer-pool/{calendar}/candidates?q&page&size` | active catalog words of supported length not yet eligible |
| `POST /answer-pool/{calendar}/words` `{wordIds?: [..], texts?: [..]}` | `200 [ItemResultDto{input, outcome: MARKED|ALREADY_ELIGIBLE|NOT_IN_CATALOG|REMOVED|UNSUPPORTED_LENGTH}]` |
| `DELETE /answer-pool/{calendar}/words/{wordId}` | `204` |
| `GET /answer-pool/uz/cyrl-status?cyrl=` | `200 {cyrlWordId?, cyrlStatus: ACTIVE|REMOVED|MISSING}` (the suggested spelling itself is computed in the panel) |
| `POST /answer-pool/uz/pairs` `{latnWordId, cyrlText, addCyrlToCatalog}` | `201 PairDto`; `409 conflict` naming the existing pair; `422 validation_failed` from catalog validation |
| `DELETE /answer-pool/uz/pairs/{pairId}` | `204` |
| `GET /calendar/{calendar}?from&to&source&repeatsOnly` | `200 [DayDto{day, text, textCyrl?, source, isRepeat, lastUsed?, locked, pickedBy?, pickedAt}]` |
| `GET /calendar/{calendar}/candidates?day&q&page&size` | eligible words with `neverUsed`, `lastUsed?`, `scheduledOn?: {day, source}` |
| `PUT /calendar/{calendar}/days/{day}` `{wordId?|pairId?}` | `200 DayDto`; `422 validation_failed` (`day: locked`, `day: too_far`, `word: not_eligible`); `409 conflict` with `usedOn: [days]` |
| `DELETE /calendar/{calendar}/days/{day}` | `200 DayDto` (now automatic); `422` when locked or not manual |
| `GET /calendar/notices?includeDismissed` / `POST /calendar/notices/{id}/dismiss` | notices list / `204` |

Audit actions: `DAILY_ELIGIBILITY_MARKED`, `DAILY_ELIGIBILITY_UNMARKED`, `DAILY_PAIR_CREATED`, `DAILY_PAIR_REMOVED`, `DAILY_WORD_PICKED`, `DAILY_WORD_UNPICKED`, `DAILY_NOTICE_DISMISSED`, and SYSTEM `DAILY_MANUAL_PICK_REPLACED`. Automatic picks are not audited one by one (they are visible as automatic days). The `staff-auth-roles` audit query already restricts WORDERs to their own entries; these actions are never theirs.

The DTOs and `AdminRoutes` are shared Kotlin types, so the backend routes and the panel compile against the same contract; there is nothing to regenerate.

### Puzzle-day timezones move to `:sharedData`

The per-language timezone map (`en`/`uz-latn`/`uz-cyrl` Asia/Tashkent, `kk` Asia/Almaty, `ru` Europe/Moscow) moves out of `DailyPuzzleProvider` into `:sharedData` as `uz.abumme.harfgame.data.wordpack.PuzzleDays` (`zoneOf(languageId)`, `epochDay(languageId, instant)`); `DailyPuzzleProvider.epochDay` delegates to it. The backend calendar engine's zone-aware "today" uses the same object, so the app and the calendar can never disagree on where a day starts. `player-accounts` and `analytics-core` reuse it.

### Transliterator moves to `:sharedData`

`UzbekCyrillic.transliterate` (Latin → Cyrillic, rule-based) moves to `:sharedData` as `uz.abumme.harfgame.lang.UzbekTransliteration` with its tests; `tools/wordlists` keeps its guess-generation wrapper and calls the shared function. `:sharedData` targets JS, so the Kobweb panel computes the suggested Cyrillic spelling client-side from the same function, with no round trip. It is only a suggestion: the ADMIN confirms or corrects every spelling, which covers loanwords (ц, ь) the rules get wrong; the backend re-validates the confirmed spelling through catalog validation.

### App: bounded first-sync wait

- `WordPackSyncManager` records, per language, a `CompletableDeferred<Unit>` for the first sync attempt of the process, completed in `finally` (success, 304, error or offline).
- `DailyPuzzleProvider.daily` calls `awaitFirstSync(lang, 2.seconds)` only when `WordPackCache.get(lang)` is null, then loads from `WordPackRepository` (which already invalidates after caching). `withTimeoutOrNull` bounds the wait; nothing is shown or thrown.
- Only the daily-word path waits; guess validation and other screens are unaffected.

*Alternatives rejected*: blocking app start on sync (hurts every launch, offline included); a loading screen with retry (surfaces the network to a game that promises offline play).

### App: bundled calendar snapshot

- Resource `composeResources/files/<lang>_calendar.json`: `{lang, version, anchorEpochDay, answers, schedule}` for `en`, `ru`, `kk`, `uz-latn`, `uz-cyrl` — no guesses, because the bundled `<lang>_guess.txt` already ships the dictionary and a full pack copy would add about 600 KB of duplicated words to every build.
- `WordPackRepository.buildBundled` builds a `WordPackDto` from the snapshot plus the bundled guesses and runs the shared validator; valid → use it, otherwise fall back to today's generated baseline. The cached server pack still wins first. `validate(id)` also checks the snapshot so an invalid committed snapshot fails the test suite.
- Gradle task `:sharedUI:refreshCalendarSnapshot` (base URL from the existing `harf.apiBaseUrl` property, production by default) fetches each `GET /api/v1/wordpacks/{lang}` and writes the files. The snapshots are **committed**, so debug builds, tests and screenshot goldens stay reproducible; refreshing them is a release-checklist step (and a manual `workflow_dispatch` CI job that opens a commit) before building a store release.
- Until the first refresh, the committed snapshot is generated from the current production packs at apply time, which also removes the existing Uzbek bundle/server disagreement.

*Alternatives rejected*: fetching at build time in CI without committing (non-reproducible builds, network-dependent tests); bundling the full pack JSON (duplicate dictionary).

### `:adminWeb` pages (Kobweb, ADMIN navigation only)

Built on the `staff-auth-roles` panel foundation: Kobweb `@Page`s, Silk widgets, the shared `AdminApi` client and the session state that holds the caller's permissions. Silk has no calendar or data table, so the pages use the panel's own components built from Compose HTML elements styled with Silk `CssStyle` (the server-paged table from `staff-auth-roles`, plus a new month-grid component here).

- **`/calendar`**: calendar selector (`en`, `ru`, `kk`, `uz`) and month navigation. A `CalendarMonthGrid` component renders one cell per day: the word (both scripts for `uz`), manual/automatic marker, repeat badge with the last-use date, lock icon for past, today and tomorrow in that calendar's timezone (computed with the shared `PuzzleDays`, confirmed by the server's `locked` flag), picker name on manual days. Clicking an unlocked day opens a picker dialog (Silk `Overlay`): candidate search showing never-used, last-used and scheduled-on information, pick, unpick; a `409` shows the days the word is used on. A notices list above the grid shows undismissed `MANUAL_PICK_REPLACED` notices with dismiss.
- **`/calendar/table`** (linked as a tab from `/calendar`): date range, source filter, repeats-only toggle, server-paged table.
- **`/answer-pool`**: per-calendar eligible list with the never-used counter, candidate search and mark, bulk paste with per-line outcomes, unmark. The `uz` tab lists pairs and a "new pair" flow: choose a Latin word → suggested Cyrillic computed client-side with the shared `UzbekTransliteration` (editable, checked with the shared tokenizer for `uz-cyrl`) → catalog status of that spelling from `cyrl-status` → the ADMIN confirms (optionally adding it to the catalog).
- Pages render only for a session with `CALENDAR_MANAGE` / `DAILY_POOL_MANAGE`; navigation entries are added only for such sessions, and a WORDER opening the URL directly gets the forbidden view. Strings go into the panel's Russian strings object.
- Pure logic sits outside composables so `:adminWeb:jsTest` covers it: month-grid math (week rows, leading/trailing days, lock markers per calendar timezone around midnight), calendar table filter state → query parameters, pair form validation. UI flows are verified manually against a local backend.

### Configuration

`DAILY_HISTORY_START` (ISO date, optional): set to the public launch date. Days before it never count as used. Unset → `calendar_state.initialized_on`. Documented in `.env.example` and `docs/deploy-oracle.md`.

## Risks / Trade-offs

- [Pools are tiny today (uz 5 pairs, kk 23, ru 49, en 128), so the 60-day horizon starts with many repeats] → by design: repeats are marked, the counter shows the runway, and every newly eligible word replaces a future repeat automatically; growing the pool is the ADMIN's lever.
- [Midnight edges: a pick accepted at 23:59:59 for the day after tomorrow publishes that day while it still has a full day of reach; the same request a second later is refused] → one clock per transaction, zone-specific `today`, unit tests at 23:59:59 and 00:00:01 for Moscow, Almaty and Tashkent.
- [Stale bundled snapshot: an offline fresh install of an old build plays that build's words for days picked after the build] → snapshot refreshed on every release; online installs wait for the first sync; accepted for installs that never go online.
- [A device that started today's round offline on a stale snapshot, then syncs a different word for today, restores rows scored against the old word] → rare (fresh install, offline, stale build, same day); the saved round has no answer to compare against; accepted and noted rather than widening this change into round persistence.
- [Daily horizon extension bumps each pack version once a day, making every client re-download its pack daily] → packs are gzip-compressed by `word-catalog` (~60 KB for `en`); acceptable for a daily game.
- [Reconciling inside WORDER catalog saves adds latency to their edits] → the engine is in-memory and linear; the eligible-set query is indexed by `(lang, status, daily_eligible)`.
- [An edit turns a scheduled word into an unsuitable spelling] → catalog validation still applies; automatic days re-pick on any edit; a manual pick follows the edit by design, and the ADMIN sees the new text in the calendar.
- [Transliteration mistakes] → suggestion only; the ADMIN confirms every Cyrillic spelling.
- [Rollback] → additive tables only; the `word-catalog` image keeps serving the last published schedule. Days that image publishes after the rollback stay as they were, since it never edits the schedule.

## Migration Plan

1. Apply after `word-catalog`. Set `DAILY_HISTORY_START` to the planned launch date in the server `.env` (or leave it unset during testing).
2. Deploy through CI. At startup the calendar migration imports history through tomorrow, creates the Uzbek pairs, reconciles and publishes; logs one line per calendar with the number of legacy days, eligible words and repeats scheduled.
3. Verify: `GET /api/v1/wordpacks/{lang}` returns the same words for today and tomorrow as before the deploy; the Calendar page shows 60 days ahead per calendar; a WORDER gets `403` on `/api/v1/admin/calendar/en`.
4. Before the first store release: run `:sharedUI:refreshCalendarSnapshot`, commit the snapshots, rebuild goldens from CI if any screenshot shows a bundled daily word.
5. Rollback: redeploy the previous image; nothing to drop.

## Open Questions

- Horizon length (60 days) and the farthest manual pick (365 days) are constants; either can change later without touching the specs' minimum or the approach.
- Tick interval (60 s) and whether the snapshot-refresh CI job commits directly or opens a pull request — decided during implementation. Resolved: 60 s; the job commits to a new branch and opens a pull request.

## Implementation notes (apply, 2026-09-17)

Details the design left open or got wrong, decided while implementing. None narrows a spec requirement.

- **Engine refinements** (`admin/calendar/CalendarEngine`). Step 4's "previous automatic still valid … not in `taken`" would re-pick every repeat on every run (a repeat's word is always taken), breaking "an automatic pick stays stable". A previous pick is kept as a non-repeat when its word is eligible and not taken, and as a repeat when no never-used word is left, the word is not manually picked, and it differs from the day before. A re-pick avoids words that later days still hold (so one change re-picks only the days it affects), and a least-recently-used choice skips manually picked words, the previous day's word and the next day's held word while other candidates exist; consecutive days differ while the pool has more than one word. An empty pool schedules nothing. Kept manual picks beyond the horizon are part of the plan (with their current text), so a far pick that loses its word is removed with a notice. The engine takes `java.time.LocalDate` days and normalized texts; `ReplacedPick` carries the notice.
- **Where the rules run.** `CalendarReconciler` (constructed by `WordCatalogService`, which owns the clock, audit writer and `PackPublisher`) loads a calendar, runs the engine and writes differences; `CalendarService` (routes, tick `refresh`) and `AnswerPoolService` use it through `CalendarTransactions` (pack locks, one `now`, 404 for an unknown or not yet initialized calendar, `PackIntegrityException` → `422 pack: pack_integrity`). `CalendarUsage` answers "used on / last used / scheduled on / never used" for the views and the counter.
- **Uninitialized calendars.** Until a calendar's first run (`calendar_state`), `reconcile` does nothing and `PackPublisher` keeps the stored schedule, anchor and effective date, so the `word-catalog` behaviour (and its tests, which never initialize calendars) is unchanged; production always migrates at startup before the server binds.
- **Publisher.** `PackPublisher.lock(lang)` locks every pack row of the language's calendar in sorted order (`uz-cyrl`, then `uz-latn`), so a catalog writer and a calendar writer never deadlock. `publish(pack, now)` stores anchor, effective date and schedule too, and advances the version only when answers, guesses or schedule differ from the stored row. Uzbek answers are the words of pairs whose two words are both active. The schedule is the contiguous run of `daily_words` from the anchor. A catalog change of an initialized calendar reconciles it and publishes every pack of the calendar (`WordCatalogService.publishChange`), because an Uzbek change can alter the other script's answers and schedule.
- **Uzbek rows and pairs.** `daily_words.word_id` is null for `uz` and `lexeme_id` holds the pair id (one reference kind per calendar). The migration also clears `daily_eligible` on Uzbek words outside any pair, so the flag equals pair membership. A pair whose word was removed stays listed (inactive) and returns when the word is restored. `lexeme_pairs` references `words` with `ON DELETE CASCADE` (words are never deleted in production; it keeps test resets simple); staff columns are plain, like `words.updated_by_staff_id`.
- **Migration.** LEGACY rows store the stored schedule's raw text (resolved with `WordPackSchedule.answerFor`, as the app does), so today and tomorrow publish byte-identical words; ids are resolved by normalized text in either status. One READ COMMITTED transaction per calendar; the log line is `Daily calendar <id>: imported N legacy days, M eligible words (K Uzbek pairs), R repeats scheduled`. Tested on the deployed resources (`ApplicationWiringTest`) and on seeded fixtures (`CalendarMigrationTest`).
- **Tick.** `DailyCalendarScheduler` keeps each calendar's last `today` in memory; `CalendarService.refresh` reconciles when that changed or the horizon is short (fewer than 61 stored days in today..today + 60). The first tick after a start reconciles every calendar, which publishes nothing when nothing changed. Started in `main` next to the session cleanup loop.
- **API details** (`AdminRoutes`, DTOs in `data.admin.calendar` / `data.admin.answerpool`):
  - `GET /calendar/{calendar}` answers a `PageDto<DayDto>` (`page`, `size` ≤ 200; without dates today..today + 60), so the table view is server-paged; the month view asks for the grid's days in one page. `DayDto` also carries `wordId`/`pairId`; days are ISO dates, times epoch ms.
  - Conflicts name days in the `field: reason` message: `word: used 2026-09-14,2026-09-15`, `word: picked 2026-10-01` (`CalendarReasons.conflict/parseConflict`); pair conflicts name the pair: `latnWordId: paired kitob/китоб` or `cyrlText: paired …`. A pick without the right id is `422 word: required`; unpicking an automatic day `422 day: not_manual`; a malformed day `422 day: invalid`.
  - Marking on `uz` answers `422 calendar: pairs_only`; more than 1,000 entries `422 items: too_many_items`. `MarkWordsRequest` has `wordIds` and `texts` (default empty); results keep request order (ids first). Unmarking a word that is not eligible is a `204` no-op; a missing word `404`.
  - `POST /answer-pool/uz/pairs` validates the Latin word (`latnWordId: not_active | bad_length`), then the Cyrillic spelling through the catalog validator (`cyrlText: <WordReasons>`), then `cyrlText: not_in_catalog | removed` unless `addCyrlToCatalog` (which adds as STAFF or restores, audited as `WORD_ADDED`/`WORD_RESTORED`). `CyrlStatusDto` also carries the normalized spelling and `pairedWith`.
  - The pool list includes removed eligible words and incomplete pairs with `active = false`; `unusedLeft` counts active entries only. `GET /calendar/notices` returns at most 200, newest first. Dismissing twice is a `204` no-op.
- **Audit.** Targets `DAILY_DAY` (`<calendar>:<day>`), `LEXEME_PAIR` (labelled `latin / cyrillic` while the pair exists) and `CALENDAR_NOTICE`; `lang` is the calendar id (`uz` for Uzbek). The audit query hides `DAILY_*` actions from members without `CALENDAR_MANAGE` or `DAILY_POOL_MANAGE`, and the panel's action filter omits them (`auditActionsFor`). Picks and unpicks record day, word and the previous word/source.
- **Configuration.** A malformed `DAILY_HISTORY_START` stops startup (`DailySettings.fromEnv`). Development CORS allows `PUT`.
- **`PuzzleDays`** lives at `uz.abumme.harfgame.data.wordpack.PuzzleDays` (`zoneOf(languageId)`, `epochDay(languageId, kotlin.time.Instant)`; unknown ids, including the calendar id `uz`, use Asia/Tashkent). The backend's `CalendarDays.today(calendar, java.time.Instant)` calls it. On Kotlin/JS, kotlinx-datetime needs the js-joda time zone database to resolve zones, so the panel's `todayIn(calendar, epochMillis)` takes the zone from `PuzzleDays.zoneOf` and the date from the browser's `Intl.DateTimeFormat` (covered by the midnight tests in headless Chrome); the server's `locked` flag stays authoritative.
- **App.** `FirstPackSync` (implemented by `WordPackSyncManager`) is an optional `DailyPuzzleProvider` parameter, bound with `getOrNull` so `languageModule` still loads alone. `CalendarSnapshotDto` (in `:sharedData`) is the snapshot format; `WordPackRepository` takes a `snapshots` source (default: the Compose resource, a missing file or any read failure means none). No snapshot is committed by this change: the real ones come from production at release time (task 5.3), and snapshots of local test data must not ship in the app. The Roborazzi goldens are unaffected: no screenshot test renders a daily word (`verifyRoborazziJvm` fails locally only on the known font/locale differences and `SemanticsDumpTest`).
- **Snapshot task.** The fetch-validate-write logic is `tools/wordlists` `CalendarSnapshotRefresh` (unit tested), run by `:sharedUI:refreshCalendarSnapshot` as a `JavaExec` over a resolvable configuration of `:tools:wordlists`; it also refuses Uzbek schedules of different lengths and writes nothing unless all five packs pass. `-PcalendarSnapshotDir=<dir>` writes elsewhere (used to try it against a local backend). The CI job is a `workflow_dispatch` input `refresh_calendar_snapshot` of `ci.yml`: with it only that job runs (the other jobs are gated), and it needs the repository setting that lets Actions open pull requests.
- **Panel.** `/calendar` (`pages/Calendar.kt`, picker `pages/calendar/PickDayDialog.kt`), `/calendar/table` (`pages/calendar/Table.kt`) and `/answer-pool` (`pages/AnswerPool.kt`, dialogs in `pages/answerpool/PoolDialogs.kt`); pure state in `calendar/CalendarGrid.kt` (Monday-first `monthGrid`, `lockOf`, `todayIn`), `calendar/CalendarState.kt` (`CalendarViewState`, `CalendarTableState`, messages) and `answerpool/PoolState.kt` (`PoolQueryState`, `PairForm`, `groupMarkResults`), covered by `CalendarStateTest`. The backend serves `calendar.html` and `calendar/table.html` from the export correctly (`AdminWebServingTest`).
