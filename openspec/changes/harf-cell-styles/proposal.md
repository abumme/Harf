## Why

We want to decide the game's feedback-mark look with real usage rather than a guess. Ship three distinct cell/mark styles, let each player experience them, capture which they prefer, and after a few sessions converge the player on one — while giving us signal on which style wins. This is a lightweight, offline A/B (really an A/B/C preference test) with no backend.

## What Changes

- Add **three selectable cell/mark styles** for board and keyboard feedback, each a variant of how correct/present/absent are drawn (e.g. Scribble — the fast pencil loop/underline/strike; Fill — solid tinted cells; Outline — clean ring/bar/slash). All keep shape-plus-color distinction (accessibility) and use the theme's feedback roles.
- Add a **forced-rotation exposure**: for a new player's first sessions, rotate the active style per session (session 1 → style A, 2 → B, 3 → C) so each is actually experienced.
- Add a **preference prompt** after the rotation completes: ask which style the player liked; record and lock that as the active style. The player can still change it later in settings.
- Add a **manual style picker in settings** (with previews) available any time.
- Add **local preference recording**: store the chosen style and the rotation/session state via settings; record the choice as a lightweight local event for later export to analytics (no network in this change).

Non-goals: online/remote A/B assignment or server metrics (needs backend — later); the theme palette/edition system itself (foundation) and purchased cosmetic themes (monetization) — this is about the *mark rendering style*, orthogonal to palette; removing styles down to one in code (that's a future cleanup once a winner is clear).

## Capabilities

### New Capabilities
- `cell-style-variants`: three interchangeable feedback-mark rendering styles for board and keyboard, each shape-plus-color distinct, applied from the active selection.
- `style-experiment`: first-session forced rotation across the three styles followed by a one-time preference prompt that records and locks the player's choice, with local capture of the selection for later analysis.

### Modified Capabilities
<!-- none -->

## Impact

- `sharedUI/.../theme/marks/`: three mark-style implementations behind a common `MarkStyle` abstraction, selectable at runtime (extends the `HarfMarks` token layer from `harf-foundation`).
- `sharedUI/.../feature/settings/`: style picker with previews.
- Style/rotation/preference state persisted via `AppSettings`; a small local `style-choice` event record.
- Board and keyboard (`harf-gameplay`) read the active `MarkStyle` from the token layer — no gameplay logic change beyond consuming the selected style.
- Depends on `harf-foundation` (settings, theme tokens) and `harf-gameplay` (board/keyboard that render marks).
