package uz.abumme.harfgame.data.stats

import eu.anifantakis.lib.ksafe.KSafe

/**
 * Durable "stats need uploading" marker. Set when a round finishes; survives app restart so an
 * offline result is retried on the next launch/foreground/reconnect and only cleared once the
 * server confirms the upload. Stats sync is last-write-wins over the whole snapshot, so a single
 * dirty flag is enough — no per-result outbox.
 */
class PendingUploadStore(private val ksafe: KSafe) {

    suspend fun markDirty() = ksafe.put(KEY, true)

    suspend fun isDirty(): Boolean = ksafe.get(KEY, false)

    suspend fun clear() = ksafe.put(KEY, false)

    companion object {
        private const val KEY = "stats.pendingUpload"
    }
}
