## 1. Word-list builder (:tools:wordlists)

- [x] 1.1 Create the `:tools:wordlists` JVM application module, included in `settings.gradle.kts` inside the client-module block and depending on `:sharedUI`'s JVM variant (fall back to a `JavaExec` task over `:sharedUI`'s JVM compilation if variant resolution fails); add a gitignored dump cache directory; verify `./gradlew :tools:wordlists:run --args="--help"` prints usage and `-PbackendOnly` builds still configure.
- [x] 1.2 Implement the streaming kaikki.org reader and entry filter (skipped `pos` values; abbreviation / acronym / initialism / misspelling tags on the entry or any sense; vulgar / offensive / derogatory / slur tags on the entry or on every sense; forms minus romanization / table-tags / inflection-template / class rows), with tests on real JSONL lines: a Russian noun with stress-marked forms, a `name` entry, an abbreviation, a word with one vulgar sense among ordinary ones (kept), and romanization forms. Tests green.
- [x] 1.3 Implement normalization, tokenization and length filtering through `Normalizer`, `Tokenizer` and `LaunchLanguages`, with tests: stress marks stripped while й, ё and ў are kept; ru ё→е; Uzbek apostrophe variants → ʻ; the `ustunga` exception; Kazakh loan letters dropped; lengths other than 5 dropped. Tests green.
- [x] 1.4 Implement Uzbek Latin→Cyrillic transliteration and the uz-cyrl list (transliterated uz-latn ∪ Wiktionary Cyrillic entries, re-tokenized), with tests for kitob→китоб, shahar→шаҳар, oʻrdak→ўрдак, yangi→янги, yer→ер, ekin→экин, gʻalla→ғалла and a length change across scripts. Tests green.
- [x] 1.5 Implement the output writer: candidates ∪ current guesses ∪ answers (∪ `UzbekDailyWords` for Uzbek), minus blocklist, sorted and deduplicated, with a `#` header naming source, dump date and license; writes both the backend and bundled copies and prints per-language word counts and pack JSON size. Test with temporary directories that no previous guess or answer is lost and blocklisted words are removed.

## 2. Generate and review the dictionaries

- [x] 2.1 Download the five kaikki.org dumps (record the date), run the builder for en, ru, kk, uz-latn and uz-cyrl, and verify `:sharedUI:jvmTest --tests "*WordPackTest"` passes with the regenerated files.
- [x] 2.2 Spot-check 50 random new English and Russian words and record findings, per-language counts and pack sizes for the PR; fix what the samples expose (rules for patterns, blocklist entries for single words) and rebuild until the spot-check passes. English tightening, decided 2026-09-14: drop words without a vowel and blocklist the abbreviation forms the review finds.
- [ ] 2.3 Kazakh and both Uzbek lists (held from the first release, 2026-09-14): a native speaker spot-checks 50 random new words per list; add rejections to the blocklists, rebuild with `--languages kk,uz`, rerun `WordPackTest`, and ship them in a follow-up PR.

## 3. Server merge (:backend)

- [x] 3.1 Implement `WordPackServerService.mergeGuesses()` test-first against the test database: resource words missing from a stored pack are added with exactly one version advance; a second run changes nothing and keeps the version; stored words absent from the resources (accepted suggestions) are kept; answers, schedule and `effectiveFrom` are unchanged; dedupe is case-insensitive like `addGuess`. Tests green.
- [x] 3.2 Call `mergeGuesses()` in `main()` right after `seed()` and before the background loops start; verify the backend compiles and the full backend suite passes.
- [ ] 3.3 Add a backend test asserting each language's backend and bundled guess files are identical, skipping when the client tree is absent; verify it passes locally and in CI's backend job.

## 4. Client verification

- [ ] 4.1 Verify the largest regenerated pack (expected en) on Android, iOS, desktop and web: fetch, cache, restart offline, and play a round, recording load time and cache success per platform. If any platform fails, stop and revise the design before merging.

## 5. Attribution and verification

- [x] 5.1 Add the Wiktionary CC BY-SA credit to `docs/legal/offer-{en,kk,ru,tr,uz}.md` and create `THIRD_PARTY_NOTICES.md` (source, Wiktextract via kaikki.org, license, dump date); verify by reading the rendered text in each locale.
- [x] 5.2 Run the full backend suite, `:sharedUI:jvmTest` (the 7 known local screenshot/semantics failures excepted) and `openspec validate import-guess-dictionaries --strict`; all pass.
- [ ] 5.3 After deploy, confirm each changed language's server pack version advanced exactly once and a synced client accepts a previously rejected inflected form (e.g. `книги`).
