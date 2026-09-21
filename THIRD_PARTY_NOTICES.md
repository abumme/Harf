# Third-party notices

## Wiktionary word lists

The guess dictionaries in `backend/src/main/resources/wordpacks/*_guess.txt` and
`sharedUI/src/commonMain/composeResources/files/*_guess.txt` include words derived from
[Wiktionary](https://www.wiktionary.org/), written by Wiktionary contributors.

- **Source data:** [Wiktextract](https://github.com/tatuylonen/wiktextract) extracts published by
  [kaikki.org](https://kaikki.org/) (English and Russian dumps, downloaded 2026-09-14; the Kazakh and Uzbek lists
  follow after native review).
- **Processing:** `tools/wordlists` selects ordinary words and their inflected forms, normalizes them to the
  game's alphabets and board length, removes blocklisted words, and transliterates Uzbek Latin to Cyrillic.
- **License:** [Creative Commons Attribution-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/)
  (CC BY-SA 4.0). The derived word lists are distributed under the same license.

The app's public offer credits this source in clause 6.5 (`docs/legal/offer-*.md`).

## Onest typeface

The staff admin panel bundles the [Onest](https://github.com/googlefonts/onest) typeface in
`adminWeb/src/jsMain/resources/public/fonts/onest/` (WOFF2 subsets as served by Google Fonts).

- **Copyright:** 2021 The Onest Project Authors.
- **License:** [SIL Open Font License 1.1](https://openfontlicense.org/); the full text ships next to the fonts
  as `OFL.txt`.
