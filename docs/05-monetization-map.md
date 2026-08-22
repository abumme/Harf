# Thread 5 — Feature & Monetization Map

**Date:** 2026-08-22 · **Status:** living reference · **Parent:** [harf-exploration.md](harf-exploration.md)

A single map of what Harf does today and what could be monetized **without turning the game pay-to-win**. It complements [02-mvp-scope.md](02-mvp-scope.md) (what ships when) and [04-architecture-sketch.md](04-architecture-sketch.md) (how). This is a menu, not a plan — items here are candidates, not commitments.

## The pay-to-win test

Before selling anything, ask one question:

> **Does the purchase change the shared daily result, or give a competitive edge?**

If **yes** → do not sell it. The daily word is a level playing field; that fairness is the product.
If **no** (cosmetic, convenience, content outside the daily competition, status/support) → it is fair game.

Core posture stays: free-forever daily game, no ads, no timers/energy/lives, no forced or interstitial paywalls, regional pricing for Central Asia (see [03-store-logistics.md](03-store-logistics.md) §3).

---

## 1. Features that exist or are planned

| Feature | Status | Model |
|---------|--------|-------|
| Daily puzzle, 5 language/script pairs (uz-Latn, uz-Cyrl, ru, en, kk) | MVP | **free forever** |
| Grapheme engine — tokenizer, scoring | MVP | free |
| Custom on-screen keyboards (digraph keys) | MVP | free |
| Uzbek dual-script, one lexeme | MVP | free |
| Board with pencil marks (circle / underline / strike) | MVP | free |
| Guess validation, win/lose | MVP | free |
| Share: emoji grid + copy/share | MVP | free |
| Streak + stats (per-language), resume in-progress round | MVP | free |
| 3 cell/mark styles + first-days rotation | MVP | free |
| Themes / palettes (editions), theme selection | MVP | partly free |
| Onboarding (language / script / notification) | MVP | free |
| **Lifetime unlock**: full archive + hard mode + Founder badge | MVP | **paid ~$1.49 regional** |
| Cosmetic board themes | MVP | **paid ~$0.99 regional** |
| Push notifications | post-launch (backend) | free |
| Group leagues (create/join/table) | post-launch (backend) | free core |
| Streak gifting ($0.49) | post-launch (backend) | paid (social) |
| National leaderboards, community words, season pass | post-launch (backend) | mixed |

---

## 2. Monetizable **without** pay-to-win

### Cosmetic (pure skin, zero gameplay effect)
- Extra board themes / palettes; mark-style packs (pencil / marker / chalk / neon)
- Tile skins, board fonts, share-card designs (frames, captions)
- Alternate app icons, sound packs, win animations
- Profile / league flair (emblem, name color)

### Convenience / QoL (never changes the daily result)
- Full past-days archive (already in lifetime)
- Advanced statistics / charts / CSV export
- Cloud backup + cross-device sync (account)
- Custom reminder times / notification tuning

### Content outside the daily competition
- Practice / unlimited mode (play as much as you like — not the daily)
- Hard mode (harder on yourself, no advantage)
- Themed word packs (food, sport, …) for practice
- Weekly-challenge packs, "6-tile Saturdays"
- Language-learning depth packs — see [06-future-directions.md](06-future-directions.md)

### Status / support (vanity)
- Founder badge (shipped), supporter flair, "supporters wall"
- Tip jar ("buy the author a tea", multiple amounts)
- Colored name / badge in leagues (status, not points)

### Social / leagues (cosmetic or convenience only)
- Custom league name / emblem, league themes
- Streak-freeze / streak-gift (personal streak, does **not** affect solving the word)

---

## 3. Red lines — never (pay-to-win / hostile)

- ❌ Hints / reveal-a-letter **in the daily** puzzle
- ❌ Extra guesses in the daily puzzle
- ❌ Removing a timer/limit that others still have
- ❌ Ad-to-continue, forced/interstitial paywalls
- ❌ Selling leaderboard / league standings
- ❌ Anything that changes the shared daily result

**Grey zones (OK, with a caveat):**
- **Hard mode** — harder, not an advantage → fair.
- **Streak-freeze / gift** — about keeping a *personal* streak, not the competition → fair, but framed as care, never pressure.
- **Hints** — allowed only in practice mode, never in the daily.

---

*Living document: add candidates here as they come up; each must pass the pay-to-win test in §0 before it becomes a plan.*
