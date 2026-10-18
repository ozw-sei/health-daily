package jp.healthsync.daily.summary

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val SYNC_TIME: LocalTime = LocalTime.of(21, 0)

/** The next 21:00 strictly after [now] in [zone] (Asia/Tokyo for this app). */
fun nextSyncTime(now: Instant, zone: ZoneId = TOKYO): ZonedDateTime {
    val local = now.atZone(zone)
    var next = local.toLocalDate().atTime(SYNC_TIME).atZone(zone)
    if (!next.isAfter(local)) {
        next = next.plusDays(1)
    }
    return next
}

fun delayUntilNextSync(now: Instant, zone: ZoneId = TOKYO): Duration {
    return Duration.between(now.atZone(zone), nextSyncTime(now, zone))
}

/** "今日" when the next 21:00 is still on [now]'s civil date, otherwise "明日". */
fun describeNextSync(now: ZonedDateTime): String {
    val next = nextSyncTime(now.toInstant(), now.zone)
    val day = if (next.toLocalDate() == now.toLocalDate()) "今日" else "明日"
    return "$day 21:00（日本時間）"
}
