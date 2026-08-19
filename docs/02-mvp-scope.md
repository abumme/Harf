# Thread 2 — MVP Scope & the Sept 12 Freeze Line

**Date:** 2026-08-19 · **Status:** exploration deep-dive · **Parent:** [harf-exploration.md](harf-exploration.md)

The concept doc describes the *destination* (five monetization streams, community submissions, national leaderboards, seasonal packs). This doc decides what ships by the **feature freeze (~Sept 12)** — 3.5 weeks from today — and what is deliberately deferred. The scoping principle: **everything inside the freeze must serve either the daily loop, the share loop, or one judge-legible metric.** Anything else waits.

---

## 1. The three loops the MVP must close

```
 LOOP 1: HABIT (daily)        LOOP 2: VIRAL (per solve)     LOOP 3: REVENUE (weekly)
 ────────────────────         ─────────────────────────     ────────────────────────
 push/urge → open app         solve → share card with       streak at risk →
 → today's word → solve       emoji grid + deep link        friend sees it in league
 → streak++ → stats           → lands in Telegram group     → gifts $0.49 save →
                              → friend taps → plays/installs → push "Aziz saved you"
                              → league table updates         → both re-engage

 needs: puzzle, streak,       needs: share renderer,        needs: leagues, RevenueCat,
 local persistence            deep links, install attrib.   webhooks, server entitlements,
                                                            push
```

If any of these three is broken at launch, the Shipaton story collapses: Loop 1 is retention (Grand Prize), Loop 2 is K-factor (Noise), Loop 3 is the HAMM narrative. Everything below is triaged against them.

## 2. In / out at the freeze

### IN — cannot ship without

| Feature | Serves | Notes |
|---------|--------|-------|
| Daily puzzle, 4 languages (uz-Latn, uz-Cyrl as one lexeme, kk, az, tg) | Loop 1 | Per [01-tile-grapheme-design.md](01-tile-grapheme-design.md) |
| Grapheme tile engine + custom keyboards | Loop 1 | The moat; settled in doc 01 |
| Streaks, personal stats | Loop 1 | Synced to server (gifting needs server-known streaks) |
| Share card: emoji grid + deep link, Telegram-optimized | Loop 2 | THE growth mechanic; over-invest here |
| Deep link → today's puzzle (installed) / store (not installed) | Loop 2 | App Links + Universal Links, needs hosted domain |
| Group leagues: create, join via link, weekly table | Loop 2+3 | Turn-based, no realtime; league card back into the chat |
| **Streak gifting $0.49** | Loop 3 | Headline HAMM mechanic — see §3 |
| Founder lifetime unlock $4.99 (regional ~$1.49) | Loop 3 | Simplest SKU; archive + hard mode + badge |
| 2–3 cosmetic board themes $0.99 | Loop 3 | Visible on league table = status; trivial engineering |
| Push notifications (daily nudge, streak-at-risk, "X saved your streak", league recap) | All three | FCM + APNs |
| Onboarding: language pick, script pick (Uzbek), notification opt-in | Loop 1 | Keep to 3 screens max |

### OUT — deliberately deferred past Shipaton (say so proudly in the submission)

| Feature | Why deferred |
|---------|--------------|
| Gem economy | A whole economy design + top-up SKUs; zero Day-0 value; the doc's transparency promises (published earn rates) deserve real design time |
| Community word submissions | Moderation + review pipeline; content track covers supply for months without it |
| National/city leaderboards ("Toshkent vs Samarqand") | Needs volume to be meaningful; great v1.1 press beat *after* installs exist |
| League Season Pass $2.99 group-buy | The heaviest webhook work (group entitlements); gifting alone proves the HAMM story |
| Seasonal packs (Navruz/Eid) | Navruz is March; Eid timing doesn't help September judging |
| Invite-3-friends unlock | Attribution plumbing; the share loop is the organic version of this |
| Hard mode as a free toggle | It's inside the lifetime unlock instead — gives the $4.99 SKU substance |

### The cut line under pressure (drop in this order if the schedule slips)

1. Third cosmetic theme → ship 2
2. League weekly recap card → table view only, recap post-launch
3. Tajik at launch → 3 languages, Tajik week 1 post-launch (it's the smallest market of the four)
4. **Never cut:** share card quality, streak gifting, Uzbek content review

## 3. The streak-gifting decision (the boundary feature)

Streak gifting is simultaneously the headline mechanic and the only MVP feature requiring the full server-side entitlement chain:

```
Friend's app                RevenueCat              Our Ktor server            Player's app
────────────                ──────────              ───────────────            ────────────
sees broken streak
in league view
→ buys consumable  ──────▶  processes IAP  ──────▶  webhook: purchase
  ($0.49)                                           → validate event
                                                    → grant streak-restore
                                                    → mark gifted-by
                                                    ◀─ push via FCM/APNs ──▶  "Aziz saved your
                                                                               41-day streak 🔥"
                                                                              → streak restored
                                                                              → both re-engaged
```

**Decision: it stays in scope, and it gets a protected, non-negotiable engineering slot in week 3** (Sept 1–7), after the backend exists but before polish week. The concept doc's own cross-cutting note says gift SKUs "are real engineering… not a checkbox" — the way to honor that is calendar space, not optimism. Fallback if the webhook chain slips: **self-serve streak freeze** ($0.49, client-side entitlement, no webhook) ships as the consumable, and gifting becomes the first post-launch update. This fallback is materially worse for the HAMM story — treat it as a genuine emergency hatch only.

## 4. Week-by-week plan (Aug 19 → Sept 30)

```
WEEK 0  Aug 19–24   GATES + SKELETON
├─ Store gates (see 03-store-logistics.md): Play account check, stub AAB,
│  closed testing recruitment, Apple enrollment, banking/tax forms
├─ KMP project scaffold; green CI: Android AAB + iOS TestFlight build
├─ Tile-engine spike: tokenizer + scoring for uz-Latn (the hard case)
└─ CONTENT TRACK STARTS: corpus sourcing, Claude candidate generation uz

WEEK 1  Aug 25–31   PLAYABLE CORE
├─ Game board + keyboard (data-driven layouts), streak/stats local
├─ uz-Latn playable end-to-end on device
├─ Ktor backend skeleton: daily-word endpoint, deploy target chosen
└─ CONTENT: uz answer list v1 → native reviewer #1 engaged

WEEK 2  Sept 1–7    LOOPS 2 & 3
├─ Share-card renderer + deep links (domain, assetlinks, AASA files)
├─ Leagues: create/join/table; results submission
├─ RevenueCat: lifetime unlock + themes purchasable
├─ ⚠ PROTECTED SLOT: streak gifting webhook chain (see §3)
└─ CONTENT: kk, az lists v1 → reviewers; uz-Cyrl pairing pass

WEEK 3  Sept 8–12   FREEZE WEEK
├─ Push notifications wired to all four triggers
├─ Remaining languages integrated; onboarding; polish
├─ Sept 12: FEATURE FREEZE — bugfix only beyond this line
└─ CONTENT: tg list v1; 90 days of daily words scheduled per language

Sept 13–16          SUBMIT
├─ Store listings ×5 locales, screenshots, privacy forms, review notes
└─ Submit both stores no later than Sept 16

Sept 17–30          REVIEW BUFFER (do not plan features here)
├─ 2–3 Apple rejection cycles expected (per concept doc research)
├─ Play closed-testing clock completes; production access application
└─ Launch seeding prep: Telegram channel list, day-1 posts, press notes
```

**Honest capacity check:** engineering weeks 0–3 assume person A full-time on app+backend and person B on content + store ops + share-card design. The plan has **zero slack weeks** — the slack lives in the cut line (§2) and the Sept 13–16 submit buffer.

## 5. What the freeze line buys, per judge

| Prize | What must demonstrably exist by judging |
|-------|------------------------------------------|
| **Noise (Most Viral)** | Share cards in real Telegram groups; measurable installs-per-share via deep-link attribution |
| **Grand Prize (traction)** | Chart position in UZ/KZ/AZ app stores; DAU curve from daily loop; Day-0 revenue from consumables |
| **HAMM** | Streak gifting live with real gift events to cite; lifetime + themes as stream #2 and #3; deferred-streams roadmap told honestly |
| **JetBrains** | ~95% commonMain measured and stated; Compose MPP board + Ktor backend as the Kotlin-everywhere story |

## 6. Next step when this crystallizes

This scope is ready to become an OpenSpec change (`openspec new change "harf-mvp"` or similar): §2's IN table maps to capabilities/specs, §3 and doc 01's decisions map to design.md, §4 maps to tasks.md. Not scaffolding it yet — that's a deliberate ask away.
