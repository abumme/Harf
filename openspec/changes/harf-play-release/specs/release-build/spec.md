## Purpose

Release-build defines what a submittable Harf artifact must be: a signed, minified Android release with the correct identity, versioning, and target-API level, produced reproducibly by CI.

## ADDED Requirements

### Requirement: Signed minified release artifact
The build SHALL produce a signed Android App Bundle for the release build type with code minification and resource shrinking enabled, signed with a release key sourced from secrets (never committed).

#### Scenario: Release AAB is produced and signed
- **WHEN** the release build runs with signing secrets available
- **THEN** a signed `.aab` for the release build type is produced

#### Scenario: No secrets committed
- **WHEN** the repository is inspected
- **THEN** no keystore or signing credential is present in version control

### Requirement: Correct application identity and versioning
The release SHALL use the final application id `uz.abumme.harfgame` and a monotonically increasing `versionCode` with a human-readable `versionName`.

#### Scenario: Identity and version present
- **WHEN** the release AAB metadata is inspected
- **THEN** the application id is `uz.abumme.harfgame` and it carries a defined versionCode/versionName

#### Scenario: Version increments per release
- **WHEN** a subsequent release is built
- **THEN** its versionCode is greater than the previous release's

### Requirement: Target-API compliance
The release SHALL target an Android API level meeting the current Play new-app requirement.

#### Scenario: Target level meets the floor
- **WHEN** the release is built for submission
- **THEN** its target API level is at or above the Play-required minimum in effect at submission time

### Requirement: Reproducible via CI
CI SHALL build the release artifact so a submission does not depend on a local machine.

#### Scenario: CI builds the release
- **WHEN** the release CI job runs
- **THEN** it outputs the signed release AAB as an artifact
