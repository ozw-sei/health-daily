package jp.healthsync.daily.sync

import java.time.LocalDate
import jp.healthsync.daily.summary.DailySummaryJson

/**
 * At most this many days in one POST. Oldest days go out first.
 * Change this constant to raise or lower the count cap.
 */
const val MAX_BATCH_DAYS = 3

/**
 * UTF-8 byte cap for one serialized `health-daily/v1-batch` body.
 * A count window that would exceed this is split further, down to one day.
 * A single day that is already larger is still sent alone.
 */
const val MAX_BATCH_BYTES = 16 * 1024

data class DatedPayload(
    val date: LocalDate,
    val json: String,
)

data class OutboundBatch(
    val dates: List<LocalDate>,
    val json: String,
)

/**
 * Collapse duplicate dates (the later payload wins), sort by date ascending,
 * then cut into windows of [maxBatchSize]. A window whose serialized body
 * would exceed [maxBatchBytes] is split further, without borrowing days from
 * the next window. A single day over the byte cap stays in its own POST.
 */
fun planBatches(
    days: List<DatedPayload>,
    maxBatchSize: Int = MAX_BATCH_DAYS,
    maxBatchBytes: Int = MAX_BATCH_BYTES,
): List<List<DatedPayload>> {
    require(maxBatchSize >= 1) { "maxBatchSize must be at least 1" }
    require(maxBatchBytes >= 1) { "maxBatchBytes must be at least 1" }
    val newestByDate = LinkedHashMap<LocalDate, DatedPayload>()
    for (day in days) {
        newestByDate[day.date] = day
    }
    return newestByDate.values
        .sortedBy { it.date }
        .chunked(maxBatchSize)
        .flatMap { window -> splitWindowByBytes(window, maxBatchBytes) }
}

/**
 * Pack [window] oldest-first so each serialized batch fits in [maxBatchBytes].
 * Days outside [window] are not pulled in. A day already over the cap is
 * emitted by itself.
 */
private fun splitWindowByBytes(
    window: List<DatedPayload>,
    maxBatchBytes: Int,
): List<List<DatedPayload>> {
    if (window.isEmpty()) return emptyList()
    val batches = mutableListOf<List<DatedPayload>>()
    var current = mutableListOf<DatedPayload>()
    for (day in window) {
        if (current.isEmpty()) {
            current.add(day)
            continue
        }
        val candidate = current + day
        if (batchUtf8Bytes(candidate) <= maxBatchBytes) {
            current.add(day)
        } else {
            batches.add(current.toList())
            current = mutableListOf(day)
        }
    }
    if (current.isNotEmpty()) {
        batches.add(current.toList())
    }
    return batches
}

/** UTF-8 size of the webhook body these days would POST. */
fun batchUtf8Bytes(days: List<DatedPayload>): Int {
    return DailySummaryJson.encodeBatch(days.map { it.json }).toByteArray(Charsets.UTF_8).size
}

/** The JSON bodies that a sync will POST, in send order. */
fun outboundBatches(
    days: List<DatedPayload>,
    maxBatchSize: Int = MAX_BATCH_DAYS,
    maxBatchBytes: Int = MAX_BATCH_BYTES,
): List<OutboundBatch> {
    return planBatches(days, maxBatchSize, maxBatchBytes).map { batch ->
        OutboundBatch(
            dates = batch.map { it.date },
            json = DailySummaryJson.encodeBatch(batch.map { it.json }),
        )
    }
}

fun uniqueDatesAscending(dates: Iterable<LocalDate>): List<LocalDate> {
    val seen = LinkedHashMap<LocalDate, Unit>()
    for (date in dates) {
        seen[date] = Unit
    }
    return seen.keys.sorted()
}

fun batchDetail(results: List<SendResult>): String {
    require(results.isNotEmpty())
    if (results.size == 1) return results.first().userMessage()
    val dates = results.map { it.date }.sorted()
    val span = "${dates.first()}〜${dates.last()}（${dates.size}日）"
    return when (val head = results.first()) {
        is SendResult.Ok -> "${span}を送信しました（HTTP ${head.status}）"
        is SendResult.HttpError -> "${span}の送信に失敗しました（HTTP ${head.status}）"
        is SendResult.NetworkError -> "${span}の送信に失敗しました。${head.message}"
        is SendResult.ConfigError -> head.message
    }
}
