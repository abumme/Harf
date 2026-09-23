# app-shell Specification

## ADDED Requirements

### Requirement: Consistent screen top bar with back
Every non-Home screen (Settings, Statistics, Archive, Paywall) SHALL present a consistent top bar containing a labeled back/close affordance and the screen title. The back affordance SHALL have an accessible name (not a bare glyph) and SHALL dismiss the screen on every platform, so a user on desktop or web is never dependent on a system back gesture the app does not control. The top bar SHALL match the shared top bar in the "After" mockup at `docs/ui-review/before-after.html`.

#### Scenario: Back is reachable on desktop and web
- **WHEN** a non-Home screen is shown on desktop or web
- **THEN** a labeled back/close control is visible in the top bar and dismisses the screen without relying on a browser or OS back gesture

#### Scenario: Back control is accessible
- **WHEN** a screen reader inspects the back control
- **THEN** the control exposes a text name describing its action, not only a symbol

### Requirement: Home action hierarchy
The Home screen SHALL present one primary action per playable language (start today's puzzle) as the visually dominant control under a "play today" label, and SHALL demote navigation to secondary destinations (Archive, Statistics, Settings) to a secondary (ghost) treatment. The edition/theme picker SHALL present the available editions as selectable swatches showing all options at once, rather than a control that cycles through one edition per tap. On wide viewports the Home content SHALL be constrained to a bounded width and centered rather than left as a fixed narrow column in empty space.

#### Scenario: Primary play action is dominant
- **WHEN** the Home screen is shown
- **THEN** the start-today action(s) render as the primary button style and the secondary destinations render as ghost buttons

#### Scenario: Editions are shown, not cycled
- **WHEN** the user views the theme picker on Home
- **THEN** the available editions are presented as selectable swatches with the active one indicated, and selecting one applies it (locked editions route to the paywall via the existing entitlement gate)

#### Scenario: Wide viewport frames the content
- **WHEN** the Home screen is shown on a wide desktop or web window
- **THEN** the action stack is constrained to a bounded width and centered, not stretched or stranded in a fixed narrow column
