## 1. Legal content inputs

- [x] 1.1 Collect the real values for every identifier — operator/seller full name (`Islomov Mekhrojbek`), contact email (`lazydevscat@gmail.com`), canonical privacy URL (`https://lazydevs.uz/harf/privacy`), canonical offer URL (`https://lazydevs.uz/harf/offer`), per-document revision dates (privacy `2026-09-17`, offer `2026-09-14`). Verify: all present as concrete values in `docs/legal/*.md`.
- [x] 1.2 Those values are filled into all source files (`docs/legal/privacy-{en,ru,uz,kk,tr}.md`, `docs/legal/offer-{en,ru,uz,kk,tr}.md`) with no fill-in blocks. Verify: `grep -R "{{" docs/legal` and `grep -Ri "FILL IN" docs/legal` both return nothing.

## 2. Hosted pages (lazydevs.uz landing site)

- [x] 2.1 Render the Privacy Policy on the `lazydevs.uz` landing site at `/harf/privacy`, embedding all five locales (uz/ru/en/kk/tr) from the source markdown, using the landing site's own design system (no separate standalone HTML in this repo). Verify: the live page renders the policy; date `2026-09-17`; §2.7 and §5.3 present; §2.2 states the display name is stored; no `{{` tokens.
- [x] 2.2 Render the Public Offer on the landing site at `/harf/offer`, same structure. Verify: the live page renders the offer; date `2026-09-14`; §6.5 (Wiktionary / Wiktextract via kaikki.org, CC BY-SA 4.0) present; no `{{` tokens.
- [x] 2.3 In-page language toggle on both pages across all five locales: the default comes from `navigator.language` (first two letters) when it is uz/ru/en/kk/tr, else `ru`, applied on the client after hydration so the prerender/SSR markup stays stable; navigating from within the site keeps the site's language; switching updates all text without changing the URL; the buttons offered are only the locales a document provides. Verify (checked on the live site): a kk-KZ browser opens Kazakh, a de-DE browser opens Russian; switching swaps the full text with no URL change.
- [x] 2.4 Rendered text matches the source markdown for each document and locale. Verify: the rendered blocks round-trip to each `.md`'s plain text for all ten documents (privacy + offer × five locales); identifiers (name/email/dates/URLs) are identical.
  - Note: the pages render the `player-accounts` revisions of the markdown — §2.2 stored display name, new §2.7 (word suggestions), new §5.3 (authorized administrator access with an action log), §5.2 retention of anonymized statistics, and §6.1 deletion by an administrator at the user's request — in every locale. The uz text is shown in the site's existing Uzbek orthography (oʻ/gʻ/ʼ).

## 3. Routing and deploy (lazydevs.uz landing repo)

- [x] 3.1 The landing site routes `/harf/privacy` and `/harf/offer` serve the pages, and the change is deployed to production. Verify: `curl -sI https://lazydevs.uz/harf/privacy` and `.../harf/offer` each return `200` with `content-type: text/html`.
- [ ] 3.2 Tap "Privacy Policy" and "Terms / Offer" in the app Settings on a device/emulator. (Not performed here — the live URLs resolve and equal the `LegalLinks` constants, but an on-device tap has not been done.)

## 4. App + release consistency

- [x] 4.1 `LegalLinks.PRIVACY` / `LegalLinks.OFFER` in `sharedUI/.../feature/settings/SettingsScreen.kt` equal the canonical URLs (already correct, unchanged); the stale KDoc above them ("Update these to the hosted locations before release") is replaced with a neutral one. Verify: constants match §1.1; KDoc no longer implies the links are unhosted.
- [ ] 4.2 Review the published Privacy Policy against the app's actual data behavior and the Play Data Safety form. (The policy-vs-behavior consistency was addressed by this change — the policy now matches what the app collects — but the Play Data Safety form cross-check and noting the live URL there belong to the `harf-play-release` change and are not done here.)
