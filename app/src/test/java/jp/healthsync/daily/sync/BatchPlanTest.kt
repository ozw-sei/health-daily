package jp.healthsync.daily.sync

import java.time.LocalDate
import jp.healthsync.daily.summary.BATCH_SCHEMA
import jp.healthsync.daily.summary.DailySummaryJson
import jp.healthsync.daily.summary.DayInput
import jp.healthsync.daily.summary.SummaryAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun sevenDays_splitIntoThreeThenThreeThenOneOldestFirst() {
        assertEquals(3, MAX_BATCH_DAYS)
        val start = LocalDate.of(2026, 10, 20)
        val days = (0 until 7).map { offset ->
            val date = start.plusDays(offset.toLong())
            DatedPayload(date, """{"schema":"health-daily/v1","date":"$date"}""")
        }
        val batches = outboundBatches(days.asReversed())
        assertEquals(listOf(3, 3, 1), batches.map { it.dates.size })
        assertEquals(days.map { it.date }, batches.flatMap { it.dates })
        assertEquals(
            listOf(
                DailySummaryJson.encodeBatch(days.take(3).map { it.json }),
                DailySummaryJson.encodeBatch(days.drop(3).take(3).map { it.json }),
                DailySummaryJson.encodeBatch(listOf(days[6].json)),
            ),
            batches.map { it.json },
        )
        assertTrue(batches.all { it.json.toByteArray(Charsets.UTF_8).size <= MAX_BATCH_BYTES })
    }

    @Test
    fun exactlyThreeDays_stayOneBatch() {
        val start = LocalDate.of(2026, 3, 1)
        val days = (0 until 3).map { offset ->
            val date = start.plusDays(offset.toLong())
            DatedPayload(date, """{"schema":"health-daily/v1","date":"$date"}""")
        }
        val batches = outboundBatches(days)
        assertEquals(1, batches.size)
        assertEquals(days.map { it.date }, batches.single().dates)
    }

    @Test
    fun fourTinyDays_splitOnlyOnTheCountLimit() {
        val start = LocalDate.of(2026, 1, 1)
        val days = (0 until 4).map { offset ->
            val date = start.plusDays(offset.toLong())
            DatedPayload(date, """{"schema":"health-daily/v1","date":"$date"}""")
        }
        val batches = outboundBatches(days)
        assertEquals(listOf(3, 1), batches.map { it.dates.size })
        assertEquals(start, batches[0].dates.first())
        assertEquals(start.plusDays(2), batches[0].dates.last())
        assertEquals(start.plusDays(3), batches[1].dates.single())
        assertTrue(batches[0].json.startsWith("""{"schema":"health-daily/v1-batch","days":["""))
        assertTrue(batches[1].json.contains("\"date\":\"2026-01-04\""))
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
    fun duplicateDates_doNotConsumeExtraChunkSlots() {
        val start = LocalDate.of(2026, 4, 1)
        val dates = (0 until 3).map { start.plusDays(it.toLong()) }
        val stale = dates.map { date ->
            DatedPayload(date, """{"schema":"health-daily/v1","date":"$date","steps":1}""")
        }
        val newest = dates.map { date ->
            DatedPayload(date, """{"schema":"health-daily/v1","date":"$date","steps":9}""")
        }
        val batches = outboundBatches(stale + newest)
        assertEquals(1, batches.size)
        assertEquals(dates, batches.single().dates)
        assertEquals(
            """{"schema":"health-daily/v1-batch","days":[${newest.joinToString(",") { it.json }}]}""",
            batches.single().json,
        )
        assertFalse(batches.single().json.contains(""""steps":1"""))
    }

    @Test
    fun defaultByteLimit_keepsTwoFatDaysAndSplitsTheThird() {
        assertEquals(16 * 1024, MAX_BATCH_BYTES)
        val start = LocalDate.of(2026, 6, 1)
        val days = (0 until 3).map { offset -> fatDay(start.plusDays(offset.toLong()), 6_000) }
        assertTrue(batchUtf8Bytes(days.take(2)) <= MAX_BATCH_BYTES)
        assertTrue(batchUtf8Bytes(days) > MAX_BATCH_BYTES)
        val batches = outboundBatches(days.asReversed())
        assertEquals(listOf(2, 1), batches.map { it.dates.size })
        assertEquals(days.take(2).map { it.date }, batches[0].dates)
        assertEquals(days[2].date, batches[1].dates.single())
        assertEquals(DailySummaryJson.encodeBatch(days.take(2).map { it.json }), batches[0].json)
        assertEquals(DailySummaryJson.encodeBatch(listOf(days[2].json)), batches[1].json)
        assertTrue(batches.all { it.json.toByteArray(Charsets.UTF_8).size <= MAX_BATCH_BYTES })
    }

    @Test
    fun sizeSplit_doesNotBorrowFromTheNextCountWindow() {
        val start = LocalDate.of(2026, 7, 1)
        val days = (0 until 4).map { offset -> fatDay(start.plusDays(offset.toLong()), 6_000) }
        assertTrue(batchUtf8Bytes(days.take(2)) <= MAX_BATCH_BYTES)
        assertTrue(batchUtf8Bytes(days.take(3)) > MAX_BATCH_BYTES)
        assertTrue(batchUtf8Bytes(days.drop(2)) <= MAX_BATCH_BYTES)
        val batches = outboundBatches(days)
        assertEquals(listOf(2, 1, 1), batches.map { it.dates.size })
        assertEquals(
            listOf(days[0].date, days[1].date, days[2].date, days[3].date),
            batches.flatMap { it.dates },
        )
        assertEquals(DailySummaryJson.encodeBatch(listOf(days[2].json)), batches[1].json)
        assertEquals(DailySummaryJson.encodeBatch(listOf(days[3].json)), batches[2].json)
    }

    @Test
    fun singleOversizeDay_isStillSentAlone() {
        val date = LocalDate.of(2026, 8, 1)
        val day = fatDay(date, 20_000)
        assertTrue(batchUtf8Bytes(listOf(day)) > MAX_BATCH_BYTES)
        val batches = outboundBatches(listOf(day))
        assertEquals(1, batches.size)
        assertEquals(listOf(date), batches.single().dates)
        assertEquals(DailySummaryJson.encodeBatch(listOf(day.json)), batches.single().json)
        assertTrue(batches.single().json.toByteArray(Charsets.UTF_8).size > MAX_BATCH_BYTES)
    }

    @Test
    fun oversizeDay_isSentAloneAndNeighborsAreNotDropped() {
        val start = LocalDate.of(2026, 5, 1)
        val huge = fatDay(start.plusDays(1), 20_000)
        val days = listOf(
            DatedPayload(start, """{"schema":"health-daily/v1","date":"$start","steps":1}"""),
            huge,
            DatedPayload(start.plusDays(2), """{"schema":"health-daily/v1","date":"${start.plusDays(2)}","steps":2}"""),
        )
        val batches = outboundBatches(days)
        assertEquals(listOf(1, 1, 1), batches.map { it.dates.size })
        assertEquals(days.map { it.date }, batches.flatMap { it.dates })
        assertEquals(DailySummaryJson.encodeBatch(listOf(days[0].json)), batches[0].json)
        assertEquals(DailySummaryJson.encodeBatch(listOf(huge.json)), batches[1].json)
        assertEquals(DailySummaryJson.encodeBatch(listOf(days[2].json)), batches[2].json)
        assertTrue(batches[1].json.toByteArray(Charsets.UTF_8).size > MAX_BATCH_BYTES)
        assertTrue(batches[0].json.toByteArray(Charsets.UTF_8).size <= MAX_BATCH_BYTES)
        assertTrue(batches[2].json.toByteArray(Charsets.UTF_8).size <= MAX_BATCH_BYTES)
    }

    @Test
    fun sizeLimit_allowsABodyEqualToTheLimitAndSplitsOneByteOver() {
        val first = DatedPayload(LocalDate.of(2026, 9, 1), """{"schema":"health-daily/v1","date":"2026-09-01"}""")
        val second = DatedPayload(LocalDate.of(2026, 9, 2), """{"schema":"health-daily/v1","date":"2026-09-02"}""")
        val exact = batchUtf8Bytes(listOf(first, second))
        assertTrue(batchUtf8Bytes(listOf(first)) < exact)
        val together = outboundBatches(listOf(second, first), maxBatchBytes = exact)
        assertEquals(1, together.size)
        assertEquals(listOf(first.date, second.date), together.single().dates)
        val split = outboundBatches(listOf(first, second), maxBatchBytes = exact - 1)
        assertEquals(listOf(1, 1), split.map { it.dates.size })
        assertEquals(listOf(first.date, second.date), split.flatMap { it.dates })
    }

    @Test
    fun sizeLimit_usesUtf8BytesNotCharacterCount() {
        val note = "あ".repeat(40)
        val days = (0 until 2).map { offset ->
            val date = LocalDate.of(2026, 8, 1).plusDays(offset.toLong())
            DatedPayload(date, """{"schema":"health-daily/v1","date":"$date","note":"$note"}""")
        }
        val combined = DailySummaryJson.encodeBatch(days.map { it.json })
        val bytes = combined.toByteArray(Charsets.UTF_8).size
        assertTrue(bytes > combined.length)
        val limit = bytes - 1
        assertTrue(combined.length <= limit)
        val batches = outboundBatches(days, maxBatchBytes = limit)
        assertEquals(listOf(1, 1), batches.map { it.dates.size })
        assertEquals(days.map { it.date }, batches.flatMap { it.dates })
        assertEquals(DailySummaryJson.encodeBatch(listOf(days[0].json)), batches[0].json)
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
        val results = (0 until 3).map { offset ->
            SendResult.HttpError(start.plusDays(offset.toLong()), 503, "unavailable")
        }
        assertEquals(
            "2026-10-20〜2026-10-22（3日）の送信に失敗しました（HTTP 503）",
            batchDetail(results),
        )
    }

    private fun fatDay(date: LocalDate, pad: Int): DatedPayload {
        return DatedPayload(
            date,
            """{"schema":"health-daily/v1","date":"$date","pad":"${"x".repeat(pad)}"}""",
        )
    }
}
