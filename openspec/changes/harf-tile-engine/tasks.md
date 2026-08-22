## 1. Model & tokenizer

- [x] 1.1 Define the grapheme model (`Graphemes`/`Word` wrapper over `List<String>` + language id) in `engine/`; verify a compile + trivial construction test.
- [x] 1.2 Implement input normalization (Uzbek apostrophe variants → U+02BB, `ё`→`е`) as pure functions; verify unit tests for each variant.
- [x] 1.3 Implement the greedy longest-match tokenizer with a per-language exception map; verify tests: `shahar`→`[sh,a,h,a,r]`, single-char languages 1:1, longest-match over `ng`/`n`, and an exception override.

## 2. Scoring

- [x] 2.1 Implement two-pass grapheme scoring (correct/present/absent) with duplicate-count multiset; verify tests: all-correct, digraph present-as-unit, surplus-duplicate→absent, correct takes priority.
- [x] 2.2 Enforce length/language validation; verify a mismatched-length guess returns an invalid result.

## 3. Language config & registry

- [x] 3.1 Define `@Serializable` `LanguageConfig` (inventory, tokenizer rules, exceptions, keyboard rows with digraph keys, display, script pairing); verify it serializes/round-trips.
- [x] 3.2 Author configs for uz-Latn, uz-Cyrl, ru, en, kk encoding launch orthography rules (tutuq excluded, 1995/U+02BB, `ё`=`е`, Kazakh native-alphabet, 4–7 length); verify each config exposes all roles and a digraph key per digraph.
- [x] 3.3 Model Uzbek as one lexeme with `perScript` decomposition; verify requesting a lexeme in Latin vs Cyrillic returns each script's graphemes.
- [x] 3.4 Implement `LanguageRegistry` provided via Koin (from `harf-foundation` DI); verify a test resolves a config by id.

## 4. Word packs

- [x] 4.1 Add first-pass bundled answer + guess-dictionary resources per language under `composeResources/files/`; verify they load offline via a resource-read test.
- [x] 4.2 Implement lazy pack loading + cached tokenized guess `Set`; verify valid-word accepted / non-word rejected tests.
- [x] 4.3 Implement `validatePack(config, pack)` and run it as a unit test per language; verify every entry tokenizes, answers have supported lengths, and answers ⊆ guess dictionary.

## 5. Integration check

- [x] 5.1 End-to-end engine test per language: tokenize an answer, score a losing then winning guess, and confirm feedback; verify JVM test suite passes (`:sharedUI` JVM tests).
