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

    suspend fun sendDate(date: LocalDate): SendResult = gate.withLock { sendLocked(date) }

    suspend fun sendRecentDays(days: Int, today: LocalDate = LocalDate.now(TOKYO)): List<SendResult> {
        val start = today.minusDays((days - 1).toLong())
        return gate.withLock {
            (0 until days).map { offset -> sendLocked(start.plusDays(offset.toLong())) }
        }
    }

    private suspend fun sendLocked(date: LocalDate): SendResult {
        val result = try {
            postDay(date)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: SecurityException) {
            SendResult.ConfigError(
                date,
                "Health Connect の読み取りが拒否されました。権限とバックグラウンド読み取りを確認してください。",
            )
        } catch (error: IllegalArgumentException) {
            SendResult.ConfigError(date, "URL または認証ヘッダーの形式が正しくありません")
        } catch (error: IOException) {
            SendResult.NetworkError(date, error.message ?: "ネットワークエラー")
        } catch (error: Exception) {
            SendResult.NetworkError(date, error.message ?: error.javaClass.simpleName)
        }
        record(result)
        return result
    }

    private suspend fun postDay(date: LocalDate): SendResult {
        val stored = settings.load()
        val url = stored.webhookUrl.trim()
        if (url.isEmpty()) {
            return SendResult.ConfigError(date, "Webhook URL が未設定です")
        }
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return SendResult.ConfigError(date, "URL は http:// または https:// で始めてください")
        }
        when (val auth = AuthHeader.resolve(stored.authHeaderName, stored.authHeaderValue)) {
            is AuthHeaderDecision.Invalid -> return SendResult.ConfigError(date, auth.reason)
            is AuthHeaderDecision.Omit -> return deliver(date, url, null)
            is AuthHeaderDecision.Include -> return deliver(date, url, auth)
        }
    }

    private suspend fun deliver(
        date: LocalDate,
        url: String,
        auth: AuthHeaderDecision.Include?,
    ): SendResult {
        when (health.availability()) {
            HealthAvailability.UpdateRequired ->
                return SendResult.ConfigError(date, "Health Connect のインストールまたは更新が必要です")
            HealthAvailability.Unavailable ->
                return SendResult.ConfigError(date, "この端末では Health Connect を利用できません")
            HealthAvailability.Available -> Unit
        }
        val day = health.readDay(date)
        val json = DailySummaryJson.encode(SummaryAggregator.aggregate(day))
        val response = withContext(Dispatchers.IO) { webhook.post(url, auth, json) }
        return if (response.ok) {
            SendResult.Ok(date, response.status)
        } else {
            SendResult.HttpError(date, response.status, response.detail)
        }
    }

    private fun record(result: SendResult) {
        val status = when (result) {
            is SendResult.Ok -> result.status
            is SendResult.HttpError -> result.status
            is SendResult.NetworkError -> null
            is SendResult.ConfigError -> null
        }
        settings.appendLog(
            SendLogEntry(
                at = OffsetDateTime.now(TOKYO).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                date = result.date.toString(),
                status = status,
                ok = result is SendResult.Ok,
                detail = result.userMessage(),
            ),
        )
    }
}
