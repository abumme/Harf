## Purpose

The app-shell is the launch surface of Harf: it initializes dependency injection, hosts the navigation graph, and presents the first screen so the game is reachable and navigable on every supported platform.

## ADDED Requirements

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
