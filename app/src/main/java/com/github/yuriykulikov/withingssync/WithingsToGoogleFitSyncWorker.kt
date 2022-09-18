package com.github.yuriykulikov.withingssync

import android.content.Context
import androidx.work.*
import com.github.yuriykulikov.withingssync.common.Logger
import com.github.yuriykulikov.withingssync.common.LoggerFactory
import org.koin.core.context.GlobalContext

class WithingsToGoogleFitSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  companion object {
    const val MAX_RETRIES = 5
    const val SYNC_JOB_NAME = "SyncJob"
  }

  private val syncer: Syncer by GlobalContext.get().inject()
  private val logger: Logger =
      GlobalContext.get().get<LoggerFactory>().createLogger("WithingsToGoogleFitSyncWorker")

  override suspend fun doWork(): Result {
    return when {
      !isActivityRecognitionPermissionApproved(appContext) -> {
        logger.warning { "No ActivityRecognitionPermissionApproved permission!" }
        retryOrBail(appContext.getString(R.string.error_android_permissions))
      }
      !hasGooglePermission(appContext) -> {
        logger.warning { "Google not signed in!" }
        retryOrBail(appContext.getString(R.string.error_oauth_permissions))
      }
      else -> {
        try {
          syncer.sync()
          Result.success()
        } catch (e: Exception) {
          logger.error(e) { "Sync failed $e" }
          retryOrBail(e.toString())
        }
      }
    }
  }

  private fun retryOrBail(message: String): Result {
    return when (runAttemptCount) {
      MAX_RETRIES -> Result.failure(errorData(message))
      else -> Result.retry()
    }
  }

  private fun errorData(message: String) = Data.Builder().putString("error", message).build()
}

fun enqueueSyncJob(context: Context) {
  val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

  val request =
      OneTimeWorkRequestBuilder<WithingsToGoogleFitSyncWorker>().setConstraints(constraints).build()

  WorkManager.getInstance(context)
      .enqueueUniqueWork(
          WithingsToGoogleFitSyncWorker.SYNC_JOB_NAME,
          ExistingWorkPolicy.REPLACE,
          request,
      )
}
