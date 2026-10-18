package jp.healthsync.daily.summary

import java.time.Duration
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TokyoScheduleTest {
    @Test
    fun before21_waitsUntilTonight() {
        val now = ZonedDateTime.parse("2026-10-18T10:00:00+09:00")
        assertEquals(Duration.ofHours(11), delayUntilNextSync(now.toInstant()))
        assertEquals("今日 21:00（日本時間）", describeNextSync(now))
    }

    @Test
    fun exactly21_movesToTomorrow() {
        val now = ZonedDateTime.parse("2026-10-18T21:00:00+09:00")
        assertEquals(Duration.ofHours(24), delayUntilNextSync(now.toInstant()))
        assertEquals("明日 21:00（日本時間）", describeNextSync(now))
    }

    @Test
    fun after21_waitsUntilTomorrow() {
        val now = ZonedDateTime.parse("2026-10-18T21:30:00+09:00")
        assertEquals(Duration.ofMinutes(23 * 60 + 30), delayUntilNextSync(now.toInstant()))
        assertEquals("明日 21:00（日本時間）", describeNextSync(now))
    }

    @Test
    fun afterMidnight_isStillToday() {
        val now = ZonedDateTime.parse("2026-10-18T00:30:00+09:00")
        assertEquals(Duration.ofMinutes(20 * 60 + 30), delayUntilNextSync(now.toInstant()))
        assertEquals("今日 21:00（日本時間）", describeNextSync(now))
    }
}
