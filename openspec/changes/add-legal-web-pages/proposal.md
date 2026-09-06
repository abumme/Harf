## Why

The Android/iOS app links out to `https://lazydevs.uz/harf/privacy` and `https://lazydevs.uz/harf/offer` from Settings (`SettingsScreen.kt:74-77`, commented *"Update these to the hosted locations before release"*), and Google Play requires a **public, reachable Privacy Policy URL on the store listing**. Today those URLs 404 — the legal text exists only as source markdown in `docs/legal/` (`privacy-{en,ru,uz}.md`, `offer-{en,ru,uz}.md`). Without a hosted page the app has dead legal links and the Play submission is blocked.

## What Changes

- Add two self-contained static HTML pages under `docs/legal/`:
  - `privacy.html` — Privacy Policy, served at `lazydevs.uz/harf/privacy`
  - `offer.html` — Public Offer (terms), served at `lazydevs.uz/harf/offer`
- Each page embeds all three locales (uz / ru / en) with an in-page **language toggle** (default locale detected from `navigator.language`, fallback `ru`), so one URL serves every listing language the app supports.
- Pages follow the existing project doc style (`docs/07-play-flow.html`): PT Serif + Golos Text, `--paper #fbfcfe`, `--accent #1c3f63`, double-rule header, Moon Cat mark.
- Fill the legal placeholders (`{{ФИО}}`, `{{КОНТАКТНЫЙ_EMAIL}}`, `{{URL_ПОЛИТИКИ_КОНФИДЕНЦИАЛЬНОСТИ}}`, `{{URL_ОФЕРТЫ}}`, `{{ДАТА_РЕДАКЦИИ}}`) with the real operator name, contact email, canonical URLs, and revision date — in both the HTML pages and the source `.md` files, so they stay in sync.
- Wire hosting so the two canonical URLs resolve: the `lazydevs.uz` apex is served by the separate landing site (`D:\Sources\landing`), so this change specifies the required route (`/harf/privacy` → `privacy.html`, `/harf/offer` → `offer.html`) as an explicit handoff. The HTML deliverables live in this repo; the routing edit lands in the landing repo.
- Confirm the in-app `LegalLinks.PRIVACY` / `LegalLinks.OFFER` constants match the final canonical URLs.

## Capabilities

### New Capabilities
- `legal-web-pages`: publicly hosted, multi-locale HTML rendering of the Privacy Policy and Public Offer, reachable at the canonical URLs the app and the Play listing point to.

### Modified Capabilities
<!-- none: no existing spec's requirements change -->

## Impact

- **New files:** `docs/legal/privacy.html`, `docs/legal/offer.html`.
- **Edited:** `docs/legal/*.md` (placeholders filled); possibly `sharedUI/.../feature/settings/SettingsScreen.kt` (only if canonical URLs change).
- **External / other repo:** `lazydevs.uz` landing site must add the `/harf/privacy` and `/harf/offer` routes (Caddy/nginx/Angular). Tracked here as a handoff task, not edited by this change.
- **Store-side:** unblocks the "host privacy policy URL" item already noted in the `harf-play-release` change (`tasks.md` 4.2).
- **No app runtime, backend, or dependency changes.**
