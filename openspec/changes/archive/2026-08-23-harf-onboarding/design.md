## Context

See proposal.md — Why. Builds on `harf-foundation` (AppSettings, theme, MVI, navigation) and reuses the `MarkStyle`/feedback tokens (from cell-styles). The Game screen already exists (`harf-gameplay`); this adds a legend + help to it and a first-run gate. Offline.

## Goals / Non-Goals

**Goals:**
- A newcomer understands the goal + the marks within seconds, once.
- Rules reachable any time during play.
- Zero friction: skippable, no wizard, no permissions.

**Non-Goals:**
- Language/script pick (Home + in-game toggle already do it), push opt-in (no push), tutorial round, or any answer hint.

## Decisions

**First-run gate via a persisted flag, at the composition root.**
Add `onboarded: Boolean` to `AppSettings` (KSafe). `App()` shows the intro when `!onboarded`, else the normal `NavHost`; dismiss/skip sets the flag. Keeps gating in one place, no new nav backstack entry needed. Alternative: a nav destination — heavier; the intro is a one-shot overlay, not a route.

**Legend as a shared composable driven by the active `MarkStyle`.**
One `MarkLegend` composable renders the three states via `LocalMarkStyle`, reused by both the intro and the in-game legend — so they always agree and track style/palette changes automatically.

**In-game legend is compact + a "?" reopens the full how-to.**
A small one-row legend sits under the board (shape+label ×3). A help icon in the game top bar reopens the intro content as a dismissible sheet/dialog. Reuses the intro composable; no duplicate copy.

**Copy is short and localizable.**
Intro text: "One word a day. Guess it in 6 tries." + the legend. Kept minimal for translation across the launch locales; strings live in resources.

## Risks / Trade-offs

- **Intro dialog + desktop lifecycle** (same class of issue seen when testing nav) — keep the intro an in-composition overlay (not a separate window/route) so it's simple to test and render.
- **Legend adds vertical space on small screens** — keep it a single compact row; it can collapse behind the "?" if space is tight.
- **Flag migration** — `onboarded` defaults false, so existing installs see the intro once; acceptable.

## Open Questions

- Whether the in-game legend is always shown or shown until the player's first solved round then collapses to just the "?" — cosmetic, decide during implementation; does not change the specs.
