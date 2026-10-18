package jp.healthsync.daily.summary

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Turns a day's raw readings into the `health-daily/v1` summary.
 *
 * Rules (Asia/Tokyo unless [DayInput.zone] says otherwise):
 * - Sleep is every session whose end falls on that civil date (the night that
 *   finished that morning, plus any nap that also ended that day). Sessions are
 *   merged: start is the earliest start, end is the latest end.
 * - When any selected session has stages, stage minutes are the summed duration
 *   of those stages. Awake, awake-in-bed, and out-of-bed count as awake and are
 *   not part of total_min. Unspecified asleep and unknown stages count toward
 *   total_min only. Sessions in the merge that have no stages add their full
 *   length to total_min. Stage fields are 0 when stages exist and that bucket
 *   is empty. When no session has stages, total_min is the summed session length
 *   and the stage fields are null.
 * - Exercise belongs to the civil date of its start. Duration is the full session.
 * - Weight and body fat are the latest sample whose timestamp falls on that date.
 * - Nutrition and hydration include records whose start falls on that date, summed.
 *   Logging the same meal in two apps counts twice.
 * - Resting heart rate is the latest sample on that date. Avg/min/max prefer
 *   Health Connect's aggregate (it dedupes watches). If that aggregate is absent,
 *   they are computed from samples on that date.
 * - HRV is the average of RMSSD samples on that date.
 * - Missing measurements stay null. An empty exercise list is null, not [].
 * - sources is the sorted unique package list (possibly empty).
 */
object SummaryAggregator {
    fun aggregate(input: DayInput): DailySummary {
        val zone = input.zone
        val sleep = sleepSummary(input.sleepSessions, input.date, zone)
        val heart = heartSummary(input, zone)
        val exercises = input.exercises
            .filter { it.start.atZone(zone).toLocalDate() == input.date }
            .sortedBy { it.start }
            .map { exercise ->
                ExerciseSummary(
                    type = exercise.type.ifBlank { "UNKNOWN" },
                    start = formatIso(exercise.start, exercise.startOffset, zone),
                    end = formatIso(exercise.end, exercise.endOffset, zone),
                    durationMin = roundMinutes(Duration.between(exercise.start, exercise.end)),
                    title = exercise.title?.trim()?.ifEmpty { null },
                )
            }
        val nutrition = nutritionSummary(input.nutrition, input.date, zone)
        val hydrationValues = input.hydration
            .filter { it.start.atZone(zone).toLocalDate() == input.date }
            .map { it.milliliters }
        return DailySummary(
            date = input.date.toString(),
            timezone = zone.id,
            steps = input.steps,
            distanceM = input.distanceMeters?.let { round(it, 1) },
            activeKcal = input.activeKcal?.let { round(it, 1) },
            totalKcal = input.totalKcal?.let { round(it, 1) },
            sleep = sleep,
            heartRate = heart,
            hrvRmssdMs = averageOnDate(input.hrvRmssdMs, input.date, zone)?.let { round(it, 1) },
            exercise = exercises.ifEmpty { null },
            weightKg = latestOnDate(input.weightsKg, input.date, zone)?.let { round(it, 2) },
            bodyFatPct = latestOnDate(input.bodyFatPct, input.date, zone)?.let { round(it, 1) },
            nutrition = nutrition,
            hydrationMl = if (hydrationValues.isEmpty()) null else round(hydrationValues.sum(), 0),
            sources = input.sources.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
        )
    }

    private fun sleepSummary(
        sessions: List<SleepSessionInput>,
        date: LocalDate,
        zone: ZoneId,
    ): SleepSummary? {
        val selected = sessions.filter { it.end.atZone(zone).toLocalDate() == date }
        if (selected.isEmpty()) return null
        val earliest = selected.minBy { it.start }
        val latest = selected.maxBy { it.end }
        val staged = selected.filter { it.stages.isNotEmpty() }
        val unstaged = selected.filter { it.stages.isEmpty() }
        val unstagedDuration = unstaged.fold(Duration.ZERO) { acc, session ->
            acc.plus(Duration.between(session.start, session.end))
        }
        if (staged.isEmpty()) {
            return SleepSummary(
                start = formatIso(earliest.start, earliest.startOffset, zone),
                end = formatIso(latest.end, latest.endOffset, zone),
                totalMin = roundMinutes(unstagedDuration),
                deepMin = null,
                lightMin = null,
                remMin = null,
                awakeMin = null,
            )
        }
        var deep = Duration.ZERO
        var light = Duration.ZERO
        var rem = Duration.ZERO
        var awake = Duration.ZERO
        var unspecified = Duration.ZERO
        for (stage in staged.flatMap { it.stages }) {
            val length = Duration.between(stage.start, stage.end)
            when (stage.kind) {
                SleepStageKind.DEEP -> deep = deep.plus(length)
                SleepStageKind.LIGHT -> light = light.plus(length)
                SleepStageKind.REM -> rem = rem.plus(length)
                SleepStageKind.AWAKE -> awake = awake.plus(length)
                SleepStageKind.ASLEEP_UNSPECIFIED, SleepStageKind.UNKNOWN ->
                    unspecified = unspecified.plus(length)
            }
        }
        val asleep = deep.plus(light).plus(rem).plus(unspecified).plus(unstagedDuration)
        return SleepSummary(
            start = formatIso(earliest.start, earliest.startOffset, zone),
            end = formatIso(latest.end, latest.endOffset, zone),
            totalMin = roundMinutes(asleep),
            deepMin = roundMinutes(deep),
            lightMin = roundMinutes(light),
            remMin = roundMinutes(rem),
            awakeMin = roundMinutes(awake),
        )
    }

    private fun heartSummary(input: DayInput, zone: ZoneId): HeartRateSummary? {
        val samples = input.heartSamples.filter { it.time.atZone(zone).toLocalDate() == input.date }
        val aggregate = input.heartAggregate
        val avg: Double?
        val min: Int?
        val max: Int?
        if (aggregate != null) {
            avg = aggregate.avg?.let { round(it, 1) }
            min = aggregate.min
            max = aggregate.max
        } else if (samples.isNotEmpty()) {
            val values = samples.map { it.value }
            avg = round(values.average(), 1)
            min = values.minOrNull()?.let { round(it, 0).toInt() }
            max = values.maxOrNull()?.let { round(it, 0).toInt() }
        } else {
            avg = null
            min = null
            max = null
        }
        val resting = latestOnDate(input.restingBpm, input.date, zone)?.let { round(it, 0).toInt() }
        if (avg == null && min == null && max == null && resting == null) return null
        return HeartRateSummary(resting = resting, avg = avg, min = min, max = max)
    }

    private fun nutritionSummary(
        records: List<NutritionInput>,
        date: LocalDate,
        zone: ZoneId,
    ): NutritionSummary? {
        val day = records.filter { it.start.atZone(zone).toLocalDate() == date }
        if (day.isEmpty()) return null
        val kcal = sumPresent(day.map { it.kcal })?.let { round(it, 1) }
        val protein = sumPresent(day.map { it.proteinG })?.let { round(it, 1) }
        val fat = sumPresent(day.map { it.fatG })?.let { round(it, 1) }
        val carbs = sumPresent(day.map { it.carbsG })?.let { round(it, 1) }
        if (kcal == null && protein == null && fat == null && carbs == null) return null
        return NutritionSummary(kcal, protein, fat, carbs)
    }

    private fun sumPresent(values: List<Double?>): Double? {
        val present = values.filterNotNull()
        if (present.isEmpty()) return null
        return present.sum()
    }

    private fun latestOnDate(samples: List<TimedSample>, date: LocalDate, zone: ZoneId): Double? {
        return samples
            .filter { it.time.atZone(zone).toLocalDate() == date }
            .maxByOrNull { it.time }
            ?.value
    }

    private fun averageOnDate(samples: List<TimedSample>, date: LocalDate, zone: ZoneId): Double? {
        val values = samples.filter { it.time.atZone(zone).toLocalDate() == date }.map { it.value }
        if (values.isEmpty()) return null
        return values.average()
    }
}

internal fun round(value: Double, decimals: Int): Double {
    return BigDecimal(value.toString()).setScale(decimals, RoundingMode.HALF_UP).toDouble()
}

internal fun roundMinutes(duration: Duration): Int {
    val seconds = duration.seconds
    if (seconds <= 0L) return 0
    return ((seconds + 30L) / 60L).toInt()
}

internal fun formatIso(instant: Instant, offset: ZoneOffset?, zone: ZoneId): String {
    val zoneOffset = offset ?: zone.rules.getOffset(instant)
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atOffset(zoneOffset))
}
