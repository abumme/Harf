## Context

See proposal.md — Why. Builds on `harf-foundation` (MVI, theme tokens, DI, settings) and `harf-tile-engine` (tokenizer, scorer, language-config, word-packs). All in `sharedUI/commonMain`. Offline-only.

## Goals / Non-Goals

**Goals:**
- A complete, offline daily round for all five language/script pairs.
- Board + keyboard render from data (theme tokens + language-config), no per-language UI code.
- Deterministic daily selection; share-grid text pixel/format-consistent across languages.

**Non-Goals:**
- Persistence of streaks/stats (`harf-streak-stats`), selectable cell-mark styles (`harf-cell-styles`), monetization, deep links, attribution, leagues.

## Decisions

**Daily selection: deterministic index into the answer list by day-number.**
Compute a day index from the language's fixed-timezone date (epoch-day in that zone), map to an answer via a stable permutation/offset so consecutive days differ and the sequence is reproducible offline. No RNG state stored. Alternative: server-assigned words — rejected (offline requirement).

**Rollover clock via kotlinx-datetime.**
Per-language `TimeZone` from language-config; "today" = current instant → that zone → date. Add `kotlinx-datetime` to the catalog if absent. Alternative: platform date APIs — rejected (need common code + fixed zones).

**One `GameViewModel` (MVI) owns a round.**
State: puzzle (answer graphemes hidden), current input, submitted rows with per-tile feedback, keyboard states, status (playing/won/lost), active script. Actions: input/delete/submit/switchScript/share. Events: invalid-guess, round-ended. Scoring delegated to the engine; the VM never re-implements scoring. Matches the docs' event-sourced-friendly shape (submitted rows are the log).

**Board/keyboard are pure token-driven composables.**
Board reads tile length from the puzzle and marks from `HarfMarks`; keyboard reads rows from language-config and per-key state from the VM. Both stateless beyond their inputs → previewable and testable.

**Keyboard key state = best-known, monotonic.**
Per grapheme keep the best state seen (correct > present > absent); never downgrade. Simple reduction over submitted rows.

**Share: text grid in common; platform sheet via `expect/actual`.**
Grid text built in commonMain (non-Wordle squares consistent with the active feedback palette's emoji mapping). A `Sharer`/`Clipboard` expect-actual: Android (Intent/ClipboardManager), iOS (UIActivityViewController/UIPasteboard), desktop/web best-effort (clipboard; share = copy). Keep iosMain thin.

**Uzbek script switching re-resolves via the engine.**
Active script is round state; switching asks the engine for the lexeme's decomposition in the new script and rebuilds board/keyboard. Already-submitted rows re-render in the new script from their stored grapheme+state (transliterated by the engine where tile counts allow; otherwise rows stay as played — cosmetic only, competition is by guess count).

## Risks / Trade-offs

- **Re-rendering submitted rows after a mid-round script switch where tile counts diverge (`ng↔нг`).** → Switching is offered mainly pre-first-guess; if switched mid-round, keep prior rows in the script they were played (label them) rather than mis-mapping. Cosmetic, not competitive.
- **Deterministic index must not desync across app updates.** → Index derived only from day-number + fixed answer-list order; document that reordering an answer list changes history, so answer order is append-only after launch.
- **Share squares vs the pencil board look different.** → Intentional: board = pencil input, share card = printed emoji output (documented product split).

## Open Questions

- Whether the daily index offset is per-language or global — does not affect specs; decide at implementation with the final answer-list sizes.
