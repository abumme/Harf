## Why

Harf is a daily-habit game people open at night, and it is white-only by construction: `HarfTheme` uses `lightColorScheme`, all five editions are light, and `App.kt` reports `isDark = false` unconditionally ("Harf editions are light"). A system-dark-mode user gets a full white flash every evening. This change gives each edition a dark ground and lets the app follow the system (or an explicit choice), while keeping the newsprint identity.

It also fixes a contrast bug the same token work touches: in the **Fill** and **Outline** mark styles the tile/key draws the dark `ink` letter over a full `correct`-colored fill (navy), producing dark-on-dark that fails contrast — today on light, and it would be worse on dark. The letter over a filled mark must use an on-mark color.

Target design: §06 "Dark editions" of the before/after mockup — `docs/ui-review/before-after.html` (published: https://claude.ai/artifact/4ZkU31gB1xvWx7xViVhu7H). The mockup's dark board/keyboard shows the intended dark ground and re-tuned mark colors.

## What Changes

- **Theme mode selection.** Add a persisted theme mode — `system` (default), `light`, `dark`. `system` follows `isSystemInDarkTheme()`. `App.kt` reports the real resolved value so platform status-bar icons match.
- **Dark palette variants.** Each edition gains a dark variant: a dark paper/card/ink/muted/rule set plus accent and feedback (correct/present/absent) colors re-tuned for a dark ground — not a naive inversion. `HarfTheme` selects `light`/`darkColorScheme` from the resolved mode and provides the matching `HarfColors`.
- **Legible filled marks (contrast fix).** When a mark fills the tile or key (Fill/Outline styles), the letter uses an `onCorrect`/`onPresent`/`onAbsent` color that meets contrast on the active theme, instead of the fixed `ink`. Fixes the current dark-on-dark on both themes.

Non-goals: new editions; per-edition bespoke dark art direction beyond re-tuned roles (start from one disciplined dark recipe per edition); the light-side visual/layout work (that is `ui-visual-pass`).

## Capabilities

### Modified Capabilities
- `theming`: add a persisted theme mode (system/light/dark) that resolves to a light or dark palette; add dark variants of every edition with feedback roles re-tuned for a dark ground; require that a letter drawn over a filled mark uses an on-mark color meeting contrast on the active theme.

## Impact

- `sharedUI/.../theme/HarfColors.kt`: dark variant per palette; add `onCorrect`/`onPresent`/`onAbsent` roles.
- `sharedUI/.../theme/HarfTheme.kt`: resolve mode → light/dark scheme + `HarfColors`; use `darkColorScheme` when dark.
- `sharedUI/.../settings/AppSettings.kt` + `settings-storage`: persist the theme mode.
- `sharedUI/.../App.kt`: report the real `isDark` to the platform.
- `feature/game/GameScreen.kt` (`Tile`, `KeyCap`): letter color from the on-mark role when the mark fills.
- `feature/settings` or Home: a theme-mode control (system/light/dark).
- Screenshot goldens gain dark variants — record from CI.
- Coexists with `ui-visual-pass` (both touch `theming`); this change adds mode/dark roles, that one adds danger/success + control styles.
