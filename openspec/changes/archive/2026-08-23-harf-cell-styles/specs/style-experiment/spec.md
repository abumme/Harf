## Purpose

Style-experiment lets a new player actually experience all three mark styles across their first sessions, then asks which they preferred and locks it — giving the player a decided look and us a recorded signal, entirely offline.

## ADDED Requirements

### Requirement: First-days forced rotation
For a new player, the active mark style SHALL rotate across the three styles over the first three distinct calendar days of use (one style per day), so each is experienced before any preference is asked. A "session" for rotation is a calendar day.

#### Scenario: Style advances per day
- **WHEN** the player opens the app on their first, second, and third distinct days
- **THEN** the active style is the first, second, and third style respectively

#### Scenario: Same-day relaunch does not advance
- **WHEN** the player closes and reopens the app on the same calendar day
- **THEN** the active style and day position are unchanged

#### Scenario: Rotation state persists
- **WHEN** the app is closed and reopened mid-experiment
- **THEN** the rotation resumes from the correct day position rather than restarting

### Requirement: One-time preference prompt locks the choice
After the three-day rotation completes, the app SHALL prompt once for the player's preferred style, record the choice, and set it as the active style thereafter.

#### Scenario: Choice becomes active and prompt does not repeat
- **WHEN** the player picks a style in the post-rotation prompt
- **THEN** that style becomes active and the prompt is not shown again

#### Scenario: Dismissing keeps a defined default
- **WHEN** the player dismisses the prompt without choosing
- **THEN** a defined default style is active and the prompt is not forced repeatedly

### Requirement: Manual override any time
The player SHALL be able to change the mark style at any time from settings, overriding the experiment outcome.

#### Scenario: Settings change wins
- **WHEN** the player selects a different style in settings
- **THEN** that style becomes and stays active until changed again

### Requirement: Local capture of the selection
The app SHALL record the chosen style (and that it came from the experiment vs manual settings) locally for later analysis, without any network call in this change.

#### Scenario: Choice recorded locally
- **WHEN** a style is chosen via the experiment prompt or settings
- **THEN** a local record of the selection and its source is stored for later export
