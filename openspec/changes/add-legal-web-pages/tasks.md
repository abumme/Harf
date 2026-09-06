## 1. Legal content inputs

- [ ] 1.1 Collect the real values for every placeholder — operator/seller full name (`{{ФИО}}`), contact email (`{{КОНТАКТНЫЙ_EMAIL}}`), canonical privacy URL (`{{URL_ПОЛИТИКИ_КОНФИДЕНЦИАЛЬНОСТИ}}` = `https://lazydevs.uz/harf/privacy`), canonical offer URL (`{{URL_ОФЕРТЫ}}` = `https://lazydevs.uz/harf/offer`), revision date (`{{ДАТА_РЕДАКЦИИ}}`). Verify: a filled values table is recorded and confirmed by the owner.
- [ ] 1.2 Fill those values into all six source files (`docs/legal/privacy-{en,ru,uz}.md`, `docs/legal/offer-{en,ru,uz}.md`) and delete each "FILL IN BEFORE PUBLISHING" block. Verify: `grep -R "{{" docs/legal` returns nothing.

## 2. HTML pages

- [ ] 2.1 Build `docs/legal/privacy.html` — self-contained page embedding the uz/ru/en Privacy Policy, styled per `docs/07-play-flow.html` (PT Serif + Golos Text, `--paper #fbfcfe`, `--accent #1c3f63`, double-rule header, Moon Cat mark), with `<meta viewport>` and `lang` set per active locale. Verify: opens in a browser, renders the policy, no `{{` tokens, passes an HTML validity check.
- [ ] 2.2 Build `docs/legal/offer.html` — same structure/style for the Public Offer. Verify: opens in a browser, renders the offer, no `{{` tokens.
- [ ] 2.3 Add the in-page language toggle to both pages: default from `navigator.language` when it is uz/ru/en, else `ru`; switching updates all legal text without changing URL. Verify: manually toggle each language on both pages and confirm full-text swap; load with a non-uz/ru/en browser locale and confirm it falls back to ru.
- [ ] 2.4 Confirm HTML text matches the filled source markdown for each document and locale. Verify: spot-diff each locale's HTML against its `.md`; identifiers (name/email/date/URLs) are identical.

## 3. Hosting handoff (lazydevs.uz landing repo)

- [ ] 3.1 Add routes on the `lazydevs.uz` landing site so `/harf/privacy` serves `privacy.html` and `/harf/offer` serves `offer.html` (Caddy/nginx/Angular route + deploy the two HTML files). Verify: `curl -sI https://lazydevs.uz/harf/privacy` and `.../harf/offer` each return `200` with `content-type: text/html`.
- [ ] 3.2 Tap "Privacy Policy" and "Terms / Offer" in the app Settings on a device/emulator. Verify: the browser opens the live pages (not a 404).

## 4. App + release consistency

- [ ] 4.1 Confirm `LegalLinks.PRIVACY` / `LegalLinks.OFFER` in `sharedUI/.../feature/settings/SettingsScreen.kt` equal the final canonical URLs; update only if they differ. Verify: constants match §1.1 values; app compiles (`./gradlew :sharedUI:jvmTest` or a JVM compile).
- [ ] 4.2 Review the published Privacy Policy against the app's actual data behavior and the Play Data Safety form. Verify: categories in the policy match what the app collects (per the spec's consistency requirement); note the URL on the `harf-play-release` change task 4.2.
