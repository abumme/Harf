## 1. Schema & suggestion service (:backend)

- [x] 1.1 Add nullable `review_state`, `lookup_attempts`, `decided_via` (plus a `review_state` index) to `WordSuggestionsTable` and a new `SuggestionReportsTable(lang, day, sent_at, PK(lang, day))`; register the new table in the `DatabaseFactory` migration list; verify the backend schema test passes and a row inserted without the new columns reads back with `review_state` NULL.
- [x] 1.2 `suggest` stores new rows with `review_state = QUEUED`; move author-label resolution (display name, else `Аноним`) into a service helper the worker can call; adapt `storedAuthorIsDisplayNameElseAnonymous` and verify `SuggestWordsTest` is green.
- [x] 1.3 Make `decide(id, accept, editor, via)` a single conditional UPDATE on `status = 'PENDING'` that records `decided_via`, calling `addGuess` only when a row changed; test that two concurrent decides yield exactly one `Applied` and bump the pack version once.
- [x] 1.4 Add worker/report queries — queued rows (oldest first, limit), mark posted, increment lookup attempts, and `dailySummary(lang, from, to)` (auto-accepted / editor-or-NULL accepted / rejected words + current pending count); test the summary with a decision exactly on the day boundary and a legacy NULL `decided_via` counted as editor.

## 2. Wiktionary lookup (:backend)

- [x] 2.1 Implement pure `classify(lang, categories)` → AUTO(dictionary form | inflected form) / REVIEW(reason) with the language prefix map and blocking list from design.md; `WiktionaryClassifyTest` with category fixtures from real responses (книга, книги, китоб, crane, walks, Павел, NATO, teh, shit, елка, a page with only Bulgarian categories, a missing page, an unmapped language) is green.
- [x] 2.2 Implement `WiktionaryLookup` over the JDK HTTP client (User-Agent, 5 s timeout, no redirects, missing-page detection) mapping transport/HTTP/parse failures to UNAVAILABLE and `WORD_LOOKUP_ENABLED=false` to REVIEW(disabled) without a request; unit-test parsing of saved JSON bodies (present, missing, malformed) and verify no test performs network I/O.

## 3. Telegram bot (:backend)

- [x] 3.1 Introduce `TelegramApi` (`call(method, body)` → `result` only on `"ok": true`) with the JDK HTTP implementation, route every bot call through it, and make send methods report confirmed delivery; existing Telegram tests in `SuggestWordsTest` pass using a fake.
- [x] 3.2 Build the message kinds from design.md — decision message with optional reason line, auto-accept announcement (form kind, `en.wiktionary.org/wiki/<word>#<Lang>` link, author, link preview disabled, no keyboard), daily report text — all routed to `topics[lang]` else the chat root; tests assert text, `message_thread_id`, and that the announcement has no `reply_markup`.
- [x] 3.3 Callback fixes: unauthorized/malformed/failing taps only answer the callback; `Applied`/`AlreadyDecided` edit the message to original text + outcome line with the editor's name and no keyboard; a fake-API test asserts a non-editor tap sends no `editMessageText`.
- [x] 3.4 Polling backoff: a non-`ok` or throwing `getUpdates` waits 3 s before the next poll; a `runTest` virtual-time test asserts a permanently failing API is polled a bounded number of times over a fixed virtual interval.

## 4. Review worker (:backend)

- [x] 4.1 Implement `SuggestionReviewWorker.runOnce()` per design.md: QUEUED rows oldest first (batch 20); AUTO → `decide(accept, via AUTO, by "wiktionary")` then announcement; REVIEW → decision message; UNAVAILABLE → increment attempts, decision message marked unverified on the 3rd; ACCEPTED-but-QUEUED → announcement only; `POSTED` only on confirmed delivery; bot disabled counts as delivered.
- [x] 4.2 `SuggestionReviewWorkerTest` (fake lookup + fake `TelegramApi`, test database) covers: AUTO path (status, `decided_via`, pack version, announcement, POSTED); REVIEW path (still PENDING, buttons, POSTED); UNAVAILABLE twice stays QUEUED then buttons with the warning; send failure stays QUEUED and the next run resends the announcement without a second decide; legacy NULL rows untouched; lookup disabled → buttons. Green.

## 5. Route & wiring (:backend)

- [x] 5.1 `suggestionRoutes` no longer takes or calls the bot; update `authenticatedSuggestionStoredStoreOnly` to assert the stored row is `QUEUED` and the response is unchanged (202, PENDING); green.
- [x] 5.2 `Application`: build the lookup (reading `WORD_LOOKUP_ENABLED`, default true), worker and scheduler; start the worker loop (5 s) always and the poller and report loop (60 s) only when the bot is enabled, through one supervised-loop helper; verify the backend compiles and `module()` integration tests still start without Telegram env.
- [x] 5.3 Document `WORD_LOOKUP_ENABLED` (optional, default true, what disabling does) in `.env.example` next to the Telegram block; verify by reading the file.

## 6. Daily report (:backend)

- [x] 6.1 Implement pure `reportDay(now)` (previous Asia/Tashkent date) and `renderReport(lang, day, summary)` (null when quiet; per-group length budget keeping the whole report within the Telegram 4096-character limit, with `…и ещё N`); tests for 23:59:59 and 00:00:01 Asia/Tashkent and for truncation are green.
- [x] 6.2 Implement `DailyReportScheduler.sendDue(now)` over pack languages: skip recorded `(lang, day)`; quiet → record without sending; otherwise send and record only on confirmed delivery. Tests with a fake `TelegramApi`: sends once, no resend after recording, retries after a failed send, quiet day sends nothing. Green.

## 7. Verification

- [x] 7.1 Full backend suite green: `./gradlew :backend:test` (CI Postgres service).
- [x] 7.2 `openspec validate suggestion-auto-accept --strict` passes.
- [ ] 7.3 After deploy, smoke test on the real bot: a real word produces a 🤖 announcement in its language topic; a junk word produces decision buttons; a non-editor tap leaves the buttons; the next 00:00 Asia/Tashkent report arrives in each active topic.
