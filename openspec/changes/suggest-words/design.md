## Context

See proposal.md — Why. Relevant existing machinery:
- Invalid-guess detection already exists: `GameViewModel.submit` emits `GameEvent.InvalidGuess` when `pack.isValidGuess(current)` is false (current is full length). `GameScreen` (L136-151) shows a transient toast from it.
- Word packs are stored server-side per language in `WordPacksTable {answers, guesses, schedule, version}` and served by `WordPackServerService.getPack` via `WordPackRoutes` with an ETag = `version` and `If-None-Match` → 304. This is the distribution channel.
- Auth: every client has at least an anonymous session (Bearer token); routes authorize via JWT (see `AuthRoutes`/JWT plugin).
- An offensive-word blocklist already exists (commit "blocklist offensive words in word packs").

## Goals / Non-Goals

- **Goals**: capture a rejected word from play, route it to editors over Telegram for accept/reject, and distribute accepted words as valid guesses.
- **Non-Goals**: notifying the suggester of the decision; a contributor/stats UI; editing or deleting existing pack words; adding words to daily *answers*; a full Telegram admin surface beyond accept/reject; webhook transport.

## Decisions

- **Telegram transport: long-polling.** A background coroutine calls `getUpdates` with a long timeout and processes `callback_query` updates. No public callback URL, secret, or `setWebhook` needed — right for a few developer-editors and a self-hosted server. *Alternative rejected*: webhook — more efficient but needs a registered HTTPS endpoint and secret management for no benefit at this scale. Ceiling noted: a single poll loop per process; revisit if editor volume ever grows.

- **Telegram client = raw Ktor `HttpClient` to `api.telegram.org`.** Only three calls (`getUpdates`, `sendMessage`, `answerCallbackQuery`); a bot library is unjustified. *Alternative rejected*: a KMP/JVM Telegram SDK — a dependency for three HTTP calls.

- **Blank bot token ⇒ feature degrades to store-only**, mirroring the RevenueCat "blank key ⇒ Unavailable, never crash" pattern. Suggestions are still stored PENDING; no notification is sent; no poll loop starts. Keeps dev/test/CI runnable without secrets.

- **Accepted word → `guesses` only, `version++`.** `WordPackServerService.addGuess(lang, word)` appends the word (deduped via `distinct()`, matching `seed`'s existing pattern) and increments the integer `version`. `schedule`/`answers`/`effectiveFrom` are untouched, so past days stay immutable and clients converge on the next pull. *Alternative rejected*: also adding to answers — answers need curation (length, no spoilers) and would change future puzzles.

- **Author association is the `word_suggestions` row, kept permanently, `suggestedBy` FK `users` ON DELETE SET NULL.** The row survives accept/reject and account deletion (author cleared) so contribution history and a future accept-notification remain possible. *Alternative rejected*: CASCADE — loses contribution history.

- **Anti-spam is layered and cheap; editors are the real filter.** Order: length must equal the language's tile count → offensive blocklist → gibberish heuristics → already-in-pack → duplicate-pending collapse (idempotent success) → per-user daily cap. The gibberish check is heuristic only — the word is unknown *by definition*, so nothing can prove it real; the checks catch obvious junk (`ааа`, too few distinct graphemes, no vowel) and carry a `ponytail:` note with tunable thresholds.

- **Word form = joined graphemes, stored raw.** The client sends the raw candidate string; the server stores it raw and appends it raw to `guesses`, exactly as pack words are stored today; the client re-tokenizes on pull. No new normalization contract.

- **Decision idempotency.** A callback acts only when the suggestion is still PENDING; a second tap (double-tap or a second editor) answers "already decided" without changing the outcome.

## Risks / Trade-offs

- **Gibberish heuristics let plausible non-words through** → mitigated: that's the editors' job; heuristics only cut noise. Documented ceiling, not a promise.
- **Long-poll loop is a single background task** → if it dies, notifications stop while suggestions still store. Mitigation: run it supervised (restart on failure) and log; acceptable for the scale.
- **Anonymous suggesters can spam** → per-user daily cap + dup collapse + heuristics bound the blast radius; the cap is a tunable knob.
- **Telegram outbound blocked/misconfigured** → store-only degrade path means suggestions are never lost, just unreviewed until config is fixed.
- **Word count on a pack grows unbounded over time** → guesses are a Set client-side; negligible at expected volumes.

## Migration Plan

1. Add the `word_suggestions` table via the existing `MigrationUtils` schema-diff path (additive; FK SET NULL). No backfill.
2. Ship shared DTOs + backend endpoint + Telegram integration together; with no `TELEGRAM_BOT_TOKEN` the endpoint still works (store-only), so it can deploy before the bot is configured.
3. Configure `TELEGRAM_BOT_TOKEN` / `TELEGRAM_EDITOR_CHAT_ID` / `TELEGRAM_EDITOR_IDS` to enable review.
4. Ship the client action; older clients simply never show it.
5. Rollback: leave the table (unused) and unset the token to disable; nullable/additive, no cleanup.

## Open Questions

- Exact daily-cap number and gibberish thresholds — tunable at implementation without changing specs or approach.
