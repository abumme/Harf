## 1. Daily puzzle

- [ ] 1.1 Add `kotlinx-datetime` to the catalog if absent; verify `:sharedUI` resolves it.
- [ ] 1.2 Implement per-language fixed-timezone "today" and a deterministic day-index → answer mapping; verify tests: same day→same word, consecutive days differ, offline (no network).
- [ ] 1.3 Resolve the Uzbek daily lexeme in either script via the engine; verify Latin and Cyrillic requests return the same lexeme.

## 2. Play-flow ViewModel (MVI)

- [ ] 2.1 Implement `GameViewModel` state/actions/events (input/delete/submit/switchScript/share); verify a unit test drives a full win and a full loss using the engine.
- [ ] 2.2 Wire guess validation against the guess dictionary; verify invalid guess is rejected without consuming an attempt.
- [ ] 2.3 Compute monotonic best-known keyboard states from submitted rows; verify a key never downgrades from correct/present.

## 3. Board & keyboard UI

- [ ] 3.1 Implement the board composable (dynamic length × 6 rows, grapheme tiles, `HarfMarks` feedback); verify a preview shows digraph-in-one-tile and all three mark states, distinguishable in grayscale.
- [ ] 3.2 Implement the keyboard composable from language-config (digraph keys, enter/delete, per-key state marks); verify a preview for uz-Latn shows dedicated `sh/ch/ng/oʻ/gʻ` keys.
- [ ] 3.3 Assemble the game screen (board + keyboard + script switch for Uzbek) bound to `GameViewModel`; verify manual play of a round on desktop.

## 4. Result & share

- [ ] 4.1 Build the emoji share-grid text (per-tile squares in the active palette mapping, header line with name/lang/puzzle#/score); verify a test matches rows/length and header.
- [ ] 4.2 Implement `expect/actual` share + clipboard (android/ios real, desktop/web best-effort); verify copy places text on clipboard on desktop and each target compiles.
- [ ] 4.3 Implement the result screen (win/lose, reveal on loss, share/copy actions); verify manual end-of-round on desktop.

## 5. Integration

- [ ] 5.1 Full offline round per language on desktop and Android (type→validate→score→win/lose→share); verify no network and correct feedback for each language, including Uzbek script switch.
