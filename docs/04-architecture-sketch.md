# Thread 4 — Architecture Sketch

**Date:** 2026-08-19 · **Updated:** 2026-08-20 (targets, navigation, language set) · **Status:** exploration deep-dive · **Parent:** [harf-exploration.md](harf-exploration.md)

Design goals, in priority order: **(1)** the daily loop works offline and instantly, **(2)** ~95% commonMain is measured and true (the JetBrains story), **(3)** the backend stays small enough for one person to operate during launch week, **(4)** iosMain stays thin because iOS is debugged only through cloud CI.

---

## 1. The system at a glance

```
┌─────────────────────────── KMP CLIENT ───────────────────────────┐
│  commonMain (~95%)                                               │
│  ┌────────────┐ ┌───────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ core:game  │ │ core:     │ │ core:data│ │ features:        │  │
│  │ tile engine│ │ content   │ │ SQLDelight│ │ play · leagues · │  │
│  │ scoring    │ │ Language  │ │ + prefs  │ │ share · paywall ·│  │
│  │ keyboard   │ │ Config,   │ │ (offline │ │ stats · onboard  │  │
│  │ state      │ │ word packs│ │  truth)  │ │ (Compose MPP)    │  │
│  └────────────┘ └───────────┘ └──────────┘ └──────────────────┘  │
│  androidMain / iosMain (thin): push token, share sheet,          │
│  deep-link entry, purchases-kmp platform bits                    │
└───────────────┬──────────────────────────────┬───────────────────┘
                │ HTTPS (Ktor client)          │ store IAP
                ▼                              ▼
┌────────────── KTOR SERVER (single deployable) ──────────────┐   ┌────────────┐
│  /packs      word-pack delivery (signed, cacheable)         │   │ RevenueCat │
│  /results    puzzle result submission → streaks, leagues    │◀──│  webhooks  │
│  /leagues    create · join(invite) · table · recap          │   └────────────┘
│  /gifts      streak-save entitlements (webhook-driven)      │
│  /.well-known assetlinks.json · AASA (deep-link verify)     │
│  /p/{...}    share-link landing (open app or store)         │
│  scheduler:  daily rollover · league recaps · streak-risk   │
│  push:       FCM + APNs                                     │
│  PostgreSQL ──────────────────────────────────────────────  │
└─────────────────────────────────────────────────────────────┘
```

One Ktor deployable + one Postgres on a managed host (Fly.io / Railway / Hetzner VPS — pick whichever the team already knows; boring beats optimal). The server also serves the deep-link verification files and share landing pages, so **one domain, one deployment** covers [03-store-logistics.md](03-store-logistics.md) §4.

### Targets (decided 2026-08-20)

All four KMP targets — **Android, iOS, desktop, wasmJs** — are scaffolded at project creation (cheap on day 0, painful to retrofit). Feature investment is mobile-only until after the Sept 12 freeze: Android + iOS are the Shipaton deliverables; desktop/wasm get zero feature time before then. Two consequences to bake in from the first commit:

- **purchases-kmp and push are Android/iOS-only.** Monetization and push live behind commonMain interfaces whose real implementations link only into the mobile targets (no-op elsewhere) — otherwise desktop/wasm don't compile. SQLDelight needs its web-worker driver on wasm; same interface discipline.
- **The post-Shipaton wasm play:** the share-link landing page (`/p/...`) can become a wasm mini-player — tap a Telegram link on desktop, play today's word in the browser, install CTA below. It's the strongest possible "Ship Kotlin Everywhere" demo for the JetBrains track, and this architecture keeps that door open at zero current cost.

## 2. The load-bearing decision: word packs, not word requests

The daily puzzle must never depend on a live request — target users ride metros with dead zones, and a cold server on launch morning would kill the streak habit at birth.

```
content pipeline (offline)            server                      client
──────────────────────────            ──────                      ──────
corpus → Claude candidates            /packs/{lang}/{month}       fetch next pack
→ native review → grapheme            signed pack: ~30 days of    opportunistically
decomposition → curated               daily entries, answers      (wifi, app open)
answer schedule                       lightly obfuscated          → SQLDelight
                                      (XOR/AES with date-derived  → puzzle opens
                                      key — deterrent, not        instantly, offline
                                      security)
```

- **Guess dictionaries** (thousands of words/language) ship **in the app binary** — they change rarely; a store update is fine.
- **Answer packs** come from the server — curation stays live (a bad word can be swapped for *future* days without an app update, honoring the "reputationally critical" risk).
- Obfuscation is a deterrent against casual datamining only. Anti-cheat posture overall: **don't fight it.** Stakes are streaks and banter; server does plausibility checks (result timing, guess count) and nothing more. This is a deliberate non-goal to record.
- Daily rollover: **per-language fixed timezone** (Asia/Tashkent for uz and en — the exam-prep audience is at home; Asia/Almaty for kk; Europe/Moscow for ru — where the diaspora lives). "Everyone in the group gets the word at midnight *our* time" is the culturally correct behavior and makes league days unambiguous. Anchors are tweakable before launch; the principle isn't.

## 3. State ownership — one table to prevent a class of bugs

| State | Owner | Sync direction | Why |
|-------|-------|----------------|-----|
| Today's board / guesses in progress | Client (SQLDelight) | — | Offline-first; survives process death |
| Streak, personal stats | **Client-first, server-mirrored** | client → server on result | Server must know streaks for gifting + streak-risk push; client remains source of truth on conflict (player trust > our plumbing) |
| League membership, tables | **Server** | server → client | Multi-party truth; client caches for display |
| Entitlements (lifetime, themes, streak saves) | **RevenueCat + server grants** | RC → webhook → server → client | Client never self-grants anything gifted; purchases-kmp handles restore |
| Word packs / daily answers | Server-authored, client-cached | server → client | §2 |
| Push tokens | Server | client → server | Per-device, per-language subscriptions |

The one genuinely fiddly seam is **streak reconciliation** (offline solve on day N, sync on day N+2, meanwhile a friend gifted a save). Rule sketch: server computes streak from the result log, gifts insert a "streak-repair" event into the same log, client replays the log — event-sourced streaks rather than a mutable counter. Small design, worth writing down in design.md before coding.

## 4. The share loop, end to end

```
solve → core:share renders card (Compose → ImageBitmap, offscreen)
      → emoji grid text + card image + link harf.app/p/uz/2026-09-30?ref=<userId>
      → platform share sheet (expect/actual, ~20 lines per platform)
      → Telegram group
tap   → installed?  App Link / Universal Link → app opens directly to that puzzle
      → not installed? /p/... landing → store badge, ref captured
      → post-install: deferred attribution (install referrer on Android;
        best-effort on iOS) → "installs per share" = the Noise metric
```

Two implementation notes:
- **Render the share card with the same Compose code on both platforms** — it's the most-seen artifact of the whole product and must be pixel-identical in every group chat. Offscreen Compose rendering to a bitmap works on both targets; spike it in week 1, not week 3.
- The emoji **text** grid (not just the image) must always be included — Telegram previews text instantly, and text grids are what made Wordle's loop copy-paste-proof.

## 5. Streak gifting — the entitlement chain in detail

Covered in [02-mvp-scope.md](02-mvp-scope.md) §3 with the protected schedule slot; architectural additions:

- Consumable purchase carries `{targetUserId, streakDate}` as RevenueCat purchase metadata (or an attributes API call at purchase time) so the **webhook alone** is enough for the server to grant the right repair to the right person — no client round-trip in the fulfillment path.
- Webhook handler must be **idempotent** (RevenueCat retries) and must tolerate arriving before/after the friend's client sync — the event-sourced streak log (§3) absorbs both orderings.
- The push ("Aziz saved your streak 🔥") fires from the webhook path — it's the emotional payoff and the re-engagement trigger in one.

## 6. Technology picks (proposed defaults, all boring)

| Concern | Pick | Note |
|---------|------|------|
| UI | Compose Multiplatform | Given |
| Navigation | **Compose Multiplatform navigation** (navigation-compose MPP) | Decided Aug 20 — the official artifact, sufficient for ~8 screens |
| Persistence | SQLDelight | commonMain, event-log friendly |
| Networking | Ktor client + kotlinx.serialization | Symmetric with server |
| DI | Koin | KMP-native, low ceremony |
| Purchases | purchases-kmp (RevenueCat) | Given; also the Shipaton requirement |
| Push | FCM (android) / APNs (ios) behind an expect/actual `PushRegistrar` | Skip Firebase iOS SDK; server talks APNs directly — less iosMain, less CI pain |
| Server | Ktor + Exposed (or plain jdbc) + Postgres | One deployable |
| Server deploy | Fly.io / Railway / small VPS | Whatever needs zero learning |
| CI | GitHub Actions (Android) + Codemagic or Actions macOS (iOS) | Per [03-store-logistics.md](03-store-logistics.md) §1B |

**iosMain discipline:** the explicit budget is *only* — app/scene delegate glue, APNs token, share-sheet presenter, deep-link forwarding, purchases-kmp init. Anything else appearing in iosMain is a design smell, because it can only be debugged through CI round-trips.

## 7. What this architecture defers without regret

- Realtime anything (leagues are turn-based reads) — no websockets, no presence
- Accounts/auth beyond an anonymous device identity + display name (league join via invite link needs no login; account linking/recovery is post-launch) — biggest honest gap: device loss = streak loss; mitigate later with a Telegram-login link, which is also culturally perfect
- Admin tooling — word-swap and league moderation via SQL + a couple of authenticated endpoints; a dashboard is a luxury
- Analytics platform — RevenueCat for revenue, store consoles for installs, plus a tiny homegrown event table for the K-factor math (share→install attribution); no third-party analytics SDK weight at launch

## 8. Open questions to settle in a design.md

1. Event-sourced streak log — exact event vocabulary and replay rules (§3)
2. Offscreen Compose → bitmap capture on iOS — spike result decides whether share cards are Compose-rendered or server-rendered (server-side rendering is the fallback: same Ktor app, skia-based, one implementation for previews *and* cards)
3. ~~Navigation library~~ — decided Aug 20: Compose Multiplatform navigation
4. Anonymous identity format + future Telegram-login migration path
5. Postgres schema for leagues (roughly 5 tables — sketch when the change is scaffolded)
