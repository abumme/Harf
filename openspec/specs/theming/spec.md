# theming Specification

## Purpose
Theming gives Harf a single, consistent design system: semantic color roles, the pencil-scribble mark styles, and typography, all applied app-wide and driven by a persisted theme selection.

## Requirements

### Requirement: App-wide design-system tokens
The application SHALL expose a single theme that supplies semantic color roles (surface/paper, ink, muted, rule, accent, and feedback roles for correct/present/absent), pencil-scribble mark assets, and typography (PT Serif + Golos). All UI SHALL consume these tokens rather than hard-coded values.

#### Scenario: Tokens are available inside the theme
- **WHEN** a composable is rendered within the app theme
- **THEN** it can read the semantic color, mark, and typography tokens for the active theme

#### Scenario: Feedback roles are distinguishable without color
- **WHEN** a feedback role (correct, present, absent) is presented
- **THEN** it is distinguishable by mark shape as well as by color, so color-blind users can tell states apart

### Requirement: Selectable, persisted active theme
The application SHALL support selecting an active theme/palette and SHALL persist that selection so it is reapplied on the next launch.

#### Scenario: Selecting a theme updates the UI
- **WHEN** the user selects a different theme/palette
- **THEN** the app surface and feedback roles update to that theme immediately

#### Scenario: Theme selection survives relaunch
- **WHEN** the user selects a theme and later relaunches the app
- **THEN** the previously selected theme is applied on launch

#### Scenario: First launch uses a defined default theme
- **WHEN** the app launches with no prior theme selection stored
- **THEN** a defined default theme is applied

### Requirement: Theme mode selection (system / light / dark)
The app SHALL support a persisted theme mode with values `system`, `light`, and `dark`, defaulting to `system`. In `system` mode the app SHALL follow the platform's dark-mode setting; in `light`/`dark` it SHALL use that ground regardless of the platform setting. The app SHALL report the resolved light/dark value to the platform so system chrome (status-bar icon color) matches the rendered ground.

#### Scenario: System mode follows the platform
- **WHEN** the theme mode is `system` and the platform switches between light and dark
- **THEN** the app renders the light or dark ground to match, and reports the resolved value to the platform

#### Scenario: Explicit mode overrides the platform
- **WHEN** the theme mode is `light` or `dark`
- **THEN** the app renders that ground regardless of the platform setting

#### Scenario: Mode is persisted
- **WHEN** the user selects a theme mode and relaunches the app
- **THEN** the previously selected mode is restored

### Requirement: Dark palette variants per edition
Every edition SHALL provide a dark variant supplying the full token set (paper/card/ink/muted/rule/accent and the correct/present/absent feedback roles) tuned for a dark ground, not a naive inversion. When the resolved mode is dark, the theme SHALL provide the active edition's dark tokens and a dark Material color scheme; the mark shapes and identity of the edition SHALL be preserved across light and dark.

#### Scenario: Dark ground for the active edition
- **WHEN** the resolved mode is dark
- **THEN** the active edition renders on its dark ground with its dark token set and a dark Material scheme

#### Scenario: Contrast holds on the dark ground
- **WHEN** any edition is rendered in dark mode
- **THEN** ink-on-ground and accent-on-ground remain legible, and the correct/present/absent feedback stays distinguishable by shape as well as color

### Requirement: Filled marks stay legible
When a feedback mark fills a tile or key (the Fill and Outline mark styles), the letter drawn over it SHALL use an on-mark color (`onCorrect`/`onPresent`/`onAbsent`) that meets contrast against the fill on the active theme, rather than the default ink color.

#### Scenario: Letter over a filled correct mark is legible
- **WHEN** a tile or key shows a filled correct/present/absent mark in the Fill or Outline style
- **THEN** the letter uses the matching on-mark color and remains legible against the fill, in both light and dark themes
