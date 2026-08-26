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
