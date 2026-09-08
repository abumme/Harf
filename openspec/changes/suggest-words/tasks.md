## 1. Shared DTOs (:sharedData)

- [x] 1.1 Add `SuggestWordRequest{lang, word}` and a response DTO (e.g. `SuggestWordResponse{status}`) in a new `data/suggestion/` package; add `ApiRoutes.SUGGESTIONS`; verify `:sharedData` compiles.
- [x] 1.2 Add a `SuggestionService` interface (`suspend fun suggest(token, request): ApiResult<SuggestWordResponse>`); verify compiles.
- [x] 1.3 Serialization round-trip test for the new DTOs; run `./gradlew :sharedData:jvmTest` green.

## 2. Backend: table & validation (:backend)

- [x] 2.1 Add `word_suggestions` table in `DatabaseTables.kt` (`id`, `lang`, `word`, `suggestedBy` FK `users` ON DELETE SET NULL, `status`, `createdAt`, `decidedBy` nullable, `decidedAt` nullable); register it in `DatabaseFactory.init` migration list; verify `DatabaseSchemaTest` passes.
- [x] 2.2 `SuggestionServerService.suggest(userId, lang, word)`: validate in order — length == tile count for lang, offensive blocklist, gibberish heuristics (≥3 same-in-a-row, <3 distinct graphemes, no vowel where applicable), already-in-pack, duplicate-pending collapse (idempotent success), per-user daily cap — then INSERT PENDING with author. Return a typed outcome.
- [x] 2.3 Unit-test the validation matrix (each reject reason + happy path + dup collapse + cap); run green.

## 3. Backend: Telegram bot (:backend)

- [x] 3.1 `TelegramBot` using a raw Ktor `HttpClient` to `api.telegram.org`: `sendMessage` (with inline accept/reject keyboard encoding suggestionId), `answerCallbackQuery`, `getUpdates` long-poll. Blank token ⇒ no-op sender + no poll loop (store-only).
- [x] 3.2 Config wiring: read `TELEGRAM_BOT_TOKEN`, `TELEGRAM_EDITOR_CHAT_ID`, `TELEGRAM_EDITOR_IDS` (comma-separated) in `Application`; start the supervised poll loop only when the token is set.
- [x] 3.3 On `suggest` success, post the pending suggestion (word + lang) to the editor chat when configured.
- [x] 3.4 Callback handling: authorize `from.id ∈ editor ids`; act only when status is PENDING; on accept call `WordPackServerService.addGuess` and mark ACCEPTED (record `decidedBy`/`decidedAt`); on reject mark REJECTED; edit the message + `answerCallbackQuery`; already-decided → answer without change.
- [x] 3.5 `WordPackServerService.addGuess(lang, word)`: append distinct to `guesses`, bump integer `version`, update `updatedAt`; leave `answers`/`schedule`/`effectiveFrom` unchanged.
- [x] 3.6 Tests: accept path adds word to pack guesses and bumps version; non-editor callback is ignored; reject leaves pack unchanged; already-decided is a no-op. Use a fake/stub Telegram sender. Run `./gradlew :backend:test` green.

## 4. Backend: route (:backend)

- [x] 4.1 `POST /suggestions` in a new `SuggestionRoutes.kt` behind JWT auth: decode `SuggestWordRequest`, resolve userId from token, call the service, map outcome to HTTP (202 accepted / 400 invalid / 401 unauthorized / 429 over cap); wire into `Application` module.
- [x] 4.2 Integration test: authenticated valid suggestion → stored PENDING + 202; unauthenticated → 401; store-only path works with Telegram unconfigured. Run green.

## 5. Client (:sharedUI)

- [x] 5.1 `KtorSuggestionService` implementing `SuggestionService` (POST with Bearer, reuse the session token flow); register in DI.
- [x] 5.2 In `GameScreen`, keep the just-rejected candidate word on `GameEvent.InvalidGuess` and show a "suggest to add" action beside the feedback (not shown for the too-short `Incomplete` case); on tap send `{activeLang, word}` and show a "sent for review" message.
- [x] 5.3 Add string resources (en/ru/uz): action label, sent-for-review, send-failed.
- [x] 5.4 Client test: invoking the action calls the service with the active language and rejected word; run `./gradlew :sharedUI:jvmTest` green. Record Roborazzi goldens only if the game screen layout visibly changed.

## 6. Verification

- [x] 6.1 Backend suite green: `./gradlew :backend:test`.
- [x] 6.2 Shared + client tests green: `./gradlew :sharedData:jvmTest :sharedUI:jvmTest`.
- [x] 6.3 Compile all targets (JVM → Android → Wasm/JS → iOS as available).
- [x] 6.4 `openspec validate suggest-words --strict` passes.
