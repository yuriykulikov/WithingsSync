package com.github.yuriykulikov.withingssync

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.github.yuriykulikov.withingssync.common.Logger
import com.github.yuriykulikov.withingssync.common.LoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class NotificationListener : NotificationListenerService() {
  private val logger: Logger = get<LoggerFactory>().createLogger("NotificationListener")
  private val lastSeenNotification =
      DataStoreFactory.create(
          serializer = LongSerializer,
          produceFile = { dataStoreFile("lastSeenNotification") },
      )

  private val scope = CoroutineScope(Dispatchers.Main)

  override fun onListenerConnected() {
    super.onListenerConnected()
    scan()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    super.onNotificationPosted(sbn)
    scan()
  }

  private fun scan() {
    val mostRecentPostTime =
        activeNotifications
            .filter { it.packageName.contains("libra") }
            .map { it.postTime }
            .maxOrNull()
            ?: 0

    scope.launch {
      lastSeenNotification.updateData { prevSeenPostTime ->
        if (mostRecentPostTime > prevSeenPostTime) {
          enqueueSyncJob(applicationContext)
        }
        maxOf(mostRecentPostTime, prevSeenPostTime)
      }
    }
  }
}
