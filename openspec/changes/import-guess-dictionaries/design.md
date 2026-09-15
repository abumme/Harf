## Context

See proposal.md — Why. Current state that shapes the approach:

- **Two list kinds.** Guesses only validate a typed word; answers feed the daily schedule. `WordPackSchedule.build` shuffles the whole answers list with a fixed seed, so editing an answers file reorders every day and splits offline clients from synced ones. This change touches guesses only.
- **Seeding is insert-if-absent.** `WordPackServerService.seed()` writes version 1 only for a language without a stored pack. Production already has version 1, so editing resource files alone never reaches it. `addGuess` is the only in-place growth path.
- **Two identical copies.** `backend/src/main/resources/wordpacks/*_guess.txt` and `sharedUI/src/commonMain/composeResources/files/*_guess.txt` are byte-identical today. Offline clients play from the bundled copy until they sync.
- **Every current puzzle is 5 tiles.** All answers and guesses in all five languages tokenize to 5 graphemes (board length supports 4–7).
- **Client checks.** `WordPackTest.every_pack_passes_integrity_validation` fails the build if a bundled entry does not tokenize or is blocklisted. For a fetched pack, untokenizable guesses are silently dropped, while any bad answer rejects the whole pack.
- **Tokenization is pure Kotlin.** `Tokenizer` depends only on `LanguageConfig`; `Normalizer` and `LaunchLanguages` have no UI dependencies — but they live in `:sharedUI`.
- **kaikki.org format** (probed 2026-09-14): one JSON object per line with `word`, `pos` (proper nouns are `name`), `forms[{form, tags}]` holding the full inflection table, and `tags` on the entry and its `senses` (`abbreviation`, `vulgar`, `derogatory`, `alt-of`, …). Russian forms carry stress marks (`соба́ка`); `romanization`, `table-tags` and `inflection-template` rows are not words.
- **Measured yield** (5 tiles, app inventories, proper nouns / abbreviations / vulgar excluded): kk 1,495 lemmas → 2,767 with forms; uz-latn 657 → 1,789; uz-cyrl only 45 → 49. English (3.0 GB) and Russian (0.9 GB) were not measured.

## Goals / Non-Goals

**Goals:**
- Large, reproducible guess dictionaries for all five languages, reviewed as diffs.
- Reach production without schema changes, manual SQL, or an admin surface.
- Keep auto-accepted and editor-accepted words.

**Non-Goals:**
- Answers and schedules (a later change: rewrite the schedule from a future effective date).
- Board lengths other than those the puzzles use.
- Removing words from server packs (append-only; removals stay manual).
- Moving the tokenizer out of `:sharedUI`.
- Frequency ranking or curation beyond the exclusion rules.

## Decisions

### Source: kaikki.org (Wiktextract) for every language
One parser and one rule set for all five languages, matching the auto-accept check's source and exclusions, with inflection tables included. Dumps are downloaded by hand into a gitignored cache and never committed; the download date is written into each output header for reproducibility.

*Alternatives*: OpenCorpora (ru) and SCOWL (en) have excellent coverage but need separate parsers and rules — possible later supplements. Hunspell dictionaries need affix expansion and have mixed licenses. Apertium lexicons are GPL, risky for a paid app. LLM-generated lists are not authoritative.

### Builder: a Kotlin JVM module reusing the app's tokenizer
`:tools:wordlists` (a `kotlin("jvm")` application) depends on `:sharedUI`'s JVM variant and calls `Normalizer`, `Tokenizer` and `LaunchLanguages` directly, so generated entries tokenize exactly as at runtime — including exceptions like `ustunga` — and `WordPackTest` passes by construction. It is included in `settings.gradle.kts` inside the client-module block, so `-PbackendOnly` builds and the Docker image never see it. It streams JSONL line by line with kotlinx.serialization, so the 3 GB English dump needs no large heap.

*Alternatives*: a Python script would re-implement grapheme rules and drift from the app. Extracting the tokenizer to `:sharedData` is the right long-term home (the backend could then validate suggestions by graphemes) but is a refactor unrelated to this change. *Fallback* if a JVM module cannot consume the multiplatform module's JVM variant: register a `JavaExec` task over `:sharedUI`'s JVM compilation instead.

### Selection rules
For each entry in a language dump:
1. Skip entries whose `pos` is not a word class: `name`, `character`, `prefix`, `suffix`, `infix`, `interfix`, `phrase`, `proverb`, `symbol`, `punct`, `abbrev`, `romanization`, `combining_form`.
2. Skip entries where the entry or any sense is tagged `abbreviation`, `acronym`, `initialism` or `misspelling`. Skip entries tagged `vulgar`, `offensive`, `derogatory` or `slur` on the entry itself or on **every** sense: an ordinary word with one rude figurative sense stays (`собака` "dog" has a derogatory sense, Kazakh `соғу` "to hit" a vulgar one). The stricter any-sense rule cost little in numbers but hit common words (kk: 16 words, 0.6%; the Russian sample lost `собака`). This is more permissive than auto-accept, where any blocking category sends a word to editors; in a guess list such a word can only be typed, and the blocklists still remove specific offensive words. (Decided during implementation, 2026-09-14.)
3. Candidates are the entry word plus every form not tagged `romanization`, `table-tags`, `inflection-template` or `class`, keeping only spellings already written entirely in lowercase. Wiktionary capitalizes proper nouns and acronyms but doesn't always tag them: English `MTOCs` is its own untagged plural entry of the initialism `MTOC`, and lowercasing it produced the non-word `mtocs` (found in the first spot-check sample, 2026-09-14).
4. Normalize: strip only stress marks (U+0301, U+0300) so й, ё and ў survive; NFC; lowercase; then the language's `Normalizer` rules (ru ё→е; Uzbek apostrophe variants → ʻ).
5. Keep a candidate only if it tokenizes under the language config at a board length listed for that language (`5` for all today). Russian spellings ending in `ъ` are dropped too: that is pre-1918 orthography, and Wiktionary keeps such spellings as untagged "dated" alternative entries (`кормъ`, found in the first spot-check sample). A final hard sign never occurs in modern Russian, so the rule is exact, whereas dropping all "dated" alternative spellings would also remove legitimate old English words. English spellings without a vowel (a, e, i, o, u, y) are dropped: lowercase abbreviation plurals and sound effects (`bldgs`, `blvds`, `brrrm`) slip past the tags, and the rule matches the backend's existing "no vowel" check on suggestions; the handful of real vowelless loans (`crwth`) are an accepted loss (decided 2026-09-14). A scan for guesses derived only from abbreviation entries was tried and rejected: common headwords such as `do` and `go` also have abbreviation entries, so it flagged ordinary archaic forms (`didst`, `goeth`) instead. Remaining review rejections (`pbars`, `insts`, `shitz`) go to the app blocklist.
6. Drop blocklisted words (`<lang>_block.txt`).
7. Output = kept candidates ∪ current guess file ∪ all answers (and, for Uzbek, `UzbekDailyWords` in the matching script) — nothing previously valid disappears. Sorted and deduplicated for stable diffs, with a `#` header naming the source, dump date and license (both loaders already skip `#` lines).

### Uzbek Cyrillic by transliteration
uz-cyrl = transliterate(uz-latn candidates) ∪ Wiktionary Cyrillic entries, each re-tokenized with the uz-cyrl config, so tile counts are recomputed (`yangi` is 4 Latin tiles and `янги` 4 Cyrillic ones; `yer` → `ер` changes length). Rules follow the official correspondence: `sh`→ш, `ch`→ч, `oʻ`→ў, `gʻ`→ғ, `yo`→ё, `yu`→ю, `ya`→я, word-initial `ye`→е, word-initial `e`→э, `x`→х, `h`→ҳ, `q`→қ, remaining letters one-to-one. Russian loanwords whose Cyrillic spelling keeps letters Latin does not encode (ц, ь, щ) come out misspelled; a misspelled guess is harmless, and native spot-checks feed the blocklist.

### Server: append-only merge at startup
`WordPackServerService.mergeGuesses()` runs in `main()` right after `seed()` and before the background loops start, so it cannot race `addGuess`. Per language, it reads the resource guesses (plus answers), computes stored ∪ resource using `addGuess`'s case-insensitive dedupe, and, when anything is new, writes the guesses and `version + 1` in one update. It is idempotent: a restart adds nothing and changes no version. It never removes words and never touches answers, schedule or `effectiveFrom`.

*Alternatives*: regenerating the pack as a new seed version would reset the schedule and drop accepted words. An admin import endpoint adds an authenticated surface for a rare operation. Manual SQL over JSON columns is unreviewable.

### One list, two copies
The builder writes each file to both the backend and bundled locations, and a backend test asserts each language's two copies are identical (it reads the client file by repository path and skips when that tree is absent). *Alternative*: the backend's `processResources` copying from `sharedUI/` would couple the backend build and Docker context to the client tree.

### Attribution
Wiktionary text is CC BY-SA. The public offer in every locale (`docs/legal/offer-{en,kk,ru,tr,uz}.md`) gains a short credit to Wiktionary contributors with the license name and link; `THIRD_PARTY_NOTICES.md` records the source, extraction tool (Wiktextract via kaikki.org) and license; each generated file carries the same in its header. Final legal wording is the owner's to confirm.

## Risks / Trade-offs

- [English dump is huge and includes rare or archaic words] → guesses are deliberately permissive and answers are untouched; the builder reports per-language counts, and a length or frequency filter can be added if the English pack proves too large.
- [Bigger packs on clients] → every version bump re-downloads a language's full pack and KSafe persists it (KSafe size limits could not be checked: context7 was unavailable) → verify fetch, cache and offline restart of the largest pack on Android, iOS, desktop and web before merging; if a platform fails, pause and revise (for example, store packs outside KSafe).
- [Words with a rude secondary sense are accepted as guesses] → only when some sense is ordinary; genuinely offensive words belong on the blocklists, which every build applies.
- [Transliterated loanwords misspelled in Uzbek Cyrillic] → harmless as guesses; spot-checks and the blocklist remove the worst.
- [kaikki.org data drifts between downloads] → dump date in headers; regeneration keeps all previous words, so drift only adds.
- [Share-alike obligations] → attribution in terms, notice and file headers; owner confirms the wording.
- [Kazakh and Uzbek quality without a native reviewer] → the merge ships only after a recorded spot-check; the lists can wait while en/ru ship.

## Migration Plan

1. Download dumps, build all five lists, run the client and backend tests.
2. Open a PR with per-language counts, pack sizes and spot-check notes; kk/uz spot-checked by a native speaker.
3. Merge and deploy: the startup merge bumps each changed language's version once; clients pull on next sync; the next app release bundles the same files.
4. Rollback: the image can roll back freely (no schema change). Words already merged stay in server packs, because they are append-only; removing a bad word needs a follow-up blocklist entry plus a manual pack edit.

## Open Questions

- Who spot-checks Kazakh and Uzbek? This gates when those two lists ship, not the approach.
- Whether English needs a size filter — decided by the measured pack size.
