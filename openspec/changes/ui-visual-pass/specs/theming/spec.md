# theming Specification

## MODIFIED Requirements

### Requirement: App-wide design-system tokens
The application SHALL expose a single theme that supplies semantic color roles (surface/paper, ink, muted, rule, accent, feedback roles for correct/present/absent, and status roles `danger`/`onDanger` and `success`/`onSuccess`), pencil-scribble mark assets, and typography (PT Serif + Golos). All UI SHALL consume these tokens rather than hard-coded values, including destructive and success affordances, which SHALL use the `danger`/`success` roles rather than literal color values.

#### Scenario: Tokens are available inside the theme
- **WHEN** a composable is rendered within the app theme
- **THEN** it can read the semantic color, mark, and typography tokens for the active theme, including the `danger` and `success` roles

#### Scenario: Feedback roles are distinguishable without color
- **WHEN** a feedback role (correct, present, absent) is presented
- **THEN** it is distinguishable by mark shape as well as by color, so color-blind users can tell states apart

#### Scenario: Destructive affordances use the danger role
- **WHEN** a destructive action (such as delete account) or its error text is rendered
- **THEN** its color comes from the `danger`/`onDanger` token role for the active palette, and no screen defines a literal destructive color

## ADDED Requirements

### Requirement: Shared control styles and shape scale
The theme SHALL define a single set of control styles — a primary button, a secondary (ghost) button, and a danger button — and a shape scale of named corner-radius tokens, both consumed app-wide. Screens SHALL use these shared styles rather than defining bespoke buttons or literal corner radii per screen. The control styles and shape scale SHALL match the "After" mockup at `docs/ui-review/before-after.html`.

The shape scale SHALL provide at least: button `10.dp`, container/card `12.dp`, list-row `11.dp`, chip/badge/pill fully rounded, and swatch `8.dp`. Existing tile and key shapes are out of scope and unchanged.

#### Scenario: Buttons share one styled implementation
- **WHEN** a primary, secondary, or danger button is rendered on any screen
- **THEN** it uses the shared control style with the button corner radius from the shape scale, not a per-screen hand-rolled control or literal radius

#### Scenario: Shape scale is applied, not flattened
- **WHEN** a button, container, list row, chip, or swatch from the design system is rendered
- **THEN** its corner radius matches the corresponding shape-scale token (button 10.dp, container 12.dp, row 11.dp, chip pill, swatch 8.dp), preserving the mockup's "After" radii rather than collapsing to a single default

#### Scenario: One primary action per screen
- **WHEN** a screen presents actions
- **THEN** at most one is styled as the primary button, and secondary navigation uses the ghost style
