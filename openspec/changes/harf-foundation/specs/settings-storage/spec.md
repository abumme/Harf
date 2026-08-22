## Purpose

Settings-storage provides durable, typed key–value persistence for user preferences and lightweight app state, so choices like the selected theme and first-run flags survive across launches on every platform.

## ADDED Requirements

### Requirement: Durable typed preference storage
The application SHALL provide a typed store for reading and writing user preferences that persists values across app launches on Android, iOS, desktop, and web.

#### Scenario: Value persists across relaunch
- **WHEN** a preference value is written and the app is relaunched
- **THEN** reading that preference returns the previously written value

#### Scenario: Default returned for unset preference
- **WHEN** a preference has never been written
- **THEN** reading it returns the defined default value rather than failing

#### Scenario: Overwrite replaces prior value
- **WHEN** a preference is written twice with different values
- **THEN** a subsequent read returns the most recently written value

### Requirement: Reactive preference observation
The store SHALL allow observing a preference so that dependent UI updates when the value changes during a session.

#### Scenario: Observer notified on change
- **WHEN** an observed preference is updated
- **THEN** the observer receives the new value without an app restart
