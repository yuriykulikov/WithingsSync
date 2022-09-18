package com.github.yuriykulikov.withingssync

import androidx.datastore.core.DataStore
import com.github.yuriykulikov.withingssync.common.Logger
import java.time.Instant
import kotlinx.coroutines.flow.first

class Syncer(
    private val lastSyncStore: DataStore<Long>,
    private val withingsAccess: WithingsAccess,
    private val googleFit: GoogleFit,
    private val logger: Logger,
) {
  suspend fun sync() {
    val lastSyncMillis = Instant.ofEpochSecond(lastSyncStore.data.first())
    logger.debug { "Performing sync, last sync was: $lastSyncMillis" }
    val now = Instant.now()
    val measures = withingsAccess.getMeasures(lastupdate = lastSyncMillis)
    googleFit.post(measures)
    lastSyncStore.updateData { now.epochSecond }
  }
}
