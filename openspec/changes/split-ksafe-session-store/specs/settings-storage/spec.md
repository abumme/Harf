# settings-storage Specification

## ADDED Requirements

### Requirement: Non-secret durable values survive an OS backup restore
Durable non-secret values (preferences, entitlement cache, result log, round persistence, archive history)
SHALL be stored so that they are readable after an OS-level backup and restore onto another device, where
no key material of the original device is available. On Android these values SHALL be written unencrypted
and SHALL be included in the app's backup set.

#### Scenario: Stats and streak return after a restore
- **WHEN** a player's install is backed up, then restored on another device
- **THEN** the result log, streak and preferences read back with their previous values

#### Scenario: A missing key never surfaces as a crash
- **WHEN** a stored value cannot be decrypted because its key is absent
- **THEN** reading it returns the declared default without throwing

### Requirement: Secrets are stored encrypted and excluded from backup
Authentication tokens SHALL be written encrypted, in a store separate from the non-secret values, and that
store SHALL be excluded from the platform's backup set, so no token leaves the device through a backup.

#### Scenario: The session does not travel in a backup
- **WHEN** an install with an active session is backed up and restored on another device
- **THEN** no token is present in the restored data and the player re-authenticates

#### Scenario: Secrets stay encrypted at rest
- **WHEN** an authentication token is written
- **THEN** it is stored as ciphertext under a platform-protected key, not as plaintext

### Requirement: Upgrading the storage layout preserves stored values
An app update that changes how a value is stored (encrypted to plain, or into another store) SHALL migrate
the existing value once on first launch, without losing it and without requiring the player to act.

#### Scenario: Values survive the upgrade
- **WHEN** an install created by the previous version is upgraded
- **THEN** preferences, stats and the streak read back unchanged, and a signed-in player stays signed in
