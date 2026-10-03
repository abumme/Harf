# app-shell Specification

## Purpose
The app-shell is the launch surface of Harf: it initializes dependency injection, hosts the navigation graph, and presents the first screen so the game is reachable and navigable on every supported platform.

## Requirements

### Requirement: Application launches to a Home screen
The application SHALL launch to a Home destination on Android, iOS, desktop, and web without crashing, with dependency injection initialized before the first screen renders.

#### Scenario: Cold start on any platform
- **WHEN** the user opens the app from a cold start
- **THEN** dependency injection is initialized
- **AND** the Home destination is displayed within the themed app surface

#### Scenario: Dependency graph is available to screens
- **WHEN** a screen requests a dependency provided by the DI container
- **THEN** the dependency is resolved without a missing-binding error

### Requirement: Type-safe navigation between destinations
The application SHALL provide a single navigation graph with type-safe routes and SHALL allow navigating forward to a destination and back to the previous destination.

#### Scenario: Navigate forward
- **WHEN** code requests navigation to a declared destination
- **THEN** that destination is shown and added to the back stack

#### Scenario: Navigate back
- **WHEN** the user triggers back from a non-root destination
- **THEN** the previous destination is restored from the back stack

#### Scenario: Back at root exits gracefully
- **WHEN** the user triggers back while on the root (Home) destination
- **THEN** the app does not navigate to an empty screen and follows the platform's default exit/no-op behavior

### Requirement: Screens follow a shared MVI contract
Every feature screen SHALL expose immutable state, receive user intent as actions, and emit one-off events, so screen logic is consistent and testable across features.

#### Scenario: State drives the UI
- **WHEN** a screen's state changes
- **THEN** the UI recomposes to reflect the new state

#### Scenario: One-off events are delivered once
- **WHEN** a screen emits a one-off event (e.g. a message or navigation signal)
- **THEN** the event is handled exactly once and is not re-delivered on recomposition or configuration change

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

### Requirement: Navigation transitions are visually continuous
Every destination SHALL be drawn as an opaque page in the active edition's paper color, covering the whole window including the system-bar and safe-area strips. During a navigation transition, a page SHALL never show another page's content through it. On Android and iOS, forward and back navigation SHALL animate as a short horizontal slide with the page underneath moving at a reduced rate. Back by gesture SHALL use the same geometry as back by button and SHALL move in the direction of the swipe, following the finger. On desktop and web, navigation SHALL switch destinations instantly with no transition animation.

#### Scenario: Pages do not show through each other
- **WHEN** the user navigates from one destination to another, forward or back, on any platform
- **THEN** at every frame of the transition each visible page is opaque, and no text or control of one page is visible through another page

#### Scenario: No darkening flash at the end of a transition
- **WHEN** a forward or back transition finishes on iOS or Android
- **THEN** the screen does not change brightness or color abruptly on the final frame. The settled page looks the same as it did in the frame before.

#### Scenario: Mobile navigation slides
- **WHEN** the user opens a destination on Android or iOS
- **THEN** the new page slides in horizontally from the leading-to-trailing direction of travel, while the previous page moves a shorter distance underneath it, and the transition completes in roughly 300 ms

#### Scenario: Back gesture matches the back button
- **WHEN** the user goes back with the system back gesture (Android predictive back or iOS edge swipe) instead of the in-app back button
- **THEN** the pages move with the same geometry as the button's back transition, in the direction of the swipe (from the left edge the top page moves right, as with the button; from the right edge it moves left), and the top page tracks the gesture's progress

#### Scenario: Desktop and web switch instantly
- **WHEN** the user navigates between destinations on desktop or in a browser
- **THEN** the new destination replaces the previous one without a transition animation

### Requirement: Navigation ignores input while a transition runs
A navigation action (forward or back) SHALL take effect only when the destination that issued it is the settled, current destination. Actions issued from a destination that is still entering or already leaving SHALL be ignored. The in-app back action SHALL never remove the start (Home) destination.

#### Scenario: Double tap on back pops once
- **WHEN** the user taps the in-app back control twice in quick succession on a destination directly above Home
- **THEN** exactly one destination is popped, Home is shown, and the app never displays an empty screen

#### Scenario: Double tap on a forward action opens one destination
- **WHEN** the user taps a control that opens a destination twice in quick succession
- **THEN** the destination is opened once, and a single back returns to the previous destination

#### Scenario: Input on the leaving page is ignored
- **WHEN** a destination is animating out and the user taps a navigation control on it before the transition ends
- **THEN** the tap does not navigate
