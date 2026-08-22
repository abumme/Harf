## Context

See proposal.md — Why, and `docs/01-tile-grapheme-design.md` for the full analysis. Pure Kotlin logic in `sharedUI/commonMain`, consumed later by `harf-gameplay`. Depends on `harf-foundation` for DI. kotlinx-serialization is already in the catalog for parsing packs.

## Goals / Non-Goals

**Goals:**
- Correct, well-tested grapheme tokenization + scoring for all five launch language/script pairs.
- Language as data so expansion is a content drop.
- Offline packs with a build/test-time integrity check.

**Non-Goals:**
- Rendering (board/keyboard) — `harf-gameplay`.
- Daily-word selection, rollover, streaks — later proposals.
- Server-delivered/obfuscated packs, anti-cheat — post-launch.

## Decisions

**Grapheme as `List<String>` with a value-typed wrapper.**
A tokenized word is an ordered list of grapheme tokens (each a normalized string). A thin `Word`/`Graphemes` wrapper carries the language id. Avoids a heavy per-grapheme class while keeping type intent. Alternative: `Char`-based — rejected, cannot represent digraphs.

**Tokenizer: greedy longest-match over a sorted grapheme set + exception map.**
Per language, build a match set ordered by descending length; scan left-to-right taking the longest grapheme that matches at each position. An exception map keyed by the raw word overrides the result for morpheme-boundary cases. Normalization (apostrophe variants → U+02BB, `ё`→`е`) runs before scanning. Alternative: regex/ICU segmentation — rejected as overkill and non-portable across KMP targets.

**Scoring: two-pass Wordle rule at grapheme level.**
Pass 1 marks correct positions and decrements a per-grapheme remaining-count multiset built from the answer. Pass 2 marks present only while remaining count > 0, else absent. Operates on grapheme lists so digraphs match as units. This is the canonical, well-understood algorithm.

**LanguageConfig: `@Serializable` data + a code-side registry.**
Config is data (inventory, tokenizer rules, exceptions, keyboard rows, display, script pairing). A `LanguageRegistry` (provided via Koin) loads configs and their packs. Uzbek is modeled as one lexeme with `perScript: Map<Script, Graphemes>`; the daily-puzzle layer later picks the script. Alternative: sealed classes per language — rejected, that is code-per-language, defeating the moat.

**Word packs: bundled resource files parsed lazily, validated in tests.**
Answers + guess dictionary per language stored as compose resources (`files/`), parsed on first access and cached. A `validatePack(config, pack)` runs in unit tests (and can gate CI) enforcing the integrity requirement. Guess lookup uses a `Set` of the canonical tokenized form for O(1) membership. First-pass content is generated; native review is a separate track (not code).

**Board length 4–7.**
Packs are bucketed by grapheme length; a puzzle requests a length. Default 5. Costs nothing now, enables hard-mode/variety later.

## Risks / Trade-offs

- **Tokenizer ambiguity (`ng` = one letter vs `n`+`g` across a boundary).** → Exception list + the pack validator surfaces mismatches; native review owns the list. Do not trust the tokenizer blindly on curated answers.
- **Generated first-pass word quality.** → Engine treats packs as data; a bad word is a content swap, not a code fix. Native review tracked outside this change.
- **Uzbek transliteration breakers (`ng↔нг`, `yo/yu/ya↔ё/ю/я`) diverge tile counts.** → Scoring is by guess count (script-agnostic); curation prefers tile-count-matching answers. Divergence is cosmetic, not competitive.
- **Kazakh native-alphabet restriction needs a speaker's check.** → Encoded as config data, adjustable without engine changes; flagged for native review.

## Open Questions

- Exact on-disk pack format (plain newline lists vs JSON with metadata) — parser is behind the registry, does not affect specs; decide at implementation.
