# Harf — Exploration Notes

**Date:** 2026-08-19 · **Updated:** 2026-08-20 (decision log added) · **Status:** exploration (no OpenSpec change created yet)
**Source:** RevenueCat Shipaton 2026 "Top 10 Market Breakers" analysis — Harf ranked #1 (Feasibility #1, Viral #2)

## The concept in one line

A daily word duel for languages Wordle forgot — Uzbek (Latin + Cyrillic), Russian, English, Kazakh at launch — with the emoji share grid rebuilt for Telegram, group leagues, and streaks friends can rescue for $0.49. Uzbek + Kazakh carry the "languages Wordle forgot" story; Russian and English widen the audience inside the app.

**Hard constraints:**
- Live in both stores by **September 30, 2026, 11:45 PM PDT**; feature freeze ~Sept 12
- Kotlin Multiplatform + Compose Multiplatform, ~95% commonMain target
- 2-person team, Windows dev machine, iOS via cloud CI only
- RevenueCat for all monetization; free-forever core, no hard paywall

## The core loop

```
                     THE HARF LOOP (2 min/day)
═══════════════════════════════════════════════════════════════

                ┌──────────────┐
                │  Daily word  │  one word per language per day
                │  (per lang)  │  ← content pipeline feeds this
                └──────┬───────┘
                       ▼
 ┌─────────┐    ┌──────────────┐    ┌──────────────────┐
 │ Streaks │◀───│    SOLVE     │───▶│  Emoji share grid │
 │  stats  │    │ (game board) │    │  + deep link      │
 └────┬────┘    └──────────────┘    └────────┬─────────┘
      │                                      ▼
      │         ┌──────────────┐    ┌──────────────────┐
      │         │ League table │◀───│  Telegram group   │
      │         │  updates     │    │  (the stadium)    │
      │         └──────┬───────┘    └────────┬─────────┘
      │                │                     │ tap-to-play
      ▼                ▼                     ▼
 ┌─────────────────────────────────────────────────┐
 │  MONETIZATION: streak gift $0.49 · themes ·     │
 │  lifetime $4.99 · league pass group-buy · gems  │
 └─────────────────────────────────────────────────┘
```

Everything in the diagram is well-understood engineering **except two things** — the tile/grapheme engine and the multilingual word lists. They are simultaneously the moat and the risk.

---

## Thread 1: The tile problem (highest-leverage design decision)

Wordle's entire mechanic assumes one letter = one key = one tile. None of the launch languages agree:

| Language | Script | The wrinkle |
|----------|--------|-------------|
| Uzbek | Latin **and** Cyrillic | `o'`, `g'`, `sh`, `ch`, `ng` — digraphs. Is *shahar* 6 chars or 5 tiles? |
| Uzbek | (again) | 2019/2021 spelling-reform instability — `o'` vs `oʻ` vs `ō` in the wild |
| Kazakh | Cyrillic (Latin transition looming) | 42-letter alphabet — the on-screen keyboard is a real layout problem |
| Russian | Cyrillic | clean 33 letters — convention: `ё` plays as `е` (standard in every RU clone) |
| English | Latin | no tile wrinkle; the wrinkle is market crowding — positioned for the home exam-prep audience |

**Working hypothesis:** tile = **grapheme**, not character. Digraph tiles like `sh` occupy one square and get their own key on a custom on-screen keyboard (precedent: Welsh and Irish Wordle clones). The custom on-screen keyboard — which Wordle needs anyway — is what makes this tractable.

**Why it must be settled early — the decision cascades into:**
- Word-length definition (what counts as a "5-tile word")
- The emoji share grid (one emoji per tile)
- Corpus tokenization in the content pipeline
- The Uzbek Latin↔Cyrillic duality: is the daily word *the same word* in both scripts, with two board lengths? Nearly impossible to change post-launch.

**Open questions:**
- Canonical script for Uzbek word identity (Latin canonical, Cyrillic rendered? or two parallel puzzles?)
- Which spelling convention for `o'`/`g'` (store one normalized form, accept all variants on input?)
- Daily-word rollover anchor (midnight Tashkent time? per-language timezone?)

---

## Thread 2: The calendar is meaner than the source doc assumes

The analysis was prepared August 1 assuming a 5-week window. As of today (Aug 19) the deadline hasn't moved:

```
Aug 19          Sept 12          Sept 16?         Sept 30
  │────────────────│────────────────│────────────────│
  │   ~3.5 weeks   │  freeze→polish │  review buffer │
  │   to build     │                │  (3–7 days,    │
  │                │                │   2–3 Apple    │
  ▼                ▼                ▼   rejections)  ▼
  TODAY         feature          must be          MUST BE
                freeze           submitted        LIVE
```

**Two gates want checking this week, before any feature code:**

1. **Google Play account age.** Personal accounts created after Nov 13, 2023 require 12 testers × 14 *continuous* days of closed testing before production. Counting backward from Sept 30, a stub AAB must be uploaded within days, not weeks.
2. **Green iOS cloud-CI build + TestFlight.** The source doc's own store-logistics research says week 1 — and week 1 is now. No physical iOS device on hand; CI is the only iOS path.

---

## Thread 3: MVP scoping — core vs. layers

```
CORE (can't ship without)          LAYERS (can slip past freeze)
─────────────────────────          ────────────────────────────
daily puzzle, 4 languages          community word submission
custom keyboard + tile engine      national/city leaderboards
streak + stats (local)             gem economy
share grid → Telegram card         seasonal cosmetic packs
deep link → install/play           league season pass group-buy
─── then, in order: ───            (webhook entitlements = real
group leagues (needs backend)       engineering, per source doc)
streak gifting ($0.49 headline)
```

**Key tension:** streak gifting is the headline HAMM mechanic *and* the first feature that forces server-side entitlements via RevenueCat webhooks. It sits exactly on the core/layer boundary — the monetization story judges will remember most is also the piece with the most hidden plumbing. If anything gets a dedicated engineering week, it's this.

---

## Thread 4: Content is the second team member's full-time job

Each language needs two lists:
- **Answer list** — curated, common, non-obscure words of the right tile-length
- **Guess dictionary** — much larger; defines what counts as a valid guess

Claude can generate candidates from open corpora, but the source doc's own risk line stands: *"word-list quality in market #1 is reputationally critical."* Native review (~$100/language) is not a checkbox — a bad word on day 3 in the flagship language is a Telegram-channel roast, not a bug ticket. Content authoring runs as a parallel track from day 1, owned by the second team member.

---

## Decision log (2026-08-20)

Settled in conversation and folded into the thread docs below:

1. **Name: Harf — locked.** Store collision check found only compound-named Turkish games (Harf Lütfen, Harfle, Harf Oyunu…); bare "Harf" is unclaimed and nothing exists in our markets. Domain: `harf.app` is taken; `harf.uz` is the preferred brand fit (verify at a .uz registrar), `harf.game` the fallback.
2. **Launch languages: Uzbek (both scripts), Russian, English, Kazakh.** Azerbaijani + Tajik deferred to post-launch expansion (their analysis in doc 01 becomes the expansion plan). ru = diaspora + lingua franca; en positioned for the home exam-prep audience, not global competition. Kazakh still needs a native reviewer — open recruiting item.
3. **Play Console: purchased Aug 20, personal account** → the 12-testers × 14-days closed-testing gate applies with certainty; the clock is now critical path.
4. **Apple Developer: enroll immediately** (this week), not one month before deploy — TestFlight is the team's only iOS device, and banking/IAP clearance runs on its own clock.
5. **Stack: KMP targeting Android, iOS, desktop, wasmJs; Compose Multiplatform navigation.** All four targets scaffolded day 0; feature investment mobile-only until after the Sept 12 freeze.

## Where this could go next

Each thread now has its own deep-dive document:

1. **[01-tile-grapheme-design.md](01-tile-grapheme-design.md)** — the tile/grapheme problem: per-language alphabet inventory, the tile-=-grapheme decision, the tutuq question, the Uzbek script duality (one lexeme, two renderings), keyboards, and the data model that makes new languages a content drop.
2. **[02-mvp-scope.md](02-mvp-scope.md)** — the Sept 12 freeze line: the three loops the MVP must close, in/out tables, the cut line under pressure, the streak-gifting protected slot, and the week-by-week plan to Sept 30.
3. **[03-store-logistics.md](03-store-logistics.md)** — the store gates: the Play closed-testing clock, Apple + cloud-CI setup, money plumbing, regional pricing, deep-link domain, rejection pre-emption, and the consolidated week-0 checklist.
4. **[04-architecture-sketch.md](04-architecture-sketch.md)** — the system: KMP module layout, word packs over word requests, state ownership, the share loop end to end, the gifting entitlement chain, tech picks, and deliberate non-goals.
5. **[05-monetization-map.md](05-monetization-map.md)** — the pay-to-win test, the full feature list (free vs paid), everything monetizable without pay-to-win (cosmetic / convenience / content / status), and the red lines never to cross.
6. **[06-future-directions.md](06-future-directions.md)** — post-MVP: the language-learning layer (meanings, translations, examples, custom notes) and the editorial layer (partner reads, per-publisher white-label editions, and subscription bundling), both built on language-as-data and theme-as-data.
