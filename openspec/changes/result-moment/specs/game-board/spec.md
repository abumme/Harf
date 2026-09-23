# game-board Specification

## ADDED Requirements

### Requirement: Staggered mark-reveal animation on submit
When a guess is submitted, the board SHALL reveal that row's feedback marks with a per-tile staggered animation (each tile animating in sequence) rather than showing all marks instantly. The animation SHALL honor the platform's reduced-motion preference: when reduced motion is requested, marks SHALL appear without the staggered motion. The reveal SHALL not block further input once complete.

#### Scenario: Marks reveal in sequence
- **WHEN** the player submits a completed guess
- **THEN** the row's tiles reveal their marks one after another in a staggered sequence

#### Scenario: Reduced motion is honored
- **WHEN** the platform reduced-motion preference is enabled
- **THEN** the submitted row's marks appear without staggered animation

#### Scenario: Reveal does not trap input
- **WHEN** the reveal animation completes
- **THEN** the next row accepts input as normal
