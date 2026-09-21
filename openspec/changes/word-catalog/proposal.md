## Why

Staff now have accounts, roles and per-language scope (`staff-auth-roles`), but the words themselves still live as one JSON blob per language in `word_packs`: nothing can be edited or removed without SQL, two writers would overwrite each other, nobody can tell who added a word, and the startup dictionary merge would bring a deleted word straight back. WORDERs need to manage words on screen, in their own languages, with every change reaching players as soon as it is saved — without ever publishing a pack the app would silently refuse.

## What Changes

- A **word catalog**: one row per word per language with status (active or removed), source (bundled dictionary, player suggestion, automatic acceptance, staff) and who/when for adding, editing and removing. Removal is a tombstone, so a removed word stays removed.
- Staff browse, search, filter and page words per language; add one word (shown in its normalized form first), paste up to 1,000 words at once with a per-line outcome, edit a word's spelling, remove and restore. WORDERs work only in their assigned languages; ADMIN in all.
- Every word is validated with the app's own rules: normalized per language, must tokenize, grapheme length within the language's board lengths, not on the language blocklist, not a duplicate.
- **Instant publishing**: every successful change rebuilds that language's published pack and advances its version in the same transaction; clients receive it through the existing pack sync. The server refuses any change whose resulting pack would fail the app's integrity check.
- The pure grapheme engine (tokenizer, normalizer, language configs) and a new pack-integrity validator move to `:sharedData`, so server and app validate identically. Package names stay the same.
- Existing vocabulary is carried into the catalog once, with no change to what clients receive. The startup merge of deployed dictionaries now adds to the catalog and never re-adds a word staff removed or respelled.
- **Suggestions**: accepted words (by editors or automatically) enter the catalog and publish instantly; staff can review pending suggestions in the panel; a word staff removed is never accepted automatically again; a suggested word must be playable (tokenizes, supported length).
- **Telegram**: editors are authorized as active staff with a linked Telegram user ID, limited to their languages; the `TELEGRAM_EDITOR_IDS` allowlist keeps working as all-language editors during migration. A panel decision also strips the Telegram buttons when the message is known.
- Word-pack responses are gzip-compressed when the client accepts it (~190 KB → ~60 KB for English).
- WORDER-facing screens and responses never carry daily-word information; catalog endpoints expose no daily-eligibility data at all (ADMIN tooling for that arrives in `daily-word-calendar`).
- Panel: **Words** (`/words`) and **Suggestions** (`/suggestions`) pages in the Kobweb admin panel `:adminWeb`, with instant validation feedback from the shared grapheme engine.

## Capabilities

### New Capabilities
- `word-catalog`: staff-managed vocabulary per language — browse/search, add, bulk add, edit, remove/restore, validation, provenance, language scope, instant publishing, integrity guarantee, audit, no daily-word exposure.

### Modified Capabilities
- `word-suggestions`: editor authorization moves to staff accounts with language scope (legacy allowlist during migration); accepted words enter the catalog and publish instantly; new requirements for reviewing suggestions in the panel, for removed words never being auto-accepted, and for suggested words having to be playable.
- `word-pack-distribution`: the startup dictionary merge never re-adds staff-removed words; new requirements that the published pack follows the catalog immediately and that pack responses are compressed on request.

## Impact

- **Depends on**: `staff-auth-roles` (staff sessions, roles, language scope, audit log, the Kobweb panel `:adminWeb` and its shared components, the `:sharedData` admin DTO convention) and on `import-guess-dictionaries` and `suggestion-auto-accept` being archived first — this change's MODIFIED requirements are written against the text those changes add.
- **`:sharedData`**: gains `engine/Tokenizer`, `engine/Normalizer`, `lang/LanguageConfig`, `lang/LanguageRegistry`, `lang/LaunchLanguages` (moved, same packages) and a pack-integrity validator next to `WordPackDto`; their tests move too. It also gains the admin words/suggestions DTOs (`data.admin.words`, `data.admin.suggestions`) and their `AdminRoutes` paths, shared by the backend and the panel.
- **`:sharedUI`**: files removed (now from `:sharedData`), `WordPackRepository` uses the shared validator; no behavior change. `tools/wordlists` depends on `:sharedData` instead of `:sharedUI`.
- **`:backend`**: new `words` table; `word_suggestions` gains `telegram_chat_id`, `telegram_message_id`, `telegram_text`, `review_reason`; `WordPackServerService` publishes from the catalog; suggestion service, review worker and Telegram bot updated; new admin `words` and `suggestions` API areas; Compression plugin (new catalog entry `ktor-server-compression`).
- **`:adminWeb`** (Kobweb): `/words` and `/suggestions` pages, their state holders and `jsTest` coverage; the WORDER home now opens `/words`.
- **API**: public `GET /api/v1/wordpacks/{lang}` contract unchanged (gzip when requested); `POST /api/v1/suggestions` may now reject unplayable words with the existing `rejected` error.
- **Ops**: back up `word_packs` before the first start; link existing editors' Telegram IDs to staff, then drop `TELEGRAM_EDITOR_IDS`. Rolling back needs the new `word_suggestions` columns dropped by hand.
- **No app release required**; client code changes are refactors with identical behavior.
