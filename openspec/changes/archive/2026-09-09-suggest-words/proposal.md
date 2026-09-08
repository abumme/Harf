## Why

The offline vocab is small, so players regularly type real words the dictionary rejects. Today that dead-ends at a "not in word list" toast. Letting a player suggest the rejected word — routed to word editors over a Telegram bot for one-tap accept/reject, then distributed to everyone via the existing word-pack channel — grows the dictionary from real play with almost no editor overhead.

## What Changes

- When a full-length guess is rejected as not-in-dictionary, the client shows a "Suggest to add" action next to the existing feedback. Tapping it sends the word to the backend and reports "sent for review".
- New authenticated endpoint accepts a suggestion `{lang, word}` from any session (including anonymous), validates it, and stores it as PENDING associated with its author.
- The backend runs a Telegram bot (long-polling) that posts each pending suggestion to an editors' chat with inline **Accept** / **Reject** buttons. Only allowlisted editor Telegram IDs may act.
- On **Accept**, the word is appended to that language's word-pack `guesses` and the pack `version` is bumped, so clients pick it up on their next pack pull. On **Reject**, the suggestion is marked rejected.
- Suggestions are validated before reaching editors: correct puzzle length, offensive-word blocklist (reused), cheap gibberish heuristics, duplicate-pending collapse, and a per-user daily cap.
- The author↔word association (`suggestedBy`) is stored permanently (SET NULL on account deletion) to enable future contribution stats and accept notifications; neither is built now.

## Capabilities

### New Capabilities
- `word-suggestions`: players propose dictionary words from the game; editors review them over a Telegram bot; accepted words enter the distributed word pack. Covers submission, validation/anti-spam, editor review + authorization, acceptance→pack update, and author association.

### Modified Capabilities
<!-- none — word-pack-distribution is unaffected: an accepted word only bumps `version`, and the existing pull path carries it. Past days stay immutable because only `guesses` change, not `schedule`/`answers`. -->

## Impact

- **Shared DTOs** (`:sharedData`): `SuggestWordRequest{lang, word}` + response, `ApiRoutes.SUGGESTIONS`, a `SuggestionService` interface.
- **Backend** (`:backend`): new `word_suggestions` table (FK `users` SET NULL); `POST /suggestions` route + `SuggestionServerService` (validation, store, notify); `TelegramBot` (raw Ktor `HttpClient` → `api.telegram.org`: `getUpdates` long-poll loop, `sendMessage`, `answerCallbackQuery`); `WordPackServerService.addGuess(lang, word)` (append distinct + `version++`); config `TELEGRAM_BOT_TOKEN` / `TELEGRAM_EDITOR_CHAT_ID` / `TELEGRAM_EDITOR_IDS` (blank token ⇒ store-only, no crash).
- **Client** (`:sharedUI`): a "Suggest to add" action driven by `GameEvent.InvalidGuess` in `GameScreen`, holding the just-rejected candidate; `KtorSuggestionService`; new string resources (en/ru/uz).
- **Tests**: suggestion validation (length/blocklist/gibberish/dup/cap), accept→pack `guesses`+`version`, editor-auth on callback, DTO serialization, client send.
- No breaking changes — all additive; Telegram absent ⇒ feature degrades to store-only.
