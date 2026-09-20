# Spec Delta

## ADDED Requirements

### Requirement: Shared results identify archive and hard mode accurately
The identifying header of a shared result SHALL distinguish an archive replay from today's official daily round and SHALL identify hard mode when used. Sharing an archive replay SHALL not imply a new official daily result.

#### Scenario: Archive replay share
- **WHEN** the player shares a completed archived puzzle
- **THEN** the header identifies it as an archive result and includes its original puzzle day or number

#### Scenario: Hard-mode share
- **WHEN** the player shares a completed hard-mode round
- **THEN** the header includes a hard-mode marker while the emoji grid still reflects the actual attempts

#### Scenario: Ordinary daily share
- **WHEN** the player shares a normal official daily round
- **THEN** the existing daily header and grid remain valid
