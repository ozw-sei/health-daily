package jp.healthsync.daily.sync

import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import jp.healthsync.daily.health.HealthAvailability
import jp.healthsync.daily.health.HealthDailyReader
import jp.healthsync.daily.net.WebhookClient
import jp.healthsync.daily.settings.AuthHeader
import jp.healthsync.daily.settings.AuthHeaderDecision
import jp.healthsync.daily.settings.SendLogEntry
import jp.healthsync.daily.settings.SettingsStore
import jp.healthsync.daily.summary.DailySummaryJson
import jp.healthsync.daily.summary.SummaryAggregator
import jp.healthsync.daily.summary.TOKYO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class SendResult {
    abstract val date: LocalDate

    data class Ok(override val date: LocalDate, val status: Int) : SendResult()
    data class HttpError(override val date: LocalDate, val status: Int, val body: String) : SendResult()
    data class NetworkError(override val date: LocalDate, val message: String) : SendResult()
    data class ConfigError(override val date: LocalDate, val message: String) : SendResult()
}

fun SendResult.userMessage(): String = when (this) {
    is SendResult.Ok -> "$date を送信しました（HTTP $status）"
    is SendResult.HttpError -> "$date の送信に失敗しました（HTTP $status）"
    is SendResult.NetworkError -> "$date の送信に失敗しました。$message"
    is SendResult.ConfigError -> message
}

fun SendResult.kind(): SendKind = when (this) {
    is SendResult.Ok -> SendKind.OK
    is SendResult.ConfigError -> SendKind.CONFIG
    is SendResult.NetworkError -> SendKind.NETWORK
    is SendResult.HttpError -> httpKind(status)
}

class SyncCoordinator(
    private val health: HealthDailyReader,
    private val webhook: WebhookClient,
    private val settings: SettingsStore,
) {
    private val gate = Mutex()

    suspend fun sendDate(date: LocalDate): SendResult =
        gate.withLock { sendLocked(listOf(date)) }.single()

    suspend fun sendRecentDays(days: Int, today: LocalDate = LocalDate.now(TOKYO)): List<SendResult> {
        val start = today.minusDays((days - 1).toLong())
        val dates = (0 until days).map { offset -> start.plusDays(offset.toLong()) }
        return gate.withLock { sendLocked(dates) }
    }

    /**
     * Reads every requested day, then POSTs one `health-daily/v1-batch` body per
     * chunk. A chunk is the whole sync unless it is longer than [MAX_BATCH_DAYS].
     * WorkManager retry observes that single response and runs again for the chunk,
     * not for each day inside it.
     */
    private suspend fun sendLocked(dates: List<LocalDate>): List<SendResult> {
        if (dates.isEmpty()) return emptyList()
        return try {
            deliverAll(dates)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val results = uniqueDatesAscending(dates).map { errorResult(it, error) }
            record(results)
            results
        }
    }

    private suspend fun deliverAll(dates: List<LocalDate>): List<SendResult> {
        val ready = when (val target = openDelivery()) {
            is DeliveryTarget.Rejected -> {
                val results = uniqueDatesAscending(dates).map { SendResult.ConfigError(it, target.message) }
                record(results)
                return results
            }
            is DeliveryTarget.Ready -> target
        }
        val payloads = dates.map { date ->
            val day = health.readDay(date)
            DatedPayload(date, DailySummaryJson.encode(SummaryAggregator.aggregate(day)))
        }
        val results = mutableListOf<SendResult>()
        for (batch in outboundBatches(payloads)) {
            val chunk = postBatch(ready, batch)
            record(chunk)
            results += chunk
        }
        return results
    }

    private suspend fun postBatch(ready: DeliveryTarget.Ready, batch: OutboundBatch): List<SendResult> {
        return try {
            val response = withContext(Dispatchers.IO) {
                webhook.post(ready.url, ready.auth, batch.json)
            }
            batch.dates.map { date ->
                if (response.ok) {
                    SendResult.Ok(date, response.status)
                } else {
                    SendResult.HttpError(date, response.status, response.detail)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            batch.dates.map { errorResult(it, error) }
        }
    }

    private fun openDelivery(): DeliveryTarget {
        val stored = settings.load()
        val url = stored.webhookUrl.trim()
        if (url.isEmpty()) {
            return DeliveryTarget.Rejected("Webhook URL が未設定です")
        }
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return DeliveryTarget.Rejected("URL は http:// または https:// で始めてください")
        }
        val auth = when (val decision = AuthHeader.resolve(stored.authHeaderName, stored.authHeaderValue)) {
            is AuthHeaderDecision.Invalid -> return DeliveryTarget.Rejected(decision.reason)
            is AuthHeaderDecision.Omit -> null
            is AuthHeaderDecision.Include -> decision
        }
        when (health.availability()) {
            HealthAvailability.UpdateRequired ->
                return DeliveryTarget.Rejected("Health Connect のインストールまたは更新が必要です")
            HealthAvailability.Unavailable ->
                return DeliveryTarget.Rejected("この端末では Health Connect を利用できません")
            HealthAvailability.Available -> Unit
        }
        return DeliveryTarget.Ready(url, auth)
    }

    private fun record(results: List<SendResult>) {
        if (results.isEmpty()) return
        val first = results.first()
        val status = when (first) {
            is SendResult.Ok -> first.status
            is SendResult.HttpError -> first.status
            is SendResult.NetworkError -> null
            is SendResult.ConfigError -> null
        }
        val dates = results.map { it.date }.sorted()
        val dateLabel = if (dates.size == 1) {
            dates.first().toString()
        } else {
            "${dates.first()}..${dates.last()}"
        }
        settings.appendLog(
            SendLogEntry(
                at = OffsetDateTime.now(TOKYO).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                date = dateLabel,
                status = status,
                ok = results.all { it is SendResult.Ok },
                detail = batchDetail(results),
            ),
        )
    }

    private fun errorResult(date: LocalDate, error: Exception): SendResult = when (error) {
        is SecurityException -> SendResult.ConfigError(
            date,
            "Health Connect の読み取りが拒否されました。権限とバックグラウンド読み取りを確認してください。",
        )
        is IllegalArgumentException -> SendResult.ConfigError(date, "URL または認証ヘッダーの形式が正しくありません")
        is IOException -> SendResult.NetworkError(date, error.message ?: "ネットワークエラー")
        else -> SendResult.NetworkError(date, error.message ?: error.javaClass.simpleName)
    }
}

private sealed class DeliveryTarget {
    data class Ready(val url: String, val auth: AuthHeaderDecision.Include?) : DeliveryTarget()
    data class Rejected(val message: String) : DeliveryTarget()
}
