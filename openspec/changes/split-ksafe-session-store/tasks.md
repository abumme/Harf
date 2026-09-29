# Tasks

Context: 1.3.1 shipped `android:allowBackup="false"` as the stopgap. These tasks make backup safe to
re-enable. KSafe facts this relies on: encryption is per-entry (`KSafeWriteMode.Plain` / `KSafePlain`),
`fileName` scopes the Keystore aliases and spells part of the AAD identity (so an existing store cannot be
renamed), and orphaned ciphertext is swept at startup while reads fall back to the declared default.

## 1. Session store

- [ ] 1.1 Add a second KSafe instance `KSafe(context, fileName = "session")` to the platform modules and inject it into `SessionStore`; verify the session survives relaunch and no other consumer resolves it.
- [ ] 1.2 Migrate an existing session on first launch: read the tokens from the old store, write them to the session store, clear the old keys; verify a signed-in install stays signed in across the upgrade.

## 2. Plain non-secrets

- [ ] 2.1 Write settings, entitlement cache, result log, round persistence and archive history through `KSafePlain` (or an explicit `KSafeWriteMode.Plain`); verify values still round-trip on every platform.
- [ ] 2.2 Migrate the existing encrypted values of those keys to plain once, keeping their current contents; verify stats and the streak are unchanged across the upgrade on a device that has the old store.

## 3. Backup rules

- [ ] 3.1 Add `res/xml/backup_rules.xml` (`fullBackupContent`, API 24–30) and `res/xml/data_extraction_rules.xml` (API 31+) excluding only `datastore/eu_anifantakis_ksafe_datastore_session.preferences_pb`; verify the exclusion names the file the store actually writes.
- [ ] 3.2 Re-enable `android:allowBackup="true"` with both rule files and drop the stopgap comment; verify lint is clean.

## 4. Verification on a device

- [ ] 4.1 Prove the restore: `adb shell bmgr backupnow <pkg>`, uninstall, restore (or migrate to a second device), then confirm stats and the streak come back and the session does not (a signed-in player re-authenticates).
- [ ] 4.2 Confirm no plaintext secret is in the backup set: dump the backup and check the session store file is absent and no token appears in the included files.
