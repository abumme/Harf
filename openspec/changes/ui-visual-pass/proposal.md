## Why

Before release, the surfaces users touch most read as unfinished: Home is a wall of six identical `width(220.dp)` buttons with no primary action; Settings is one flat scroll of full-width `OutlinedButton`s where **Delete account** is distinguished only by a hardcoded `Color(0xFFD32F2F)`; and there is no shared top bar, so Stats and Settings have no back/close on desktop or web. The `theming` spec already says "All UI SHALL consume these tokens rather than hard-coded values" — several screens violate it. This change adopts a single control system and applies it to Home and Settings.

The target design is the **"After" column of the before/after mockup**:
- Committed in-repo: `docs/ui-review/before-after.html` (§01 Home, §02 Settings, §05 Copy, §06 System).
- Published: https://claude.ai/artifact/4ZkU31gB1xvWx7xViVhu7H

The mockup is the visual authority for this change. Its corner radii, spacing, and grouping are part of the spec, not a suggestion — see `design.md` for the pinned shape scale so the "after" radii survive implementation.

## What Changes

- **One control system, tokenized.** Introduce shared primary / ghost / danger button styles and a shared screen top bar (labeled back + serif title), reused across screens instead of per-screen hand-rolled `Box + clickable` or ad-hoc `OutlinedButton` stacks. Corner radii and spacing come from the mockup shape scale (`design.md`).
- **`danger` / `success` semantic token roles** added to `HarfColors` + `HarfTheme`, replacing the three hardcoded `Color(0xFFD32F2F)` literals in Settings. Roles are defined per palette so they carry into future editions (and dark mode) for free.
- **Home gains hierarchy.** Today's language(s) become the one primary action under a "play today" label; Archive / Statistics / Settings demote to a single secondary (ghost) row. The theme control becomes a visible **swatch row** (recognition) instead of a blind one-at-a-time cycle. On wide windows the stack is centered within a `widthIn(max=...)` hero instead of a 220dp column stranded in whitespace.
- **Settings is grouped** into sections (Account / Play Games / Legal) with an isolated **danger zone** at the bottom using the `danger` token, and gains the shared top bar.
- **Copy clarify (RU primary).** `home_tagline` дуэль→игра в слова; delete confirm button names its object (`Удалить`→`Удалить аккаунт`); `signin_failed` / `link_failed` / `delete_failed` stop showing the raw `%2$s` code to users (logged instead); normalize the Uzbek apostrophe (`ʻ` U+02BB vs ASCII `'`) and unify `Qiyin rejim` across `values-uz`.

Non-goals: the Archive rows + the archive open-day bug (change `archive-history`); the win/result moment (change `result-moment`); dark editions (separate change). No behavior change to gameplay, scoring, or navigation targets.

## Capabilities

### New Capabilities
<!-- none — the shared components are design-system behavior captured under theming/app-shell -->

### Modified Capabilities
- `theming`: add `danger`/`success` (with `onDanger`/`onSuccess`) semantic roles to the token set; add a requirement that shared control styles (primary/ghost/danger buttons) and the shape scale (corner radii, spacing) are defined once from the mockup and consumed app-wide, with no hard-coded colors or per-screen bespoke buttons.
- `app-shell`: add a consistent screen top bar (labeled back + title) available to every non-Home screen so it is dismissible on desktop/web; add a Home action-hierarchy requirement (one primary "play today", grouped secondary navigation, theme picker shown as selectable swatches).

## Impact

- `sharedUI/.../theme/HarfColors.kt`: add `danger`/`onDanger`/`success`/`onSuccess` roles + per-palette values.
- `sharedUI/.../theme/HarfTheme.kt`: map the new roles into the Material scheme.
- New shared composables (buttons + top bar) under `sharedUI/.../theme/` or a `core/ui` package.
- `feature/home/HomeScreen.kt`: action hierarchy + swatch theme picker.
- `feature/settings/SettingsScreen.kt`: sectioned groups + danger zone + top bar; removes 3× `Color(0xFFD32F2F)`.
- Strings: `values/`, `values-ru/`, `values-uz/` (tagline, delete button, error messages, UZ apostrophe/terminology).
- Screenshot goldens for Home/Settings shift — re-record from CI artifact.
