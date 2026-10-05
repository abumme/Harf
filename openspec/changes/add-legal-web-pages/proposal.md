## Why

The Android/iOS app links out to `https://lazydevs.uz/harf/privacy` and `https://lazydevs.uz/harf/offer` from Settings (`SettingsScreen.kt`, `LegalLinks`), and Google Play requires a **public, reachable Privacy Policy URL on the store listing**. The legal text is authored as source markdown in `docs/legal/` (`privacy-{en,ru,uz,kk,tr}.md`, `offer-{en,ru,uz,kk,tr}.md`); without a hosted page the app has dead legal links and the Play submission is blocked.

The `lazydevs.uz` apex is a separate Angular (SSG) landing site, so the pages are hosted there rather than as static files in this repo. (An earlier draft of this change put `docs/legal/privacy.html` / `offer.html` in this repo; that approach was dropped once the pages moved to the landing site.)

## What Changes

- Host the Privacy Policy and Public Offer on the `lazydevs.uz` landing site:
  - `/harf/privacy` — Privacy Policy
  - `/harf/offer` — Public Offer (terms)
- Each page carries **all five locales (uz / ru / en / kk / tr)** behind an in-page **language toggle**, so one URL serves every listing language. The language buttons shown are those the document actually exists in.
- **Default language:** on a direct visit the page defaults to the browser language (`navigator.language`, first two letters) when it is one of uz/ru/en/kk/tr, otherwise `ru`. This is applied on the client after hydration, so the prerendered/SSR markup stays stable (it is rendered in `ru`). When the visitor reaches a legal page by navigating within the site, the page opens in the language they are already reading the site in (an explicit on-site language choice wins).
- The rendered text mirrors the source markdown in `docs/legal/` word for word (same clause numbering), at the current revisions — Privacy Policy `2026-09-17`, Public Offer `2026-09-14` — with concrete operator name, contact email, canonical URLs, and per-document revision dates (no template placeholders).
- Pages are rendered with the landing site's own design system (no separate standalone HTML is kept in this repo).
- The in-app `LegalLinks.PRIVACY` / `LegalLinks.OFFER` constants already equal the canonical URLs; only the stale KDoc above them is updated.

## Capabilities

### New Capabilities
- `legal-web-pages`: publicly hosted, multi-locale HTML rendering of the Privacy Policy and Public Offer, reachable at the canonical URLs the app and the Play listing point to.

### Modified Capabilities
<!-- none: no existing spec's requirements change -->

## Impact

- **New files (this repo):** none — the hosted pages live in the `lazydevs.uz` landing repo.
- **Edited (this repo):** `sharedUI/.../feature/settings/SettingsScreen.kt` (KDoc only; the URL constants are unchanged). `docs/legal/*.md` already carry the real identifiers and current revisions.
- **External / other repo:** the `lazydevs.uz` landing site renders the two documents from its `src/app/pages/legal/` data (content, per-document dates, language toggle, default-language rule). The `/harf/privacy` and `/harf/offer` routes already exist there.
- **Store-side:** unblocks the "host privacy policy URL" item already noted in the `harf-play-release` change (`tasks.md` 4.2).
- **No app runtime, backend, or dependency changes.**
