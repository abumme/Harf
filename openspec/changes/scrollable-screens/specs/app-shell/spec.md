## ADDED Requirements

### Requirement: Content screens are reachable on any viewport
Every content screen SHALL keep all of its content reachable regardless of viewport height — when the content is taller than the available space it SHALL scroll rather than clip. The game screen is exempt: its board and keyboard are a fixed-fit layout that must remain visible together and is adapted rather than scrolled.

#### Scenario: Tall content scrolls instead of clipping
- **WHEN** a content screen's content is taller than the available height (small device, landscape, or large font scale)
- **THEN** the screen SHALL allow scrolling to every element, and no control SHALL be permanently cut off

#### Scenario: Short content is not forced to scroll
- **WHEN** a content screen's content fits within the available height
- **THEN** the screen SHALL display without a scroll affordance and SHALL preserve its intended vertical alignment (e.g. the Home screen stays vertically centered)

#### Scenario: The game screen is exempt
- **WHEN** the game screen is shown
- **THEN** it SHALL NOT scroll its board/keyboard and SHALL instead keep them visible together as a fixed-fit layout
