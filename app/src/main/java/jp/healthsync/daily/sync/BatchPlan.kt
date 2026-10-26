package jp.healthsync.daily.sync

import java.time.LocalDate
import jp.healthsync.daily.summary.DailySummaryJson

/** One sync stays a single POST unless it is longer than this. */
const val MAX_BATCH_DAYS = 31

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
 * then split only when [maxBatchSize] is exceeded.
 */
fun planBatches(
    days: List<DatedPayload>,
    maxBatchSize: Int = MAX_BATCH_DAYS,
): List<List<DatedPayload>> {
    require(maxBatchSize >= 1) { "maxBatchSize must be at least 1" }
    val newestByDate = LinkedHashMap<LocalDate, DatedPayload>()
    for (day in days) {
        newestByDate[day.date] = day
    }
    return newestByDate.values
        .sortedBy { it.date }
        .chunked(maxBatchSize)
}

/** The JSON bodies that a sync will POST, in send order. */
fun outboundBatches(
    days: List<DatedPayload>,
    maxBatchSize: Int = MAX_BATCH_DAYS,
): List<OutboundBatch> {
    return planBatches(days, maxBatchSize).map { batch ->
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
