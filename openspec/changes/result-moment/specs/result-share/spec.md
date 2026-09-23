# result-share Specification

## ADDED Requirements

### Requirement: Result screen renders the grid inline
When a round ends, the result screen SHALL render the feedback grid visually in the app — one cell per tile, per played attempt, in the active edition's colors — before and independent of the share action, so the outcome is visible without sharing. The share action SHALL remain available as the primary action and copy as a secondary action.

#### Scenario: Grid is visible on the result screen
- **WHEN** a round ends (won or lost)
- **THEN** the result screen shows the feedback grid rendered in-app, matching each tile's state, without requiring the user to share first

#### Scenario: Share stays primary
- **WHEN** the result screen is shown
- **THEN** the share action is presented as the primary action and copy as a secondary action
