package jp.healthsync.daily.sync

import java.time.LocalDate
import jp.healthsync.daily.summary.BATCH_SCHEMA
import jp.healthsync.daily.summary.DailySummaryJson
import jp.healthsync.daily.summary.DayInput
import jp.healthsync.daily.summary.SummaryAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchPlanTest {
    @Test
    fun encodeBatch_embedsDayJsonVerbatim() {
        val day = DailySummaryJson.encode(
            SummaryAggregator.aggregate(DayInput(date = LocalDate.of(2026, 10, 18))),
        )
        assertTrue(day.contains("\"schema\":\"health-daily/v1\""))
        assertEquals(
            """{"schema":"$BATCH_SCHEMA","days":[$day]}""",
            DailySummaryJson.encodeBatch(listOf(day)),
        )
    }

    @Test
    fun encodeBatch_keepsCallerOrderAndDoesNotReserialize() {
        val first = """{"schema":"health-daily/v1","date":"2026-10-18","title":"a\"b"}"""
        val second = """{"schema":"health-daily/v1","date":"2026-10-19","title":"c"}"""
        assertEquals(
            """{"schema":"health-daily/v1-batch","days":[$first,$second]}""",
            DailySummaryJson.encodeBatch(listOf(first, second)),
        )
    }

    @Test
    fun oneDay_stillUsesTheBatchEnvelope() {
        val date = LocalDate.of(2026, 10, 26)
        val day = """{"schema":"health-daily/v1","date":"2026-10-26","steps":1}"""
        val batches = outboundBatches(listOf(DatedPayload(date, day)))
        assertEquals(1, batches.size)
        assertEquals(listOf(date), batches.single().dates)
        assertEquals("""{"schema":"health-daily/v1-batch","days":[$day]}""", batches.single().json)
    }

    @Test
    fun sevenDays_areOnePostSortedAscending() {
        val start = LocalDate.of(2026, 10, 20)
        val days = (0 until 7).map { offset ->
            val date = start.plusDays(offset.toLong())
            DatedPayload(date, """{"schema":"health-daily/v1","date":"$date"}""")
        }
        val batches = outboundBatches(days.asReversed())
        assertEquals(1, batches.size)
        assertEquals(days.map { it.date }, batches.single().dates)
        assertEquals(
            DailySummaryJson.encodeBatch(days.map { it.json }),
            batches.single().json,
        )
    }

    @Test
    fun duplicateDate_keepsTheLaterPayload() {
        val earlier = LocalDate.of(2026, 10, 18)
        val date = LocalDate.of(2026, 10, 20)
        val stale = """{"schema":"health-daily/v1","date":"2026-10-20","steps":1}"""
        val newest = """{"schema":"health-daily/v1","date":"2026-10-20","steps":9}"""
        val other = """{"schema":"health-daily/v1","date":"2026-10-18","steps":4}"""
        val batches = outboundBatches(
            listOf(
                DatedPayload(date, stale),
                DatedPayload(earlier, other),
                DatedPayload(date, newest),
            ),
        )
        assertEquals(1, batches.size)
        assertEquals(listOf(earlier, date), batches.single().dates)
        assertEquals(
            """{"schema":"health-daily/v1-batch","days":[$other,$newest]}""",
            batches.single().json,
        )
    }

    @Test
    fun moreThan31Days_splitsOnlyTheOverflow() {
        assertEquals(31, MAX_BATCH_DAYS)
        val start = LocalDate.of(2026, 1, 1)
        val days = (0 until 32).map { offset ->
            val date = start.plusDays(offset.toLong())
            DatedPayload(date, """{"schema":"health-daily/v1","date":"$date"}""")
        }
        val batches = outboundBatches(days)
        assertEquals(listOf(31, 1), batches.map { it.dates.size })
        assertEquals(start, batches[0].dates.first())
        assertEquals(start.plusDays(30), batches[0].dates.last())
        assertEquals(start.plusDays(31), batches[1].dates.single())
        assertTrue(batches[0].json.startsWith("""{"schema":"health-daily/v1-batch","days":["""))
        assertTrue(batches[1].json.contains("\"date\":\"2026-02-01\""))
    }

    @Test
    fun exactly31Days_stayOneBatch() {
        val start = LocalDate.of(2026, 3, 1)
        val days = (0 until 31).map { offset ->
            DatedPayload(start.plusDays(offset.toLong()), "{}")
        }
        assertEquals(1, outboundBatches(days).size)
    }

    @Test
    fun batchDetail_matchesTheSingleDayMessage() {
        val date = LocalDate.of(2026, 10, 20)
        assertEquals(
            "2026-10-20 を送信しました（HTTP 200）",
            batchDetail(listOf(SendResult.Ok(date, 200))),
        )
    }

    @Test
    fun batchDetail_spansEveryDayInTheRequest() {
        val start = LocalDate.of(2026, 10, 20)
        val results = (0 until 7).map { offset ->
            SendResult.HttpError(start.plusDays(offset.toLong()), 503, "unavailable")
        }
        assertEquals(
            "2026-10-20〜2026-10-26（7日）の送信に失敗しました（HTTP 503）",
            batchDetail(results),
        )
    }
}
