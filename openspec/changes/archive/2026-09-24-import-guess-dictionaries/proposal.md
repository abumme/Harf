## Why

The guess dictionaries are tiny — en 662, ru 151, kk 35, uz-latn 27 and uz-cyrl 25 five-tile words — so players constantly hit "not in word list" for ordinary words, and the suggestion flow can only grow them one word at a time. Wiktionary's machine-readable extract (kaikki.org) covers all five launch languages with full inflection tables and the same part-of-speech and usage labels the auto-accept check already relies on; a measured pass over the Kazakh and Uzbek extracts yields about 2,800 kk and 1,800 uz-latn five-tile words.

## What Changes

- A repeatable word-list build turns kaikki.org extracts into per-language guess dictionaries: dictionary forms and their inflected forms, normalized and tokenized with the app's own rules, limited to the board lengths the puzzles use (5 today), excluding proper nouns, abbreviations, misspellings, words that are only vulgar or offensive, and blocklisted words. Output replaces the existing `*_guess.txt` files and is reviewed as a diff; current guesses and all answers are always kept.
- Uzbek Cyrillic guesses are generated from the Uzbek Latin list by transliteration and merged with Wiktionary's own Cyrillic entries, since Wiktionary has fewer than 50 Cyrillic five-tile Uzbek words.
- At startup the backend merges the deployed guess dictionaries into the stored server packs: it adds missing words, keeps every word already stored (including editor-accepted and auto-accepted ones), advances the version once, and leaves answers and schedules untouched. Clients pick the words up through the existing pack sync.
- The same files ship as the app's bundled dictionaries; a check keeps the backend and bundled copies identical.
- The word lists credit Wiktionary under its CC BY-SA license in the public offer texts and a repository notice.
- Not in this change: daily answers. Adding answers rewrites the schedule and needs its own change.

## Capabilities

### New Capabilities
<!-- none: the build tool is an implementation detail; its observable effect is dictionary content, specified under word-packs -->

### Modified Capabilities
- `word-packs`: adds requirements for what a guess dictionary contains (inflected forms in, proper nouns / abbreviations / misspellings / offensive words out, comparable Uzbek coverage in both scripts) and for crediting dictionary sources.
- `word-pack-distribution`: adds the requirement that deployed guess dictionaries reach existing server packs append-only, with one version advance and no schedule change.

## Impact

- **New build tool**: `:tools:wordlists` JVM module (included with the client modules, so `-PbackendOnly` builds skip it) reusing `Tokenizer`, `Normalizer` and `LaunchLanguages` from `:sharedUI`. Reads locally downloaded kaikki.org JSONL dumps that are never committed (en 3.0 GB, ru 0.9 GB, kk 36 MB, uz 26 MB).
- **Resources**: `backend/src/main/resources/wordpacks/*_guess.txt` and `sharedUI/src/commonMain/composeResources/files/*_guess.txt` regenerated; answers files and `UzbekDailyWords` unchanged.
- **Backend**: `WordPackServerService` gains an append-only guess merge, called from `main()` after `seed()`. No schema change.
- **Client**: no code change expected; bundled and cached packs grow, verified on Android, iOS, desktop and web.
- **Docs**: Wiktionary attribution in `docs/legal/offer-*.md` and a new `THIRD_PARTY_NOTICES.md`.
