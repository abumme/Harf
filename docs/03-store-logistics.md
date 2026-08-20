# Thread 3 — Store Logistics Gates

**Date:** 2026-08-19 · **Updated:** 2026-08-20 (Gate A confirmed, Apple decision, name/domain findings) · **Status:** exploration deep-dive · **Parent:** [harf-exploration.md](harf-exploration.md)

The Shipaton rule is unforgiving: **live in both stores by Sept 30, 11:45 PM PDT — "in review" does not count.** Store logistics are therefore not an end-of-project chore; several have long clocks that start ticking only when *we* act, and two of them could already be fatal if left until September. This doc is the checklist, ordered by how irreversible the deadline math is.

> Items marked **⚠ verify** involve policy details that change frequently — confirm against current store documentation this week rather than trusting any summary, including this one.

---

## 1. The two clocks that can kill the entry

### Gate A — Google Play closed-testing requirement (CONFIRMED — applies)

The account was purchased **Aug 20, 2026, as a personal account** — squarely inside the rule: personal accounts created after **Nov 13, 2023** must run a closed test with **12 testers opted in for 14 continuous days**, then *apply* for production access (itself a review step with its own turnaround) before anything can go live. The organization-account exemption requires a legal entity + D-U-N-S number — not realistic in this window.

```
worst-case backward math from Sept 30:
  production live          Sept 30
  ── production review     ~2–7 days       → apply by ~Sept 22
  ── production app review  included above
  ── 14 continuous days    → closed test fully staffed by ~Sept 7
  ── recruit 12 testers,   → stub AAB uploaded + testers invited
     opt-in lag ~2–3 days     THIS WEEK (by ~Aug 24)
```

**Action now:**
1. Complete the new-account **identity verification** first — it can take days by itself and blocks everything downstream.
2. Then immediately: upload a stub AAB (empty Compose app with the real applicationId), create the closed track, recruit testers from personal networks/Telegram — recruit 15–18 for dropout margin, since "12 opted-in continuously" is the requirement, not "12 invited." Target: testers running by **Aug 26–28**.
3. The 14-day clock and feature development run in parallel — the stub gets replaced by real builds as they come; the clock doesn't reset on new uploads. ⚠ verify current policy details.
4. Put the testers in one Telegram group from day 1 — they are the seed community, the first league, and the launch-day amplifiers. Compliance burden and growth plan are the same work.

### Gate B — Apple account + first green CI build (this week)

No Mac on the team; iOS exists only through cloud CI. Every day without a green iOS build is schedule risk compounding silently.

**Action now:**
1. **Decision (Aug 20): enroll immediately — this week, not "a month before deploy."** Waiting until ~Sept 1 would leave iOS entirely untested through the whole build phase (TestFlight is the team's only iOS device), land any enrollment hiccup in freeze week, and delay the banking/IAP clearance clock. Enrollment ($99/yr) typically clears in ~24–48h but can drag for days with identity verification. ⚠ verify
2. Pick the CI lane and get **any** Compose Multiplatform iOS build signed and on TestFlight in week 0:
   - **GitHub Actions macOS runners** — no new vendor, pay-per-minute, most manual signing setup
   - **Codemagic** — KMP-aware presets, free tier, fastest to first green build (likely winner for a 2-person team)
   - Signing via App Store Connect API key + fastlane match (or Codemagic's managed signing) — never manual certificates from a Windows machine
3. TestFlight build in week 0 is also the *only* way the team can test iOS at all — it's not just release infrastructure, it's the iOS dev loop.

## 2. Money plumbing (blocks all revenue, silently)

| Item | Why it's sneaky | When |
|------|-----------------|------|
| App Store Connect: **Paid Apps agreement + banking + tax forms** | IAPs silently fail in review/production until banking clears; clearing can take days–weeks | Week 0 |
| Play Console: payments profile / merchant account | Same failure mode | Week 0 |
| RevenueCat project: both store apps linked, API keys into the KMP app | Prereq for any purchase code in week 2 | Week 0–1 |
| **RevenueCat webhook endpoint** registered against our Ktor server | The streak-gifting chain ([02-mvp-scope.md](02-mvp-scope.md) §3) depends on it | Week 2 |
| IAP products created in BOTH consoles (consumable streak-save, non-consumable lifetime, themes) | Apple reviews IAPs *with* the app; missing/misconfigured IAP metadata is a classic first-rejection cause | Week 2, before submit |

## 3. Regional pricing & carrier billing (a launch task, not a setting)

The Asia economics of the concept depend on $0.49–$1.99 *local equivalents* being genuinely local:

- **Play Console:** per-country price templates for UZS, KZT, RUB (diaspora; INR for later expansion). Set deliberately — auto-converted defaults land at unpsychological price points.
- **Carrier billing** (Play): available through local operators in Uzbekistan/Kazakhstan — critical where card ownership is low. Mostly automatic when enabled per country, but **⚠ verify** current operator coverage; don't promise it in marketing until seen working.
- **App Store:** territory-specific pricing on the same SKUs; Apple's alternate price points allow the ~$0.49-equivalent tiers.
- Sanity-check the headline promise: streak save should feel like "a bus fare, not a coffee" in Tashkent — roughly the 5,000–6,000 UZS zone. Price by feel per market, not by FX conversion.

## 4. Deep links need a domain (small, but on the critical path)

Loop 2 (share → tap → play) requires **verified** links, and verification is server-side:

1. Name locked: **Harf** (Aug 20). Store collision check found only compound-named Turkish games (Harf Lütfen, Harfle, Harf Oyunu, Harf Kutusu…) — bare "Harf" is unclaimed and nothing exists in our markets. Domain: `harf.app` is already registered; **`harf.uz` is the preferred brand fit** (DNS check inconclusive — verify at a .uz registrar), `harf.game` appears free as the international fallback. Buy this week.
2. Host `/.well-known/assetlinks.json` (Android App Links) and `/.well-known/apple-app-site-association` (iOS Universal Links) — trivially served by the same Ktor deployment
3. Link format like `harf.app/p/uz/2026-09-30?ref=...` → installed: open puzzle; not installed: store page. Deferred-deep-link attribution (which share drove which install) is what makes the Noise K-factor **measurable** — worth the extra plumbing.
4. Test Telegram's link-preview rendering of share cards early — the card as seen *inside a Telegram group* is the real product surface.

## 5. Review-cycle realities (plan for rejection, not approval)

The concept doc's research: new-app review 3–7 days on both stores, **2–3 Apple rejection cycles typical**. Submit no later than **Sept 16**.

Likely rejection reasons to pre-empt:

| Risk | Pre-emption |
|------|-------------|
| Apple 4.2 "minimal functionality" — word games are a crowded template category | Leagues, gifting, 5 languages, polished onboarding all visible in screenshots + review notes; emphasize the platform, not "a Wordle" |
| IAP metadata/review issues | All SKUs attached to the first submission with clean names/descriptions; test in sandbox |
| Privacy declarations | Privacy policy URL (host on the same domain), App Privacy labels, Play Data Safety form — write once, keep consistent with what the app actually collects |
| Age rating / content questionnaires | Word game with UGC deferred = easy ratings; answer conservatively |
| **Play target API level** | New-app requirements ratchet annually (API 35+ era) — **⚠ verify** the current floor before scaffolding, so the build targets it from day 0 |
| Push permission UX | iOS: request after first solve, not at launch — both a review nicety and better opt-in rates |

**Release-mode note:** use **manual/staged release** on both stores so approval ≠ uncontrolled launch — approved builds can sit ready, then go live in a coordinated moment (with the Telegram seeding push) safely before Sept 30. Do not cut the "go live" click close to the deadline: *live*, not *approved*, is the rule.

## 6. Store listing as a growth asset (second teammate's lane)

- Listings localized in **uz (Latin), ru, kk, en** — the "in OUR language" positioning starts on the store page itself; per-locale subtitles ("Harf — soʻz oʻyini", "Harf — сөз ойыны") also put daylight between us and the Turkish "Harf" titles
- Screenshots show: the board in each language, the Telegram share card, a league table, the streak-gift moment. The store page should *look like* the viral loop
- App name/subtitle carry the local-language keywords ("so'z o'yini", "сөз ойыны"…) — ASO in small national markets is nearly uncontested
- Prepare the Shipaton submission assets (demo video, RevenueCat integration proof) in the same pass — same screenshots, same story

## 7. The week-0 checklist, consolidated

```
☑ Play account purchased Aug 20 (personal) → Gate A CONFIRMED, clock live
□ Identity verify → stub AAB + 15–18 testers → running by Aug 26–28
□ Apple Developer enrollment (decided: now) → this week
□ CI lane picked; green iOS TestFlight     → by Aug 26
□ Green Android AAB from same CI            → by Aug 26
□ Paid-apps agreements, banking, tax ×2     → started this week
□ RevenueCat project + store apps linked    → this week
□ Domain bought (harf.uz pref, harf.game alt) → this week
□ Current Play target-API floor verified    → before project scaffold
□ Privacy policy drafted                    → this week (10 min with a template)
```

Everything here is parallelizable with feature work — but only if it *starts* now. Every item that slips a week converts directly into September risk with no engineering remedy.
