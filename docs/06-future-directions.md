# Thread 6 — Future Directions: Learning & Editorial

**Date:** 2026-08-22 · **Status:** exploration (post-MVP) · **Parent:** [harf-exploration.md](harf-exploration.md)

Two directions that extend Harf from "a daily word game" toward "a daily habit around language and reading." **Neither is in the MVP** ([02-mvp-scope.md](02-mvp-scope.md)) — they are recorded here so the architecture and data model don't accidentally close the door on them. Both lean on the same moats already chosen: language-as-data ([01-tile-grapheme-design.md](01-tile-grapheme-design.md)) and theme-as-data ([04-architecture-sketch.md](04-architecture-sketch.md)).

---

## 1. Learning layer — from guessing a word to knowing it

The moment after solving is when curiosity peaks: *"what does today's word actually mean?"* Harf can own that moment.

### What it adds
- **Meaning** of the daily word (and any word in the guess dictionary), in the player's language.
- **Translations** across the launch set (uz-Latn/uz-Cyrl ⇄ ru ⇄ en ⇄ kk) — the multilingual base is already there.
- **Usage examples** in different contexts (sentences, collocations, register notes; ideally sourced from a corpus or a partner dictionary).
- **Custom notes** — the player's own note on a word, saved and searchable ("my words").
- Optional depth: etymology, related words, pronunciation/audio.

### Where it lives
- A **"learn" sheet** reachable from the result screen and from a word-history / dictionary view — never interrupting the round.
- A personal **"my words"** list (notes + saved words), a natural retention surface.

### Data-model fit (why it's cheap to keep open)
Word entries in the packs already carry a stable `lexemeId`. The learning layer is **additive metadata** keyed by `lexemeId`: `{ meaning, translations[], examples[], pronunciation? }`. It ships the same way packs do (bundled or, later, server-delivered), and reuses the content pipeline. Custom notes are per-user local data (KSafe), later syncable with the account.

### Positioning & mission
This is the honest version of the exam-prep angle noted in the concept doc (English at home), and it deepens the "finally, a game in **our** language" story for Uzbek/Kazakh — a word game that also teaches. Learning content should skew **free** because it serves the mission and reach; depth **packs** (rich examples, audio, a full bilingual dictionary) are a fair, non-pay-to-win purchase (content outside the daily competition — see [05-monetization-map.md](05-monetization-map.md) §2). Custom notes stay free.

### Risks / open questions
- **Content quality is reputational** (same bar as word lists) — meanings/examples need native/lexicographer review; a wrong definition in the flagship language is a public roast, not a bug.
- Sourcing: open corpora + Claude drafts, then review; or license a dictionary (possible partner overlap with §2).
- Keep it strictly out of the daily loop so it never becomes a hint vector (no meanings/translations of the *unsolved* daily word).

---

## 2. Editorial layer — reading partners & per-publisher experiences

A word game is a **break** activity; so is reading a short article. Pairing the two, and partnering with journalism outlets, turns Harf into a small daily-habit platform and opens a distribution + revenue channel that fits Central-Asian media.

### The two-way idea
- **Their content in our app:** short reads "for breaks" — a curated feed of partner articles surfaced around the game (e.g. after solving, or a "read" tab). Optional, never forced.
- **Our game in their app:** the daily puzzle embedded in a publisher's own app/site (like the NYT/Wordle relationship, but as a partnership). Drives the publisher's engagement; drives our reach.

### Per-publisher experiences (leverages the theme-as-data moat)
Because palette, marks, typography, and language are **all data** already, a **white-label / co-branded Harf** per publisher is cheap: each outlet gets its own edition — its palette, wordmark, fonts, tone — over the same engine. We could even **build the app for the publisher** (their brand, our engine + editorial feed), each with a unique style. The design system built for our own editions is exactly the mechanism.

```
            ┌───────────── shared engine + content pipeline ─────────────┐
            │  grapheme engine · word/learning packs · theme-as-data     │
            └───────────────┬───────────────────────────┬───────────────┘
                            │                           │
                    Harf (our brand)          Publisher edition(s)
                    our editions              per-publisher palette/marks/
                    + partner feed            wordmark/tone + their articles
                            │                           │
                            └── two-way: our game ⇄ their content/audience ──┘
```

### Subscription bundling (think-through)
If a partner has **paid subscriptions**, combine them rather than compete:
- **Cross-entitlement via RevenueCat:** a partner subscriber gets Harf premium (themes/learning packs) unlocked, and/or a Harf supporter gets partner perks. RevenueCat's entitlements/offerings can grant this from either side, with **revenue share** on joint SKUs.
- **Joint "reader + player" subscription:** a single bundle (their journalism + our premium) priced regionally; splits by agreement.
- **Attribution both ways:** installs/reads driven between apps are measurable (deep links, referral), so revenue share is grounded in real numbers.
- Keep it **non-aggressive**: bundling *adds* value (one price, two products), it never gates the free daily game or the free learning basics.

### Why Central Asia specifically
Local journalism (Uzbek/Kazakh/Russian outlets) is underserved by slick native apps; "a beautiful word game + your daily read, in your language" is a story both sides can market. It reinforces the "languages Wordle forgot" mission and gives partners a modern engagement surface they likely lack.

### Risks / open questions
- **Licensing & rights** for article content; clear terms, per-partner.
- **Content moderation & editorial independence** — we host, they own; define boundaries.
- **Offline-first tension:** the game is offline; article feeds need network — keep reading clearly optional and degrade gracefully.
- **Scope discipline:** white-label + partnerships are a *platform* play; do it only after the core app proves the loop. Never let editorial creep compromise the free, fair daily game.
- **Store/billing:** cross-platform subscription bundling has App Store / Play policy nuance — verify before promising.

---

## Where this sits on the roadmap

- **MVP:** neither ships. Only keep the data model additive (`lexemeId`-keyed metadata; theme-as-data; account-ready notes) so both stay cheap to add.
- **Post-launch v1.x:** learning layer (meanings/translations/examples/notes) — closest, highest mission value, mostly free.
- **Later / platform:** editorial feed, per-publisher editions, subscription bundling — partnership-gated, pursued once the core habit is proven.

Both remain candidates, not commitments; each learning/editorial monetization item must still pass the pay-to-win test in [05-monetization-map.md](05-monetization-map.md).
