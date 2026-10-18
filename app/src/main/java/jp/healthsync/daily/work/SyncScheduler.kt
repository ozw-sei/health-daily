package jp.healthsync.daily.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Instant
import java.util.concurrent.TimeUnit
import jp.healthsync.daily.summary.delayUntilNextSync
import jp.healthsync.daily.summary.nextSyncTime

object SyncScheduler {
    const val UNIQUE_NAME = "health-daily-sync"
    const val KEY_DATE = "summary_date"

    fun enqueue(
        context: Context,
        policy: ExistingWorkPolicy,
        now: Instant = Instant.now(),
    ) {
        val next = nextSyncTime(now)
        val delayMs = delayUntilNextSync(now).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<DailySyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_DATE to next.toLocalDate().toString()))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }
}
