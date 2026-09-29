# Third-party notices

## Wiktionary word lists

The guess dictionaries in `backend/src/main/resources/wordpacks/*_guess.txt` and
`sharedUI/src/commonMain/composeResources/files/*_guess.txt` include words derived from
[Wiktionary](https://www.wiktionary.org/), written by Wiktionary contributors.

- **Source data:** [Wiktextract](https://github.com/tatuylonen/wiktextract) extracts published by
  [kaikki.org](https://kaikki.org/) (English, Russian, Kazakh and Uzbek dumps, downloaded 2026-09-14; the Kazakh and
  Uzbek lists await native review).
- **Processing:** `tools/wordlists` selects ordinary words and their inflected forms, normalizes them to the
  game's alphabets and board length, removes blocklisted words, and transliterates Uzbek Latin to Cyrillic.
- **License:** [Creative Commons Attribution-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/)
  (CC BY-SA 4.0). The derived word lists are distributed under the same license.

The app's public offer credits this source in clause 6.5 (`docs/legal/offer-*.md`).

## Kazakh Hunspell dictionary

The Kazakh guess dictionary (`kk_guess.txt`) includes words spelled by the Kazakh Hunspell dictionary.

- **Source:** [hunspell-kk](https://github.com/taem/hunspell-kk) (`kk_KZ.aff`, `kk_KZ.dic`), developed under the
  OpenOffice.org Lingucomponent project from Alexey Lipchansky's Aspell word list and Kaldybai Bektaiuly's dictionary,
  with the affix file by Akmaral Mussayeva, László Németh and Rail Aliev.
- **Processing:** a word enters the list only when the dictionary spells it and FineWeb-2 (below) shows it in use.
- **License:** tri-licensed GNU GPL 2.0+, GNU LGPL 2.1+ and Mozilla Public License 1.1+; Harf uses it under the
  [Mozilla Public License 1.1](https://www.mozilla.org/MPL/1.1/). The dictionary's source files are available from the
  repository above.

## Uzbek lemma dataset

The Uzbek guess dictionaries (`uz-latn_guess.txt`, `uz-cyrl_guess.txt`) include inflected forms of lemmas from
UzbekLemmaStems-POS-Dataset.

- **Source:** [UzbekLemmaStems-POS-Dataset](https://github.com/MaksudSharipov/UzbekLemmaStems-POS-Dataset) by
  Maksud Sharipov et al., compiled mainly from the *Explanatory Dictionary of the Uzbek Language*.
- **Processing:** `tools/wordlists` inflects each lemma with regular Uzbek suffixes, keeps only forms FineWeb-2 (below)
  shows in use, and transliterates them to Cyrillic.
- **License:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## FineWeb-2 word counts

Kazakh and Uzbek dictionary words are admitted only when they occur in
[FineWeb-2](https://huggingface.co/datasets/HuggingFaceFW/fineweb-2) (`kaz_Cyrl`, `uzn_Latn`) often enough and mostly
in lowercase. Only word counts are used; no FineWeb-2 text is distributed.

- **License:** [Open Data Commons Attribution License v1.0](https://opendatacommons.org/licenses/by/1-0/) (ODC-By 1.0).
  FineWeb-2 is built from Common Crawl, whose [terms of use](https://commoncrawl.org/terms-of-use) also apply.
