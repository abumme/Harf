## Why

The bundled vocab is small (en 663 guesses, ru 152, kk 36, uz ~27), so most suggestions are real words — and every one currently waits for an editor tap. English Wiktionary tags words by language and by form for all five launch languages, so the backend can accept verifiable real words itself, leave editors only the doubtful ones, and summarize each day's outcomes per language so nothing accepted goes unseen.

## What Changes

- After a suggestion is validated and stored, it is checked against English Wiktionary in the background. A dictionary form or an inflected form in the suggestion's language is **auto-accepted** into that language's pack guesses. Words that are not found, flagged (proper noun, abbreviation, misspelling, vulgar/offensive), or unverifiable go to editors with the existing Accept/Reject buttons plus a reason line. Verification never auto-rejects.
- Auto-accepted words are announced in the language's topic as an informational message (no buttons) with a link to the Wiktionary entry.
- A daily report per language topic, sent at 00:00 Asia/Tashkent, lists the day's auto-accepted, editor-accepted and rejected words plus the count still pending. Quiet days send nothing.
- `POST /suggestions` no longer calls Telegram inline; it stores the suggestion for background review and responds exactly as before.
- Notifications become reliable: a suggestion counts as delivered only once Telegram confirms the message, so outages delay notifications instead of dropping them.
- Bot fixes: a non-editor or malformed tap no longer wipes the decision buttons; a decided message keeps its word/language/author; `getUpdates` errors back off instead of hot-looping; concurrent decisions on one suggestion apply once.
- Kill switch `WORD_LOOKUP_ENABLED=false` routes every suggestion to manual review.
- Spec correction: the wrong-length validation scenario is aligned with the implemented character bound.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `word-suggestions`: adds automatic dictionary verification, auto-accept announcements and a daily per-language report; editor review now covers only suggestions that were not auto-accepted, delivery is confirmed and retried, and non-editor taps leave messages intact; acceptance into the pack applies to automatic acceptance too; the length validation scenario is corrected.

## Impact

- **Backend** (`:backend`) only: Wiktionary lookup (JDK HTTP client, no new dependency; outbound HTTPS to `en.wiktionary.org`), a background review worker, a daily report scheduler, `TelegramBot` (confirmed sends, new message kinds, callback and polling fixes), `SuggestionServerService` (queue, atomic decide, daily summary), `SuggestionRoutes` (no inline notify), `Application` wiring.
- **Schema**: nullable `review_state`, `lookup_attempts`, `decided_via`, `auto_form` on `word_suggestions`; new `suggestion_reports` table. Additive, applied by the existing startup migration.
- **Config**: optional `WORD_LOOKUP_ENABLED` (default on) in `.env.example`. Existing `TELEGRAM_*` variables unchanged.
- **No** `:sharedData` or `:sharedUI` changes; the API contract and response are unchanged, so no client release is needed.
- **Tests**: backend unit and integration tests; no screenshot golden impact.
