## Why

The tile engine is Harf's moat and the highest-leverage design decision (see `docs/01-tile-grapheme-design.md`). Wordle assumes one letter = one character; none of Harf's launch languages fully agree. A grapheme-based, data-driven engine is what lets `shahar` count as 5 tiles, digraphs be first-class, Uzbek render in two scripts as one lexeme, and new languages ship as content — not code. It is pure logic with no UI, so it must exist and be well-tested before the gameplay screen is built on top of it.

## What Changes

- Add a **grapheme model**: a tile is one linguistic letter, not one Unicode character.
- Add a **longest-match tokenizer** that decomposes a word string into graphemes per language (`ng` before `n`, `sh` before `s`, `oʻ` before `o`, etc.), with a human-reviewable exception list for morpheme-boundary cases.
- Add a **scoring function**: given a guess and an answer (as grapheme lists), produce per-tile feedback (correct / present / absent) using Wordle's duplicate-count rule, computed at grapheme granularity.
- Add **`LanguageConfig` as data**: grapheme inventory, tokenizer rules + exceptions, keyboard layout (rows of keys, digraphs as first-class keys), display metadata, and (Uzbek) script pairing. Adding a language is a config + word-pack drop, not code.
- Add **bundled word packs** for the launch set (uz-Latn, uz-Cyrl, ru, en, kk): an answer list and a guess dictionary per language, decomposed to graphemes, loaded from app resources (offline). A first-pass generated set; native review tracked separately.
- Encode launch decisions from doc 01: tutuq words excluded at launch; Uzbek 1995 orthography with U+02BB canonical apostrophe; Russian `ё` = `е`; Kazakh restricted to native-word alphabet; board length 4–7 configurable (default 5); Uzbek = one lexeme with per-script grapheme decomposition, scoring by guess count.

Non-goals: any UI (board/keyboard rendering is `harf-gameplay`); server-delivered packs, obfuscation, anti-cheat (all post-launch); daily-word selection/rollover (that is `harf-gameplay`/daily-puzzle).

## Capabilities

### New Capabilities
- `grapheme-tokenization`: decompose a word into ordered graphemes for a language via longest-match rules plus an exception list.
- `guess-scoring`: compute per-grapheme correct/present/absent feedback for a guess against an answer, honoring duplicate-letter rules.
- `language-config`: data-defined per-language configuration (inventory, tokenizer, keyboard layout, script pairing, display) enabling new languages without code changes.
- `word-packs`: bundled, offline answer lists and guess dictionaries per launch language, validated against their language config.

### Modified Capabilities
<!-- none -->

## Impact

- `sharedUI/src/commonMain/kotlin/uz/abumme/harfgame/`: new `engine/` (model, tokenizer, scorer), `lang/` (LanguageConfig + per-language definitions).
- `sharedUI/src/commonMain/composeResources/files/`: bundled word-pack resources (answers + guess dictionaries) per language.
- Depends on `harf-foundation` (DI to expose the engine/config registry). No new third-party dependencies; pure Kotlin + kotlinx-serialization for pack parsing.
