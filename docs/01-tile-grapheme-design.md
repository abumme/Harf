# Thread 1 — The Tile/Grapheme Problem

**Date:** 2026-08-19 · **Updated:** 2026-08-20 (launch set now uz/ru/en/kk) · **Status:** exploration deep-dive · **Parent:** [harf-exploration.md](harf-exploration.md)

This is the highest-leverage design decision in Harf. Wordle's entire mechanic silently assumes **one letter = one key = one tile = one emoji**. None of our launch languages fully agree, and the decision we make here cascades into the game engine, the keyboard, the share grid, the content pipeline, and the Uzbek script duality. It is nearly impossible to change after launch without invalidating streaks, stats, and word lists.

---

## 1. The alphabets, honestly inventoried

| Language | Script | Letters | Digraphs / special | Verdict |
|----------|--------|---------|--------------------|---------|
| Uzbek | Latin (official 1995) | 29 + tutuq (ʼ) | `oʻ gʻ sh ch ng` are single letters written as 2 chars | **The hard case** |
| Uzbek | Cyrillic (still widely used) | 35 | none — 1 char = 1 letter | Clean alone; pairing with Latin is the issue |
| Kazakh | Cyrillic | **42** (33 Russian + ә ғ қ ң ө ұ ү һ і) | none | Keyboard layout problem, not tile problem |
| Russian | Cyrillic | 33 | none — `ё` plays as `е` (the convention every RU clone uses) | Clean |
| English | Latin | 26 | none | Clean; the crowded market is a positioning problem, not a tile problem |

**Key observation: the digraph problem is exclusively an Uzbek Latin problem.** Every other launch alphabet is one-character-per-letter. That means we don't need a general digraph theory — we need a general **grapheme** abstraction that happens to be trivial (grapheme = char) for 4 of the 5 script/language pairs.

## 2. The core decision: tile = grapheme

**A tile is one linguistic letter (grapheme), not one Unicode character.**

- *shahar* (city) = `sh · a · h · a · r` → **5 tiles**, not 6
- *oʻzbek* = `oʻ · z · b · e · k` → **5 tiles**
- *singil* (younger sister) = `s · i · ng · i · l` → **5 tiles**

Precedent: Welsh Wordle clones treat `ch, dd, ff, ng, ll, ph, rh, th` as single tiles; Irish and Hungarian clones do the same for their digraphs. Native speakers *think* in these units — an Uzbek speaker counts *shahar* as a 5-letter word because `sh` is one letter of their alphabet.

**Why this is tractable:** Wordle-style games ship a **custom on-screen keyboard** anyway (needed for the color feedback on keys). Digraphs get their own dedicated keys — `oʻ`, `gʻ`, `sh`, `ch`, `ng` are first-class keys, so the player never types `s` then `h`. Input ambiguity disappears entirely; there is no "did they mean s+h or sh" parsing problem, ever.

### What tile-=-grapheme cascades into

```
                    ┌───────────────────┐
                    │  tile = grapheme  │
                    └─────────┬─────────┘
        ┌───────────┬─────────┼──────────┬─────────────┐
        ▼           ▼         ▼          ▼             ▼
   word length   keyboard   emoji     scoring      content
   = grapheme    has digr.  grid: 1   (dup rules   pipeline
   count, not    keys       emoji per at grapheme  tokenizes
   char count               tile      level)       to graphemes
```

- **Word length** — "5-letter word" means 5 graphemes. The answer list and guess dictionary are bucketed by grapheme count.
- **Scoring** — green/yellow/gray computed per grapheme. Duplicate handling (Wordle's classic yellow-count rule) operates on graphemes: `sh` in the guess matches `sh` in the answer, never `s`+`h`.
- **Emoji grid** — one emoji per tile. A 5-tile Uzbek word makes a 5-column grid even if the word is 7 characters. This keeps share cards visually identical across all languages — important for the recognizable-format virality.
- **Content pipeline** — corpus words must be decomposed into graphemes with a longest-match tokenizer (`ng` before `n`, `sh` before `s`, `ch` before `c`, `oʻ` before `o`, `gʻ` before `g`). Ambiguity exists in rare words where `n+g` are separate letters across a morpheme boundary — the pipeline needs a human-reviewable exception list rather than trusting the tokenizer blindly.

## 3. The tutuq belgisi (ʼ) question — Uzbek Latin

Words like *sanʼat* (art), *maʼno* (meaning) contain the tutuq (glottal-stop apostrophe). Options:

| Option | Consequence |
|--------|-------------|
| ʼ is its own tile | Weird UX — a tile that's "not a letter"; keyboard needs a key for it |
| ʼ attaches to preceding tile (`aʼ`) | Inflates grapheme inventory; scoring gets confusing |
| **Exclude tutuq words from answers & guesses at launch** ✅ | Zero engineering; shrinks dictionary slightly; nobody misses them in v1 |

**Recommendation: exclude at launch.** Revisit post-Shipaton if players complain (they won't — most clones do this).

Related: **apostrophe normalization is a content-pipeline problem only.** In the wild, `oʻ` appears as `o'`, `o`", `oʼ`, `o`` (typewriter apostrophe, modifier letter, backtick…). Because input goes through our keyboard, gameplay never sees raw text — but the corpus ingestion must normalize all variants to one canonical codepoint (recommend U+02BB ʻ, the official form) before tokenizing.

Also worth a decision: the **2021 spelling-reform proposals** (`oʻ`→`ō`, `sh`→`ş` etc.) were never fully adopted. Ship the 1995 orthography (what people actually type and what school teaches); the language-config-as-data architecture (below) means a future reform is a content update, not an app update.

## 4. The Uzbek script duality — the sharpest open question

Uzbekistan genuinely uses both scripts: younger/official = Latin, older generation + Russia-diaspora + a lot of Telegram content = Cyrillic. Three options:

### Option A — Latin only at launch
Simplest. But it silently excludes the Cyrillic-native audience in the flagship market, and "finally a word game in OUR language" loses force if half the market feels it's not quite *their* script.

### Option B — Two parallel puzzles (Latin word ≠ Cyrillic word)
Two independent daily words, two answer lists. Doubles Uzbek content burden, splits leaderboards, and a bilingual family can't banter about "today's word" — kills the shared-experience virality inside exactly the groups we're targeting.

### Option C — One lexeme, two renderings ✅ (recommended)
The daily word is **the same word**, stored with a per-script grapheme decomposition. Player picks their script (switchable); board, keyboard, and share grid render in that script.

The transliteration is *almost* 1:1 at grapheme level — `sh↔ш, ch↔ч, oʻ↔ў, gʻ↔ғ, h↔ҳ, x↔х, j↔ж` — with a known set of breakers:

| Latin | Cyrillic | Problem |
|-------|----------|---------|
| `ng` | `нг` | 1 Latin grapheme → 2 Cyrillic letters (tile counts diverge) |
| `yo, yu, ya` | `ё, ю, я` | 2 Latin graphemes → 1 Cyrillic letter |
| `ts` | `ц` | loanwords only |
| `e` | `е/э` | position-dependent |
| ʼ | ъ | excluded anyway (see §3) |

**The saving insight: league scoring is script-agnostic.** Score = number of guesses, not tiles. A Latin player solving a 5-tile board and a Cyrillic relative solving the same word on a 6-letter board are still playing *the same word* and compete fairly on guesses. Tile-count divergence is cosmetic, not competitive.

**Curation policy:** for the answer list (the ~365 curated daily words), simply **prefer words whose tile count matches in both scripts** — the breaker set is small enough that this barely constrains selection. The guess dictionary doesn't care (each script validates against its own decomposition).

## 5. Keyboards, per language

Custom Compose on-screen keyboard, layout defined as data per language:

- **Uzbek Latin** — QWERTY base minus unused letters (`c` and `w` don't exist standalone in Uzbek!), plus dedicated `oʻ gʻ sh ch ng` keys. ~29 keys, comfortably 3 rows + action row.
- **Uzbek Cyrillic** — ЙЦУКЕН base (35 keys incl. ў қ ғ ҳ); standard Uzbek phone layout exists to copy.
- **Kazakh** — the 42-letter monster. Mitigation: **restrict answers and guesses to native-word alphabet** — в, ё, ф, ц, ч, щ, ъ, ь, э, ю, я occur almost exclusively in Russian loanwords. Cutting them yields ~31 keys and a sane 3-row layout, and native-words-only is the *right* content policy for a national-pride game anyway.
- **Russian** — ЙЦУКЕН, 33 letters with `ё` merged into `е` on board and keyboard (standard RU-clone convention), so 32 keys.
- **English** — plain QWERTY, 26 keys. The trivial case.

Keyboard = data (rows of key definitions), not per-language code. Key feedback colors (the "used letters" state) keyed by grapheme.

## 6. Word length flexibility

Wordle's 5 is not sacred. Uzbek/Kazakh are agglutinative — *root* words skew short; the pipeline must verify there are ≥365 high-quality common words per language at the chosen length. Engine decision: **support 4–7 tile boards from day one, length configured per language per day.** This costs almost nothing now (board is already dynamic across scripts per §4) and buys: content flexibility if a language runs dry at 5, and future "hard mode Saturdays" with 6-tile words as a retention lever.

## 7. The data model this implies

```
LanguageConfig (shipped as data, not code)
├─ id: "uz-latn" | "uz-cyrl" | "kk" | "ru" | "en"
├─ graphemeInventory: ["a","b",…,"oʻ","gʻ","sh","ch","ng"]
├─ tokenizer: longest-match rules + exception list
├─ keyboardLayout: rows of keys
├─ display: name, flag, script label
└─ collation: normalization map (content pipeline only)

Word
├─ lexemeId (stable across scripts — Uzbek pairs share it)
├─ perScript: { script → grapheme list }
└─ flags: answerEligible, difficulty, reviewStatus

DailyPuzzle
├─ language, date, lexemeId, tileCount(perScript)
```

**The strategic payoff:** because alphabet, tokenizer, and keyboard are all data, **adding a language post-launch is a content drop + config file, not an engineering project.** Azerbaijani and Tajik — dropped from launch on 2026-08-20, both clean one-char alphabets already analyzed here — are the ready-made first expansions; Bahasa/Filipino follow the same contract. That's the platform moat from the concept doc, made concrete. (Hindi/Devanagari would be the first to break assumptions — abugida, matras — so it stays explicitly out of the v1 engine contract; the Latin/Cyrillic alphabetic world is the v1 contract.)

## 8. Decisions to carry into a design.md when we scaffold the change

1. Tile = grapheme; digraphs are first-class keys ✅ (high confidence)
2. Tutuq words excluded at launch ✅ (high confidence)
3. Uzbek = one lexeme, two script renderings; scoring by guess count ✅ (medium-high — the recommendation of §4)
4. Kazakh restricted to native-word alphabet ✅ (medium — needs a native speaker's sanity check)
5. Board length 4–7 configurable, default 5 (high confidence)
6. 1995 Uzbek orthography, U+02BB canonical apostrophe (high confidence)
7. Launch languages: uz (both scripts), ru, en, kk — decided 2026-08-20; az/tg deferred to expansion ✅
8. Russian `ё` = `е` on board and keyboard ✅ (high confidence — universal RU convention)

Open items needing a native reviewer, not more analysis: Kazakh alphabet restriction, the `ng`-boundary exception list, per-language answer-list depth at 5 tiles.
