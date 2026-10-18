package jp.healthsync.daily.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.format.DateTimeParseException
import jp.healthsync.daily.HealthSyncApp
import jp.healthsync.daily.summary.TOKYO
import jp.healthsync.daily.sync.SendResult
import jp.healthsync.daily.sync.WorkDecision
import jp.healthsync.daily.sync.decideSync
import jp.healthsync.daily.sync.kind
import kotlinx.coroutines.CancellationException

class DailySyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val date = readDate()
        val decision = try {
            val graph = (applicationContext as HealthSyncApp).graph
            val sent = graph.coordinator.sendDate(date)
            decideSync(sent.kind(), runAttemptCount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            decideSync(SendResult.NetworkError(date, "").kind(), runAttemptCount)
        }
        if (decision != WorkDecision.RETRY) {
            SyncScheduler.enqueue(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return when (decision) {
            WorkDecision.SUCCESS -> Result.success()
            WorkDecision.RETRY -> Result.retry()
            WorkDecision.FAILURE -> Result.failure()
        }
    }

    private fun readDate(): LocalDate {
        val raw = inputData.getString(SyncScheduler.KEY_DATE)
        if (raw != null) {
            try {
                return LocalDate.parse(raw)
            } catch (_: DateTimeParseException) {
                // Fall through to "today" in Tokyo.
            }
        }
        return LocalDate.now(TOKYO)
    }
}
