## Context

See proposal.md — Why. Extends `harf-foundation`'s `HarfMarks` token layer and is consumed by `harf-gameplay`'s board/keyboard. Offline; state via `AppSettings`.

## Goals / Non-Goals

**Goals:**
- Three runtime-swappable mark styles, each accessible (shape+color), palette-agnostic.
- A first-run rotation + one-time preference lock, resumable across app restarts.
- Local capture of the choice for later analytics export.

**Non-Goals:**
- Remote/online experiment assignment or metrics upload (needs backend — later).
- Palette/edition system (foundation) and purchased cosmetic themes (monetization).
- Deleting the losing styles from code (future cleanup once a winner is known).

## Decisions

**`MarkStyle` strategy behind the token layer.**
Define `interface MarkStyle { fun tile(state); fun key(state) }` (or a data-driven mark set) with three implementations: `Scribble` (pencil loop/underline/strike — the mockup default), `Fill` (solid tinted cell + shape hint), `Outline` (clean ring/bar/slash). The active `MarkStyle` is provided through the existing `HarfMarks` CompositionLocal so board/keyboard code is unchanged — it always asks the token layer for the mark. Alternative: `when(style)` inside board — rejected (spreads style logic into gameplay).

**Experiment state machine in a `StyleExperimentController`.**
Tracks `dayCount`, `lastDayCounted`, `phase` (rotating | prompt-pending | decided), and `chosenStyle`, all in `AppSettings` (KSafe). A **"session" = a calendar day**: the first app-open on a new local date advances the day counter (once per day, not per navigation or per round). During `rotating`, active style = styles[dayCount]. After day 3, phase → prompt-pending; the prompt result (or dismissal→default) sets `decided` and `chosenStyle`. Manual settings change sets `decided` + `chosenStyle` directly and ends the experiment. Alternative: per-round or per-foreground counting — rejected (too fast; per-day gives each style a full day of real play, matching "через несколько сессий").

**Choice capture: local event record now, export later.**
Append a `style-choice` record (style id, source=experiment|settings, timestamp-from-datetime) to a small local store (settings-backed list or the streak-stats event log if present). No network. A later analytics proposal exports it. Keeps this change offline and self-contained.

**Precedence: manual settings > experiment.**
Any settings pick wins and is sticky; the experiment never overrides a decided choice.

## Risks / Trade-offs

- **Day-boundary counting must be consistent.** → Use the local calendar date; store `lastDayCounted` and advance only when the date differs, so a mid-day relaunch never double-counts. Document the date rule for analytics.
- **Rotation could annoy players who want stability.** → Only 3 sessions, each style is a full working look, and settings override is always available.
- **Three full styles is extra UI surface.** → All three share the `MarkStyle` contract and theme roles; incremental cost is the two non-default renderers.

## Open Questions

- Final three styles' exact visuals beyond Scribble (Fill/Outline specifics) — cosmetic, decided during implementation with previews; does not change the specs.
