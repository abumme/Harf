## Context

See proposal.md — Why. Current state that shapes the approach:

- `word_packs` holds one row per language: JSON text `answers`, `guesses`, `schedule` (800 entries anchored 2026-01-01 by `WordPackSchedule`) and an integer-string `version`. `GET /api/v1/wordpacks/{lang}` serves it with `ETag = version`.
- `WordPackServerService.addGuess` (accepted suggestions) and `mergeGuesses` (startup, bundled `<lang>_guess.txt` + `_answers.txt`) read, append and rewrite the blob; both are append-only, so a deleted word comes back on the next start.
- The pool runs `REPEATABLE READ`; `SuggestionServerService.decide` already opts into `READ COMMITTED` for its conditional update.
- The client (`WordPackRepository.buildFromDto`) rejects a whole fetched pack if any answer or schedule entry fails to tokenize, an answer's grapheme count is outside `minLength..maxLength` (4..7), or answers/schedule are empty; it adds answers and schedule words to the guess set itself and silently drops untokenizable guesses. A cached server pack fully replaces the bundled one.
- The tokenizer lives in `:sharedUI` (`engine/Tokenizer`, `engine/Normalizer`, `lang/LanguageConfig` incl. `Lexeme`, `lang/LanguageRegistry`, `lang/LaunchLanguages`). All five files are pure Kotlin (only `kotlinx.serialization`). `:sharedData` already targets android, jvm, js, wasmJs, iosArm64, iosSimulatorArm64, and `:sharedUI` depends on it with `api(...)`. `tools/wordlists` depends on `:sharedUI` only for the tokenizer. `-PbackendOnly` builds include `:sharedData` but not `:sharedUI`.
- Suggestions: `suggest()` validates by character length (2..24) plus blocklist/gibberish; `SuggestionReviewWorker` looks up Wiktionary and calls `decide` or `TelegramBot.sendForReview`; `TelegramBot` authorizes taps against `TELEGRAM_EDITOR_IDS` (any language) and edits the message using the text in the callback payload. Telegram's `sendMessage` result (with `message_id`) is discarded.
- Blocklists exist as `backend/src/main/resources/blocklists/<lang>_block.txt` (loaded per call in `SuggestionServerService`).
- Client HTTP engines: OkHttp (Android, desktop), Darwin (iOS), the browser engine (JS/Wasm). All request and transparently decode gzip.
- `staff-auth-roles` provides staff sessions, role and language-scope guards, the audit-log writer, the Kobweb panel `:adminWeb` (session state, the `AdminApi` client with the anti-forgery header, the server-paged data table and select components, Silk layout) and the convention that admin DTOs and `AdminRoutes` live in `:sharedData` under `uz.abumme.harfgame.data.admin.<area>`; this change plugs into them.

## Goals / Non-Goals

**Goals:**
- One source of truth for vocabulary that staff can edit safely and concurrently, with tombstones and attribution.
- Byte-for-byte the same validation on server and client.
- Every accepted change published in the same transaction; no pack the app would reject is ever published.

**Non-Goals:**
- Daily eligibility, answer-pool curation and schedule changes (→ `daily-word-calendar`). This change never writes `schedule` or `effective_from`, and exposes no eligibility data through any endpoint.
- Blocklist editing in the panel; blocklists stay files.
- Word metadata (meanings, notes), per-word history beyond the audit log, undo.
- Delta or partial pack downloads.
- Removing `word_packs`; it stays as the published snapshot.

## Decisions

### Move the pure engine into `:sharedData`, keeping packages
Move `Tokenizer.kt`, `Normalizer.kt`, `LanguageConfig.kt` (with `Lexeme`), `LanguageRegistry.kt`, `LaunchLanguages.kt` from `sharedUI/src/commonMain` to `sharedData/src/commonMain` under the same package paths (`uz.abumme.harfgame.engine`, `uz.abumme.harfgame.lang`). Because `:sharedUI` exposes `:sharedData` via `api`, no import in `:sharedUI` changes. `UzbekDailyWords` stays in `:sharedUI`. `TokenizerTest` and `LanguageConfigTest` move to `sharedData/src/commonTest`; `RegistryAndLexemeTest` stays (it uses Koin's `languageModule`). `tools/wordlists` switches to `implementation(project(":sharedData"))`.

*Alternatives rejected*: a new `:sharedEngine` module (another module name, build wiring and commit scope for five files); duplicating the tokenizer in `:backend` (drift is exactly the failure being prevented); renaming packages to `uz.abumme.harfgame.data.*` (touches every client import for no behavior gain).

### Pure pack-integrity validator shared by client and server
Add `WordPackIntegrity` in `sharedData/.../data/wordpack/` next to `WordPackDto`: `check(dto: WordPackDto, config: LanguageConfig): WordPackIntegrity.Result` returning either `Valid(answers, guesses, schedule)` (tokenized) or `Invalid(problems)`. It encodes today's `buildFromDto` rules exactly: answers and schedule non-empty; every answer and schedule entry tokenizes; every answer's grapheme count within `minLength..maxLength`; untokenizable guesses dropped; answers and schedule folded into guesses. `WordPackRepository.buildFromDto`/`isAdoptable` delegate to it (same behavior, existing `WordPackTest`/`WordPackClientTest` stay green). The backend runs it on every pack it is about to publish.

### Catalog table
```
words
  id                  varchar(36) PK
  lang                varchar(16)
  text                varchar(64)      normalized form (Tokenizer.normalize)
  status              varchar(16)      ACTIVE | REMOVED
  source              varchar(16)      BUNDLED | SUGGESTION | AUTO | STAFF
  suggestion_id       varchar(36) null → word_suggestions.id, SET NULL
  daily_eligible      boolean          from current pack answers at carry-over; not exposed here
  created_by_staff_id varchar(36) null → staff.id
  created_at          timestamp
  updated_by_staff_id varchar(36) null
  updated_at          timestamp
  removed_by_staff_id varchar(36) null
  removed_at          timestamp null
  unique (lang, text); index (lang, status, text); index (lang, created_at)
```
- `text` is stored normalized, so uniqueness, search and the blocklist all compare one form; clients normalize again before tokenizing, so publishing the normalized form is equivalent for them.
- A REMOVED row is the tombstone. Uniqueness spans both statuses: re-adding a removed word flips the same row back to ACTIVE (reported as `RESTORED`).
- **Edit** updates `text` on the same row (id, source and provenance survive) and inserts a REMOVED tombstone row for the previous spelling (same source, `removed_by` = editor), so a bundled old spelling is never merged back. Editing into a REMOVED spelling is a 409 that points to restore instead.
- The column is `daily_eligible boolean` (contract). A database default isn't used anywhere in this schema, so the Exposed column is declared non-null with a Kotlin-side `default(false)` and every insert path sets it explicitly; the schema test verifies the generated DDL.

*Alternatives rejected*: a separate tombstone table (two lookups on every add/merge, and edit/restore become moves between tables); hard delete plus an "ignore list" (loses provenance); keeping raw input text and normalizing on read (uniqueness would need a functional index and every query would repeat the normalization).

### Validation pipeline
`WordValidator(lang)` in `:backend` (`admin/words/`): `LanguageRegistry.config(lang)` (unknown config → `unsupported_language`) → `normalize` → trim; empty → `empty`; `tokenize` null → `not_tokenizable`; grapheme count outside `minLength..maxLength` → `bad_length`; normalized text in the language blocklist (entries normalized the same way) → `blocklisted`. Uniqueness is checked inside the write transaction (`duplicate`, `removed_exists`). The blocklist loader moves out of `SuggestionServerService` into a shared `Blocklists` object cached per language, used by suggestions and the catalog.

Because the grapheme engine now lives in `:sharedData` and the Kobweb panel is Kotlin/JS, the panel runs the same `Normalizer`/`Tokenizer` locally for instant feedback while typing (normalized form, grapheme count, tokenizes, supported length). What only the server knows — the blocklist and the word's current catalog state — comes from a side-effect-free `POST /api/v1/admin/words/check` returning `{normalized, graphemeCount, outcome, reason?}`, called debounced and before submit. Every write revalidates on the server, which stays the authority; the blocklist is not shipped to the browser.

### Publishing in the same transaction, serialized per language
Every mutation (add, bulk add, edit, remove, restore, suggestion accept, merge) runs as:
```
dbQuery(READ_COMMITTED) {
    lock = SELECT … FROM word_packs WHERE lang = ? FOR UPDATE      -- serializes writers per language
    apply row changes to words (+ audit rows)
    if nothing changed → return (no publish)
    guesses  = SELECT text FROM words WHERE lang=? AND status='ACTIVE' ORDER BY text
    answers  = SELECT text … AND daily_eligible ORDER BY text
    dto      = WordPackDto(lang, version+1, effectiveFrom, anchorEpochDay, answers, guesses, schedule(stored))
    WordPackIntegrity.check(dto, config) Invalid → throw IntegrityRefused (rolls back everything)
    UPDATE word_packs SET guesses, answers, version, updated_at
}
```
- `FOR UPDATE` first, under `READ COMMITTED`: a second writer waits and then sees the first writer's rows instead of hitting the serialization error `REPEATABLE READ` raises on a concurrently updated row (same reasoning as `decide`).
- Rebuilding ~23k guesses is two indexed selects and one JSON encode (tens of ms), cheaper and simpler than patching the JSON blob.
- Ordering by text makes the published lists deterministic; clients do not depend on answer or guess order when a schedule is present (`DailyPuzzleProvider` indexes the schedule).
- `IntegrityRefused` maps to 422 `pack_integrity` with a generic message ("Изменение нарушит целостность словаря"). The realistic trigger is removing the last eligible answer of a small language; the message says nothing about daily words.
- Languages without a `word_packs` row are not editable (404); seeding still creates rows.

*Alternatives rejected*: a debounced background publisher (breaks "live immediately" and needs a dirty-flag/retry path); advisory locks (the pack row already exists per language and is what we update); publishing outside the transaction (a crash could leave catalog and pack disagreeing).

### Carry-over of existing packs (one time, per language)
At startup, after `seed()` and before the merge: for each language with a `word_packs` row and zero `words` rows, in one transaction under the pack row lock:
- `words = normalize(guesses ∪ answers)`, deduplicated by normalized text (collisions logged).
- `source`: match `(lang, word)` against `word_suggestions` with `status = ACCEPTED` → `AUTO` when `decided_via = AUTO`, else `SUGGESTION` (with `suggestion_id` and `created_at = decided_at`); everything else `BUNDLED` with `created_at` = migration time.
- `daily_eligible` = normalized text is in the pack's `answers`.
- Words that fail today's validation (e.g. legacy untokenizable guesses) are still imported as ACTIVE and counted in a startup log line; staff can remove them.
- No publish, no version change: the published blob already holds these words. One SYSTEM audit entry per language (`WORD_CATALOG_IMPORTED`, count).

"Zero rows for the language" is the idempotency guard. A partial import is impossible because it is one transaction.

### Startup merge of deployed dictionaries → catalog
`mergeGuesses()` becomes `mergeBundled()`: per language, normalize the bundled `<lang>_guess.txt` + `<lang>_answers.txt` lines, then `INSERT … ON CONFLICT (lang, text) DO NOTHING` as `BUNDLED`, `daily_eligible = false`. REMOVED tombstones occupy the key, so removed and respelled words are skipped. Publish once only if rows were inserted; one SYSTEM audit entry per language with the count. Invalid bundled lines are skipped and logged (the word-list builder already guarantees validity). The existing `WordPackMergeTest` cases are rewritten against the catalog, plus a tombstone case.

### Suggestions integration
- **Playable check in `suggest()`** (new rejection reason `not_playable`): tokenize + board length via the shared engine, after the existing checks. The existing char bounds stay. The client only offers suggestions for full-length words typed on the language keyboard, so real players are unaffected. The class comment noting char-length validation is updated.
- **Already present** checks the ACTIVE catalog instead of the pack blob.
- **Removed words**: the worker checks the catalog before the Wiktionary lookup; a REMOVED match is sent to review with the new `ReviewReason.REMOVED_BY_STAFF` ("⚠️ Слово ранее удалено редакторами") and is never auto-decided.
- **`decide(...)`** keeps its conditional `UPDATE … WHERE status = 'PENDING'`; on `Applied(accept)` it calls `catalog.acceptSuggestion(lang, word, suggestionId, via)`: insert ACTIVE (`AUTO`/`SUGGESTION`), or flip a REMOVED row back to ACTIVE, or no-op if already ACTIVE, each followed by the publish routine. The decide statement and the catalog write run in one transaction, so a status and pack disagreement is impossible. If a legacy pending word fails catalog validation, `decide` returns `Invalid(reason)` and changes nothing; Telegram answers "Слово не прошло проверку" and the panel shows the reason (the editor can still reject).
- **Attribution**: `decided_by` stores `staff:<staffId>` for panel decisions and for Telegram taps by linked staff; legacy allowlist taps keep the Telegram user id; automatic stays `wiktionary`.
- **Audit**: `SUGGESTION_DECIDED` (actor STAFF for panel, TELEGRAM for taps with `actor_staff_id` when linked, SYSTEM for automatic), plus the catalog's `WORD_ADDED`/`WORD_RESTORED`.

### Telegram authorization via staff, legacy allowlist during migration
`TelegramBot` takes an `EditorDirectory` instead of `editorIds`:
```
suspend fun authorize(telegramUserId: Long, lang: String): Editor?
  staff row with telegram_user_id = id, status ACTIVE, role ADMIN or lang ∈ staff_languages → Editor.Staff(staffId, name)
  else id ∈ TELEGRAM_EDITOR_IDS (legacy, all languages)                                     → Editor.Legacy(id)
  else null
```
The callback first loads the suggestion (to learn its language; 404 → "Не найдено"), then authorizes, then decides. The status is re-checked by the conditional update, so the extra read adds no race. Unauthorized, out-of-scope and disabled users get the existing "Недостаточно прав" popup, and the message is untouched. The outcome line keeps the Telegram name of the tapper.

### Telegram message tracking and panel decisions
`word_suggestions` gains nullable `telegram_chat_id varchar(64)`, `telegram_message_id bigint`, `telegram_text text` and `review_reason varchar(16)`. `sendForReview` returns the sent `Message` (`TelegramApi.call` already returns `result`). The worker records the review reason when it sends for review, and `markPosted(id, sent)` stores chat id, message id and the exact text. When the bot is disabled, `review_reason` is still recorded and the Telegram columns stay NULL. After a panel decision (Applied or AlreadyDecided), if the three columns are set and the bot is enabled, the service calls `editMessageText(chat_id, message_id, text + "\n\n✅ Принято — <staff name> (панель)")` without `reply_markup`. The call is best-effort and a failure is only logged. If the buttons survive, a later tap resolves to the existing "ℹ️ Уже обработано" edit. Rows posted before this change have NULL columns and are not edited.

`telegram_text` is stored because Telegram offers no "append" or "get message", and rebuilding the text from the row could differ from what was posted (the author label can change after posting).

*Alternatives rejected*: `editMessageReplyMarkup` only (buttons vanish without an outcome, which contradicts the "decided message keeps its context" requirement); a reply message per panel decision (chat noise).

### Admin API (`backend/.../admin/words`, `backend/.../admin/suggestions`)
All routes sit behind the staff session guard from `staff-auth-roles`: word reads need `WORDS_READ`, word mutations `WORDS_WRITE`, suggestion review `SUGGESTIONS_REVIEW` (the permissions `staff-auth-roles` reserves for WORDER and ADMIN), applied with `RequirePermission`. `lang` is checked against the caller's scope with `requireLanguage` (403 `forbidden`). Word ids are resolved to their language before the scope check.

| Method & path | Purpose |
|---|---|
| `GET /api/v1/admin/words?lang&q&status=ACTIVE\|REMOVED\|ALL&source&addedBy&addedFrom&addedTo&sort=text\|created\|updated&dir&page&size` | page of `WordDto` + `total` (size ≤ 200) |
| `POST /api/v1/admin/words/check` `{lang, text}` | normalized form + outcome, no side effects |
| `POST /api/v1/admin/words` `{lang, text}` | add → 201 `WordDto` (`restored` flag); 422 `validation_failed{reason}`; 409 `conflict{reason: duplicate}` |
| `POST /api/v1/admin/words/bulk` `{lang, lines[≤1000]}` | `{results:[{line, text?, outcome: ADDED\|RESTORED\|DUPLICATE\|INVALID\|BLOCKLISTED, reason?}], packVersion}`; >1000 → 422 |
| `PATCH /api/v1/admin/words/{id}` `{text}` | edit; 409 `removed_exists` / `duplicate` |
| `POST /api/v1/admin/words/{id}/remove`, `/restore` | tombstone / restore |
| `GET /api/v1/admin/suggestions?lang&status=PENDING\|DECIDED&page&size` | pending oldest first; decided newest first |
| `POST /api/v1/admin/suggestions/{id}/decision` `{accept}` | 200 applied; 409 `already_decided`; 422 `validation_failed` |

`WordDto` = `id, lang, text, graphemeCount, status, source, suggestionId?, createdBy?{id, username, displayName}, createdAt, updatedBy?, updatedAt, removedBy?, removedAt?`. It has no eligibility or daily field, for any role. `SuggestionDto` = `id, lang, word, author (label), createdAt, reason?, status, decidedBy?{kind: STAFF|TELEGRAM|AUTO, name}, decidedAt?`. `reason` comes from a new nullable `word_suggestions.review_reason varchar(16)` (the `ReviewReason` name) that the worker writes when it sends a suggestion for review. It is NULL on rows reviewed before this change and while a suggestion is still queued; the panel then shows "ожидает проверки".

Errors use `ApiErrorResponse`. The request/response types (`WordDto`, `WordPageDto`, `CheckWordRequest`/`CheckWordResult`, `AddWordRequest`, `BulkAddRequest`/`BulkAddResult`, `EditWordRequest`, `SuggestionDto`, `SuggestionPageDto`, `DecisionRequest`) are `@Serializable` classes in `:sharedData` (`uz.abumme.harfgame.data.admin.words`, `uz.abumme.harfgame.data.admin.suggestions`) and the paths are `AdminRoutes` constants, so the backend routes and the Kobweb panel compile against the same contract; there is no separate API document to regenerate.

### Panel pages (Kobweb `@Page`s `/words` and `/suggestions` in `:adminWeb`)
- **Слова** (`/words`): Silk `Tabs` for the languages in the principal's scope (from the session state loaded via `auth/me`; ADMIN sees every pack language); a debounced search field and filters (status, source, added by, date range) built from Silk `TextInput`, the shared select component and native date inputs; the shared server-paged, sortable data table from `staff-auth-roles` with provenance (source, added by/at, updated by/at, removed by/at) in a row expander; dialogs on Silk `Overlay` — add (instant local preview from the shared normalizer/tokenizer plus the debounced `/check` outcome), bulk add (textarea, live line counter capped at 1000, results grouped by outcome), edit (409 messages including "восстановите удалённое слово"), remove confirmation; a "Удалённые" filter/tab with restore.
- **Предложения** (`/suggestions`): the same language tabs; an "Ожидают" tab (oldest first, reason chip, accept/reject buttons disabled while a request is in flight, a 409 shows "Уже обработано" and refreshes the list); an "История" tab (newest first, outcome and who decided).
- Both navigation entries are shown to ADMIN and WORDER; the WORDER home placeholder from `staff-auth-roles` is replaced by `/words`. View state (language, filters, page) is kept in query parameters so a reload or a shared link restores the view; the backend's `index.html` fallback plus the Kobweb router handles deep links.
- All copy is Russian and lives in the panel's strings object. Page state holders (query/paging/sort state, bulk-input parsing, result grouping, decision-outcome mapping, local validation preview) are plain Kotlin kept apart from composables so `:adminWeb:jsTest` covers them; the composables themselves are verified manually against a running backend.

### Compression
Add `ktor-server-compression` to the version catalog and install `Compression { gzip { minimumSize(1024) }; deflate { minimumSize(1024) } }` for JSON responses. The plugin only encodes when the request's `Accept-Encoding` allows it, so clients that don't ask get identity. The 304 path has no body. The ETag stays the version string: the representation changes, but its identity for `If-None-Match` purposes does not, and clients compare only the version.

*Alternative rejected*: `encode zstd gzip` in the host Caddyfile (outside the repo, untested in CI, and must be kept in step by hand).

## Risks / Trade-offs

- [Shared admin DTOs and the grapheme engine in `:sharedData` also end up in the app's dependency graph] → they are small, unused by app code paths, and removed by R8/Kotlin DCE in release builds; `WordDto` deliberately carries no daily field for any consumer.
- [Every save makes every client of that language re-download the pack] → gzip (en ≈190 KB → ≈60 KB); clients only pull on launch/foreground; bulk adds publish once.
- [Moving engine files breaks a platform compile (iOS/Wasm) not covered by `jvmTest`] → the code is pure common Kotlin; the verification follows the compile-check order JVM → Android → Wasm/JS; iOS relies on CI/macOS.
- [Carry-over normalizes stored words, and two legacy spellings collapse into one] → logged; the client normalizes before matching, so the playable set is unchanged.
- [Removing the last eligible answer is refused with a generic message a WORDER cannot act on] → rare (kk 23, uz 5 answers); ADMIN tooling in `daily-word-calendar` manages the pool.
- [A WORDER removes a word that is a scheduled daily word] → the schedule is untouched and the client adds schedule words to guesses, so the day still plays; the calendar change handles future days.
- [Legacy allowlist forgotten in `.env`] → it stays all-language; the migration plan removes it once editors are linked, and startup logs a warning while it is set.
- [Stored `telegram_text` goes stale if the bot's wording changes] → only the appended edit uses it; harmless.
- [Admin-set text search over ~23k rows with `LIKE %q%`] → a sequential scan in single-digit milliseconds at this size; add `pg_trgm` only if it shows up.
- [Rollback needs manual SQL] → the previous image refuses to start while `word_suggestions` has unmapped columns (DROP guard). Staff removals would also be undone by its append-only merge (see Migration Plan).

## Migration Plan

1. Prerequisites: `import-guess-dictionaries` and `suggestion-auto-accept` archived; `staff-auth-roles` deployed with at least one ADMIN.
2. On the box, before deploying: `docker exec harf-postgres pg_dump -U $POSTGRES_USER -t word_packs -t word_suggestions $POSTGRES_DB > word_packs_pre_catalog.sql`.
3. Deploy through CI. Startup: the schema diff creates `words` and adds the four `word_suggestions` columns → `seed()` → carry-over (logs per-language counts, no version change) → bundled merge (normally no-op) → background loops.
4. Verify: `GET /api/v1/wordpacks/en` returns the same version as before the deploy; the panel lists en ≈23k words.
5. Create or verify a staff account for each current Telegram editor, set their Telegram user id and languages, confirm a tap works, then remove `TELEGRAM_EDITOR_IDS` from `.env` and restart.
6. Smoke test (tasks §10).
7. Rollback (prefer rolling forward):
   ```sql
   ALTER TABLE word_suggestions DROP COLUMN telegram_chat_id, DROP COLUMN telegram_message_id,
       DROP COLUMN telegram_text, DROP COLUMN review_reason;
   ```
   Then redeploy the previous image. `words` can stay; the old diff ignores tables it does not model. `word_packs` still holds the last published snapshot, so clients are unaffected. The old append-only merge will re-add bundled words staff removed.

## Open Questions

- Page-size default and maximum, bulk limit (1,000) and search debounce: tunable without spec changes. Resolved: 50 per page (max 200), 1,000 lines, 300 ms search and 350 ms check debounce.
- Whether the Words table also shows a per-language word count per source: cosmetic, decide during implementation. Resolved: not shown; the source filter plus the table's total gives the count.

## Implementation notes (apply, 2026-09-17)

Details the design left open or got wrong, decided while implementing. None narrows a spec requirement.

- **Engine move.** Besides the five files, `engine/Grapheme.kt` (`Word`, returned by `Tokenizer.word`) and `lang/UzbekDailyWords.kt` moved to `:sharedData` (same packages). The Context line "tools/wordlists depends on `:sharedUI` only for the tokenizer" was wrong: `GuessListWriter` also reads `UzbekDailyWords`, so leaving it in `:sharedUI` would have kept the tool on `:sharedUI`. It is pure data; no import changed anywhere.
- **Shared word rules.** `data.admin.words.WordRules.check(config, raw)` (normalize + trim, `empty`, `not_tokenizable`, `bad_length`) is the one implementation behind the backend `WordValidator` (which adds the blocklist) and the panel's local preview. `WordRules.MAX_BULK_LINES = 1000`.
- **Schema.** `words.daily_eligible` is Exposed `bool(...).default(false)`, i.e. `NOT NULL DEFAULT false` in the DDL (the contract's wording); every insert sets it explicitly, and `DatabaseSchemaTest` checks the column and that a second migration emits nothing. Only `created_by_staff_id` has a foreign key (to `staff`), as drawn; `updated_by`/`removed_by` are plain columns. The Kotlin property for column `source` is `WordsTable.wordSource` (`source` clashes with Exposed's `ColumnSet.source`). Indexes: `idx_words_lang_text` (unique), `idx_words_lang_status_text`, `idx_words_lang_created`.
- **Publisher.** `admin/words/PackPublisher`: `lock(lang)` (`SELECT … FOR UPDATE`, returns a `LockedPack`) and `publish(pack)` (rebuild, `WordPackIntegrity`, version + 1, returns the new version; throws `PackIntegrityException`, whose problems are only logged). All catalog writers run at READ COMMITTED.
- **Error shapes.** Word validation: `422 validation_failed`, message `text: <reason>`; bulk over 1,000: `lines: too_many_lines`; integrity refusal: `pack: pack_integrity`; add/edit conflicts: `409 conflict`, `text: duplicate | removed_exists | not_active`; a missing `lang` query parameter: `lang: required`. Suggestion decisions: `409 conflict` `status: already_decided`, `422 validation_failed` `word: <reason>`. The reason vocabularies are `WordReasons` and `SuggestionReasons` in `:sharedData`.
- **DTO details.** `WordDto.restored` (default false) is set only in the add response that restored a removed word (201 either way). `WordDto.graphemeCount` is null for a legacy word that no longer tokenizes. `CheckWordResult.outcome` is `VALID | RESTORABLE | DUPLICATE | INVALID | BLOCKLISTED`. `WordPageDto`/`SuggestionPageDto` are type aliases of the existing `PageDto<T>`. Query names live in `WordParams` (`status` = `ACTIVE | REMOVED | ALL`, default ACTIVE; `addedFrom`/`addedTo` epoch ms, `addedTo` exclusive; `sort` = `text | created | updated`, `dir` = `asc | desc`; size ≤ 200) and `SuggestionParams` (`lang` optional: without it every language in scope). `SuggestionDto.reason` is a string (`ReviewReasons` names), so a new reason never breaks the panel's decoding.
- **Idempotent writes.** Removing a removed word, restoring an active word and editing to the same normalized spelling answer 200 with the word and publish nothing. Edit, remove and restore set `updated_by`/`updated_at`; the tombstone of an old spelling copies source, suggestion id, `created_by` and `created_at` and is `removed_by` the editor.
- **Carry-over.** Spellings empty after normalization or longer than 64 characters are skipped and counted in the log line; words failing validation are imported and counted. A word from an accepted suggestion gets `created_at = decided_at`. Audit: `WORD_CATALOG_IMPORTED` (count, invalid, packVersion). The merge's audit entry is `WORD_BUNDLED_MERGED` with the count and at most 200 of the added words; blocklisted bundled lines are skipped like invalid ones.
- **Startup.** `prepareWordCatalog(wordPacks, catalog)` in `Application.kt` runs seed → `catalog.carryOver()` → `catalog.mergeBundled()` before the server binds. `ApplicationWiringTest` runs it against the deployed resources on an empty database, then publishes a change to every real pack, proving the seeded packs pass `WordPackIntegrity`.
- **Suggestions.** The blocklist check in `suggest()` now compares normalized forms (a variant spelling of a blocked word is `offensive` too). `not_playable` comes after `already_present`; an unknown language is `not_playable`. `decide(...)` gained `actor` (audit) and `authorize(lang)` (run inside the transaction once the language is known, so the panel's scope refusal changes nothing); it validates before the conditional update and maps a `PackIntegrityException` to `Invalid(pack_integrity)`. The worker also sends a still-queued legacy word that fails the catalog rules to editors without a lookup (new `ReviewReason.NOT_PLAYABLE`, "можно только отклонить"), because accepting it would be refused; a suggestion staff decided in the panel while it was still queued is not announced.
- **Telegram.** `sendForReview` returns `ReviewPost` (`Posted(PostedMessage?)`, `Disabled`, `NotConfirmed`); the stored chat id is the numeric `chat.id` of Telegram's answer. After a decision edit succeeds — a tap or a panel decision — the three `telegram_*` columns are cleared, so a later panel decision that finds the suggestion already decided never overwrites the outcome Telegram shows; such a decision edits only while the columns are still set, with "ℹ️ Уже обработано", like a stale tap. A malformed callback is answered "Некорректно" before the suggestion is loaded.
- **Compression** is installed application-wide for `application/json` responses of at least 1 KB (gzip and deflate), so admin lists benefit too; the panel's HTML and scripts are untouched. The API answers no HEAD requests, so task 10.4's check uses a GET.
- **Wiring.** `AdminBackend` gained `wordPacks`, `catalog` (`WordCatalogService`), `suggestionService` and `telegram` parameters (defaults build fresh instances for tests; `main` passes the shared ones) and exposes `suggestions` (`SuggestionsService`). `AuditService` labels `WORD` and `SUGGESTION` targets with their current spelling.
- **Panel.** Pure state holders: `words/WordsState.kt` (`WordsQueryState`, `BulkInput`, `groupBulkResults`, `WordPreview`), `words/WordMessages.kt`, `suggestions/SuggestionsState.kt` (`SuggestionsQueryState`, `decisionOutcome`), covered by `WordsStateTest` and `SuggestionsStateTest`. New shared components: sortable `DataTable` headers (`TableColumn.sortKey`, `TableSort`) and an expandable detail row, `FormDialog`, `TextAreaField`, `StateTabs`/`LanguageTabs` (Silk `Tabs`, one-line labels that wrap as a whole). The URL's `page` parameter is 1-based. The "Кто добавил" filter offers every staff member to an ADMIN (from the staff list) and "Я" to a WORDER, who cannot list staff. `/` sends a member who reads words but not the whole audit log (a WORDER) to `/words` (`homeRedirect`). The word and pending-suggestion tables show the day, with the time as a tooltip.
- **Test fixtures.** `resetAdminData()` now leaves one valid pack per language (a single daily answer, e.g. `wharf` in English) carried into the catalog, and clears `words` and `word_suggestions`; `insertPack(...)` carries its pack into the catalog unless `catalog = false` and defaults `answers`/`schedule` to that one daily answer, because publishing refuses a pack without answers.
