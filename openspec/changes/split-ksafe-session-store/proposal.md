## Why

Every durable value — settings, entitlement cache, result log, in-progress and archived rounds, and the
auth session — goes through one `KSafe(androidApplication())` instance (`di/PlatformModule.android.kt`).
KSafe 3.2.0 encrypts by default (AES-256-GCM) under a non-exportable Android Keystore key, so Android
Auto Backup copies ciphertext the new device cannot open: `cleanupOrphanedCiphertext()` drops those
entries at startup and `getDirectRaw` returns the declared default, so a restored install silently loses
stats, the streak and the onboarding flag. Not a crash — a silent reset the player cannot explain.

1.3.1 ships `android:allowBackup="false"` so nothing is promised and nothing is silently lost. That is a
stopgap: a player who changes phones still starts over. A signed-in player gets stats back from the
server (`SyncManager`); a guest — the majority on a first install — does not.

Making backup work is not a matter of switching KSafe to `KSafeWriteMode.Plain`: the refresh token lives
in the same store, and plain writes would ship it to Google's backup in the clear. The split is the fix:
secrets stay encrypted and excluded from backup, everything else is plain and restorable.

## What Changes

- **A separate session store.** The auth session moves to its own `KSafe(context, fileName = "session")`
  instance, which keeps the default encrypted mode. `SessionStore` takes that instance; nothing else does.
- **Plain writes for non-secrets.** Settings, entitlement cache, result log, round persistence and archive
  history write through `KSafePlain` (or an explicit `KSafeWriteMode.Plain`), so their values survive a
  restore on a device whose Keystore never held our key.
- **Backup includes the game, excludes the session.** `android:allowBackup` goes back on with
  `dataExtractionRules` (and `fullBackupContent` for API 24–30) excluding only the session store file, so
  the token never leaves the device while stats and the streak follow the player.
- **One-time migration.** On first launch after the update, existing values are re-read under the old
  (encrypted) store and rewritten plain, and the session token is moved into the session store. A store's
  `fileName` is part of KSafe's AAD identity and its Keystore alias scope, so renaming the existing store
  is not an option — the new store is created alongside it and the old entries are migrated, then removed.

Non-goals: iOS/desktop/web backup behaviour (Keychain and the OS vaults have their own semantics); any
change to what is stored or to the sync protocol.

## Capabilities

### Modified Capabilities
- `settings-storage`: require that durable non-secret values are readable after an OS-level backup restore
  on another device, that secrets are stored encrypted in a store excluded from backup, and that an
  upgrade migrates existing values without losing them.

## Impact

- `sharedUI/.../di/PlatformModule.android.kt` (and the other platform modules): a second named KSafe instance.
- `sharedUI/.../data/auth/SessionStore.kt`: takes the session instance.
- `sharedUI/.../settings/AppSettings.kt`, `data/stats/*`, `data/archive/*`: plain writes.
- `androidApp/src/main/AndroidManifest.xml`: `allowBackup` back on, `dataExtractionRules`, `fullBackupContent`.
- `androidApp/src/main/res/xml/`: new backup rule files excluding the session store.
- A one-time migration path plus its test.

Verification note: the restore path cannot be proven by unit tests alone — it needs
`adb shell bmgr backupnow` / restore on a device, or a real device migration.
