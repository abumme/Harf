## Purpose

Cell-style-variants provides three interchangeable ways to draw feedback marks on the board and keyboard, so the same correct/present/absent states can be rendered in distinct visual styles selectable at runtime.

## ADDED Requirements

### Requirement: Three interchangeable mark styles
The app SHALL provide at least three feedback-mark styles, each rendering correct/present/absent for board tiles and keyboard keys, selectable at runtime from a single active-style setting.

#### Scenario: Switching style re-renders feedback
- **WHEN** the active mark style changes
- **THEN** the board and keyboard render subsequent (and currently shown) feedback in the new style

#### Scenario: Each style covers all three states
- **WHEN** any of the three styles is active
- **THEN** correct, present, and absent each have a defined rendering for tiles and keys

### Requirement: Shape-plus-color distinction preserved in every style
Every style SHALL keep the three feedback states distinguishable by shape as well as color, so accessibility is not lost in any variant.

#### Scenario: Distinguishable in grayscale
- **WHEN** any style is rendered in grayscale
- **THEN** correct, present, and absent remain distinguishable by shape

### Requirement: Styles use theme feedback roles
Styles SHALL draw using the active theme's feedback color roles rather than hard-coded colors, so a style works across all palettes/editions.

#### Scenario: Style follows the active palette
- **WHEN** the active palette changes while a style is active
- **THEN** that style's marks adopt the new palette's feedback colors
