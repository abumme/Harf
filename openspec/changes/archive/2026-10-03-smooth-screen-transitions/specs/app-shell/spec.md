## ADDED Requirements

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
