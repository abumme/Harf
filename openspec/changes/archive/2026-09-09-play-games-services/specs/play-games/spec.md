## Purpose

Integrate Google Play Games Services on Android so players can sign in, appear on leaderboards, and unlock achievements derived from their existing game history — kept entirely separate from the app's own account and stats-sync.

## ADDED Requirements

### Requirement: Android-only Play Games integration
Play Games Services SHALL be available only on Android; every other platform SHALL use a no-op implementation that never crashes and reports the feature as unavailable.

#### Scenario: Non-Android platform no-ops
- **WHEN** the app runs on iOS, desktop, or web
- **THEN** all Play Games actions SHALL do nothing and report unavailable, and the app SHALL build and run normally

#### Scenario: Missing Play Games IDs degrade gracefully
- **WHEN** leaderboard or achievement IDs are not configured
- **THEN** the corresponding submissions/unlocks SHALL be skipped without error

### Requirement: Play Games isolation from the app account system
Play Games sign-in SHALL be independent of the app's own identity: it SHALL NOT read, write, or link the app's session, tokens, backend account, or stats-sync in any way.

#### Scenario: PGS state does not affect app auth
- **WHEN** the player is signed in to Play Games, signed out, or sign-in fails
- **THEN** the app's anonymous/linked account, tokens, and stats-sync behavior SHALL be exactly the same as without Play Games

#### Scenario: No backend linking of the Play Games identity
- **WHEN** Play Games sign-in succeeds
- **THEN** the system SHALL NOT send a server auth code or otherwise associate the Play Games player with the backend account

### Requirement: Player sign-in for games features
The Android app SHALL attempt the standard Play Games v2 sign-in so that leaderboard and achievement actions can be attributed to a player, and SHALL tolerate a signed-out player.

#### Scenario: Sign-in attempted at startup
- **WHEN** the Android app starts
- **THEN** it SHALL initialize the Play Games SDK and attempt sign-in per the v2 flow

#### Scenario: Signed-out player does not break gameplay
- **WHEN** the player is not signed in to Play Games
- **THEN** gameplay SHALL continue normally and leaderboard/achievement submissions SHALL be skipped silently

### Requirement: Leaderboard and achievement updates from game results
When a daily round finishes, the Android app SHALL submit the player's aggregate results to the configured leaderboards and unlock any newly-earned achievements, using the app's existing computed history.

#### Scenario: Finishing a round updates leaderboards
- **WHEN** a daily round finishes and the player is signed in to Play Games
- **THEN** the app SHALL submit the player's best streak and total wins to their leaderboards

#### Scenario: Earning a milestone unlocks its achievement
- **WHEN** a result causes the player to reach an achievement threshold (e.g. first win, a 7- or 30-day streak, a first-guess solve, or 100 total wins)
- **THEN** the app SHALL unlock the corresponding achievement

#### Scenario: Submissions never block finishing a round
- **WHEN** a leaderboard/achievement submission fails or the player is signed out
- **THEN** the round result SHALL still be recorded and shown, unaffected

### Requirement: Access to native leaderboard and achievement UIs
The Android app SHALL let the player open the native Play Games leaderboards and achievements screens.

#### Scenario: Opening the leaderboards UI
- **WHEN** the player chooses to view leaderboards from the app
- **THEN** the app SHALL present the native Play Games leaderboards UI

#### Scenario: Opening the achievements UI
- **WHEN** the player chooses to view achievements from the app
- **THEN** the app SHALL present the native Play Games achievements UI
