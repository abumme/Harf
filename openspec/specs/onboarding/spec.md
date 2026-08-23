# onboarding Specification

## Purpose

Onboarding gives a first-time player just enough to start: that Harf is one hidden word a day solved in six tries, and what the feedback marks mean — shown once, then never in the way again.

## Requirements

### Requirement: One-time first-run introduction
On first launch, before the first round, the app SHALL show a brief introduction explaining the daily-word goal (one word a day, six attempts) and the feedback legend (on the spot / in the word / not in the word). It SHALL be dismissible.

#### Scenario: Shown on first launch
- **WHEN** the app is opened for the first time (intro not yet seen)
- **THEN** the introduction is shown before gameplay

#### Scenario: Not shown again after being seen or skipped
- **WHEN** the player dismisses or skips the introduction and later relaunches the app
- **THEN** the introduction is not shown again

### Requirement: Introduction never blocks play permanently
The introduction SHALL be skippable and SHALL not require any account, permission, or tutorial round to proceed to the game.

#### Scenario: Skip goes straight to the app
- **WHEN** the player chooses to skip the introduction
- **THEN** they proceed directly into the app with no further gating

### Requirement: Legend reflects the active mark style
The introduction's feedback legend SHALL render the three states using the currently active mark style, so what the player learns matches what they will see on the board.

#### Scenario: Legend matches board rendering
- **WHEN** the introduction shows the feedback legend
- **THEN** each state (correct/present/absent) is drawn in the active mark style and is distinguishable by shape as well as color
