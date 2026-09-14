## Context

See proposal.md — Why. Current state that shapes the approach:

- `POST /suggestions` validates, stores PENDING, then calls `TelegramBot.notifyPending` inline. The send result is swallowed by `runCatching`, so a failed send loses the notification permanently.
- `TelegramBot.runPolling` long-polls `getUpdates`. On every callback it calls `editMessageText` without `reply_markup`, which strips the inline keyboard — including after an unauthorized or malformed tap. A non-`ok` HTTP response (401 bad token, 409 second poller) is not an exception, so the loop re-polls with no delay.
- `SuggestionServerService.decide` reads the status and updates it in separate transactions.
- `WordPackServerService.addGuess` appends to `guesses` and bumps `version`; clients replace their cached pack wholesale (`WordPackCache.put`), so the server list is authoritative.
- Schema is applied at startup by the `MigrationUtils` diff (additive only; DROPs refused). No column in the schema uses a database default today.
- One backend instance (Oracle box); no cross-instance coordination is needed.
- Packs: `en`, `ru`, `kk`, `uz-latn`, `uz-cyrl`. Uzbek Latin stores the tutuq as U+02BB; the client folds Russian ё→е.

## Goals / Non-Goals

**Goals:**
- Accept verifiable real words without editor action, never auto-reject.
- Never lose a suggestion notification to a transient Telegram or dictionary failure.
- One daily, per-topic summary of outcomes.

**Non-Goals:**
- Undoing an auto-accept from Telegram (chosen: informational notice only; a wrong word is removed by hand).
- Verifying with other dictionaries or an LLM.
- Catching up reports older than the previous day.
- Notifying players of outcomes; any `:sharedData`/`:sharedUI` change.
- Multi-instance locking.

## Decisions

### Dictionary source: English Wiktionary categories API
`GET https://en.wiktionary.org/w/api.php?action=query&prop=categories&cllimit=max&format=json&formatversion=2&titles=<word>` with a descriptive User-Agent (Wikimedia policy), a 5 s timeout, and no redirect following — the page title must equal the word the player typed.

Probed 2026-09-14: English Wiktionary covers all five languages (`қалам`/`орман` Kazakh, `китоб`/`дарё` Uzbek Cyrillic, `oʻrdak` with U+02BB). The native Wiktionaries miss `орман` (kk) and all Uzbek Cyrillic tested.

*Alternatives rejected*: native-language Wiktionaries (coverage gaps above); dictionaryapi.dev (English only); Yandex Dictionary (API key, no Uzbek); an LLM (cost, nondeterminism, confident false "yes"); following redirects (would accept a spelling the player did not type).

### Classification is a pure function over the target language's categories
Language → category prefix: `en` English, `ru` Russian, `kk` Kazakh, `uz-latn`/`uz-cyrl` Uzbek; any other language → REVIEW. Only categories starting with `<Lang> ` are considered, matched by the text after the prefix:

1. Page missing, or no `<Lang> ` categories → **REVIEW(not found)**. A page alone is not enough: `книга` also has Bulgarian and Chechen sections.
2. Any blocking category — proper nouns, abbreviations, acronyms, initialisms, misspellings, vulgarities, swear words, derogatory terms, offensive terms, slurs — → **REVIEW(flagged, kind)**. Blocking beats lemma: `Павел` is `Russian lemmas` + `Russian proper nouns`; `NATO` has `acronyms`; `teh` has `deliberate misspellings`; `shit` has `vulgarities`.
3. `lemmas` or `variant lemmas` → **AUTO(dictionary form)**. `variant lemmas` is required: Uzbek Cyrillic entries (`китоб`, `дарё`) carry only that.
4. `non-lemma forms` → **AUTO(inflected form)** (`книги`, `walks`).
5. Anything else → REVIEW(not found).

Transport failure, non-2xx, or unparseable body → **UNAVAILABLE**. The ё fold is not a blocker: е-spelled Russian entries exist (`елка`: `Russian lemmas` + `terms spelled with Е instead of Ё`). `WORD_LOOKUP_ENABLED=false` returns REVIEW(disabled) without a request.

Keeping `classify(lang, categories)` separate from HTTP lets tests pin the rules with fixtures captured from the responses above.

### Background review worker driven by database state
The route only stores the row with `review_state = QUEUED` and returns 202. `SuggestionReviewWorker.runOnce()` runs every 5 s in a supervised loop started from `main()` (same pattern as the poller) and processes up to 20 QUEUED rows, oldest first:

```
status ACCEPTED (auto-accepted earlier, announcement unconfirmed) → send announcement
status PENDING → lookup
    AUTO        → decide(accept, via AUTO, by "wiktionary") → send announcement
    REVIEW      → send decision message (+ reason line when flagged/disabled)
    UNAVAILABLE → lookup_attempts + 1; on the 3rd failure send decision message
                  marked "словарь недоступен", otherwise stay QUEUED
delivery confirmed (or bot disabled) → review_state = POSTED; otherwise stay QUEUED
```

- The pack changes before the announcement is sent: a Telegram outage delays the notice, never the word.
- The worker runs without a bot token too: auto-accept still applies and sends count as delivered (parity with today's store-only mode).
- Processing is sequential; the worst case (20 × 5 s timeout) only delays editors.

*Alternatives rejected*: verify inline in the request (player waits on Wiktionary; a failed send is still lost); fire-and-forget coroutine after storing (a restart strands the suggestion with no notification and no retry).

### Schema: nullable columns, NULL means "pre-existing row"
`word_suggestions` gains:
- `review_state` varchar(16) nullable — `QUEUED` | `POSTED`. NULL marks rows created before this change (already posted inline); the worker never selects them, so nothing is re-posted.
- `lookup_attempts` integer nullable — NULL treated as 0.
- `decided_via` varchar(16) nullable — `AUTO` | `EDITOR`. NULL on older decided rows is counted as editor.
- `auto_form` varchar(16) nullable — `DICTIONARY` | `INFLECTED`, written by an automatic acceptance so an announcement retried after a Telegram failure still names the form without a second lookup (added during implementation: the announcement is sent after the pack changes, so the form must outlive the tick that found it).

New `suggestion_reports(lang varchar(16), day date, sent_at timestamp)`, primary key `(lang, day)`.

The status vocabulary is unchanged: an auto-accept is `ACCEPTED` + `decided_via = AUTO` + `decided_by = "wiktionary"`. Add an index on `review_state` for the worker query.

*Alternatives rejected*: non-null `review_state` with a database default to backfill old rows (no default exists in this schema and the `MigrationUtils` diff behaviour for it is unverified; nullable needs no backfill); a new `AUTO_ACCEPTED` status (leaks server review mechanics into the shared client enum).

### Atomic decide
`decide` becomes one `UPDATE … SET status, decided_by, decided_via, decided_at WHERE id = ? AND status = 'PENDING'`. Zero rows updated → `NotFound` if the row is absent, else `AlreadyDecided`. `addGuess` runs only when a row was updated, so two simultaneous decisions change the pack at most once and the stored status always matches the pack.

### Telegram transport seam with confirmed delivery
A small `TelegramApi` interface — `suspend fun call(method: String, body: JsonObject): JsonObject?`, returning `result` only when the response has `"ok": true` — carries every bot call. The default implementation is today's JDK `HttpClient` code; tests inject a fake that records calls and can fail. Bot send methods report whether delivery was confirmed.

Message kinds, all routed to `topics[lang]` or the chat root:
- **Decision message**: existing text (`Новое слово: <word> (<lang>)` / `От: <author>`), an optional `⚠️` reason line (`Wiktionary: имя собственное`, `Словарь недоступен`, …), and the accept/reject keyboard.
- **Announcement**: `🤖 Автопринято: <word> (<lang>)` / `Словарная форма` or `Форма слова` · `en.wiktionary.org/wiki/<word>#<Lang>` / `От: <author>`, link preview disabled, no keyboard.
- **Daily report**: see below.

The author label (display name, else `Аноним`) is resolved by the worker instead of the route.

### Callback and polling fixes
- Unauthorized, malformed, or failing callbacks → `answerCallbackQuery` to the tapper only; the message and keyboard stay.
- `Applied` or `AlreadyDecided` → `editMessageText` with the original message text plus an outcome line (`✅ Принято — <editor>` / `❌ Отклонено — <editor>`, editor = username or first name) and no keyboard. `decided_by` keeps storing the Telegram user id.
- `getUpdates` returning non-`ok` or throwing → wait 3 s and log before polling again.

### Daily report: one-minute idempotent tick
`DailyReportScheduler.sendDue(now)` runs every 60 s, started only when the bot is enabled:

```
day = LocalDate(now, Asia/Tashkent) − 1
for lang in word_packs:
    (lang, day) in suggestion_reports → skip
    summary = decided_at ∈ [day 00:00, day+1 00:00) Asia/Tashkent,
              grouped AUTO / EDITOR-or-NULL accepted / REJECTED,
              + current PENDING count for lang
    quiet (no decisions, 0 pending) → record, send nothing
    else send → record only on confirmed delivery (failure retries next tick)
```

- One idempotent check covers the normal send, retry after a Telegram failure, and catch-up after downtime, with no separate code paths. Reports land by ~00:01.
- Send-then-record: a crash between the two yields a duplicate report at worst, never a missing one.
- Each group lists words until a per-group length budget (~1,200 characters) is spent, then `…и ещё N`, so three groups stay under Telegram's 4096-character limit even for 24-character suggestions. (A fixed 150-word cap, the first plan, would not: 3 × 150 × 26 characters is far over the limit.)
- Pure helpers `reportDay(now)` and `renderReport(lang, day, summary): String?` (null when quiet) carry the date and formatting logic.

*Alternatives rejected*: sleeping until the next midnight (needs separate retry and catch-up paths); per-language puzzle timezones (one editor clock chosen).

## Risks / Trade-offs

- [Wiktionary lists a rare or junk word in the right language] → only `guesses` change, never answers; blocking categories catch the common bad classes; every auto-accept is announced and appears in the daily report; kill switch; manual database fix.
- [Wiktionary renames categories] → acceptance categories renamed ⇒ words fall to REVIEW (safe direction); blocking categories renamed ⇒ flagged words could be auto-accepted → fixture tests pin today's names; kill switch.
- [Wikimedia throttles or blocks the User-Agent] → UNAVAILABLE ⇒ bounded retries ⇒ editors; volume is small and sequential.
- [Offensive words Wiktionary does not flag] → the existing blocklist still runs before verification.
- [Rolling back the image needs manual SQL] → the previous image refuses to start until the added columns are dropped (Migration Plan step 4), and suggestions queued under the new build stay unposted afterwards → roll forward with the kill switch instead whenever possible.
- [Duplicate report after a crash between send and record] → rare and harmless.
- [Worker backlog under a burst] → 20 rows per 5 s tick drains it; only editor latency grows.
- [Egress from the Oracle box to `en.wiktionary.org` blocked] → lookups report UNAVAILABLE and everything goes to editors; verify with the post-deploy smoke test.

## Migration Plan

1. Deploy through CI as usual. Startup migration adds the nullable columns and `suggestion_reports`; existing rows keep `review_state = NULL` and are never re-posted.
2. Document `WORD_LOOKUP_ENABLED` (default on) in `.env.example`; the box's `.env` needs no edit unless disabling.
3. Smoke test on the real bot: a real word → 🤖 announcement in its topic; a junk word → decision buttons; a non-editor tap leaves the buttons; the next 00:00 Asia/Tashkent report arrives.
4. Rollback: prefer rolling forward with `WORD_LOOKUP_ENABLED=false`. The previous image will **not** start against the migrated schema: its startup migration treats the added columns as unmapped and its guard refuses the resulting DROPs (verified 2026-09-14 by running the pre-change schema code against a migrated database). To roll back anyway, run by hand before redeploying it:
   ```sql
   DROP INDEX IF EXISTS idx_suggestion_review_state;
   ALTER TABLE word_suggestions
       DROP COLUMN review_state, DROP COLUMN lookup_attempts, DROP COLUMN decided_via, DROP COLUMN auto_form;
   ```
   `suggestion_reports` is outside the old schema diff and can stay. This discards review progress and the auto/editor distinction; suggestions still queued stay PENDING and unposted under the old build, which only posts inline.

## Open Questions

- Exact tick intervals, batch size, retry count and per-group word cap — tunable at implementation without changing specs or approach.
