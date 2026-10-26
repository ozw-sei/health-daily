package jp.healthsync.daily.summary

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

const val SCHEMA = "health-daily/v1"
const val BATCH_SCHEMA = "health-daily/v1-batch"
const val TIMEZONE_ID = "Asia/Tokyo"
val TOKYO: ZoneId = ZoneId.of(TIMEZONE_ID)

enum class SleepStageKind {
    DEEP,
    LIGHT,
    REM,
    AWAKE,
    ASLEEP_UNSPECIFIED,
    UNKNOWN,
}

data class SleepStageInput(
    val start: Instant,
    val end: Instant,
    val kind: SleepStageKind,
)

data class SleepSessionInput(
    val start: Instant,
    val end: Instant,
    val startOffset: ZoneOffset?,
    val endOffset: ZoneOffset?,
    val stages: List<SleepStageInput> = emptyList(),
)

data class ExerciseInput(
    val type: String,
    val start: Instant,
    val end: Instant,
    val startOffset: ZoneOffset?,
    val endOffset: ZoneOffset?,
    val title: String?,
)

data class TimedSample(
    val time: Instant,
    val value: Double,
)

data class NutritionInput(
    val start: Instant,
    val kcal: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val carbsG: Double?,
)

data class HydrationInput(
    val start: Instant,
    val milliliters: Double,
)

/** Heart-rate stats already aggregated by Health Connect (deduped across sources). */
data class HeartAggregate(
    val avg: Double?,
    val min: Int?,
    val max: Int?,
)

/**
 * One civil day of readings, already converted out of Health Connect types.
 * Bucketing (which night, which weigh-in) happens in [SummaryAggregator].
 */
data class DayInput(
    val date: LocalDate,
    val zone: ZoneId = TOKYO,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
    val sleepSessions: List<SleepSessionInput> = emptyList(),
    val heartAggregate: HeartAggregate? = null,
    val heartSamples: List<TimedSample> = emptyList(),
    val restingBpm: List<TimedSample> = emptyList(),
    val hrvRmssdMs: List<TimedSample> = emptyList(),
    val exercises: List<ExerciseInput> = emptyList(),
    val weightsKg: List<TimedSample> = emptyList(),
    val bodyFatPct: List<TimedSample> = emptyList(),
    val nutrition: List<NutritionInput> = emptyList(),
    val hydration: List<HydrationInput> = emptyList(),
    val sources: Set<String> = emptySet(),
)

data class SleepSummary(
    val start: String,
    val end: String,
    val totalMin: Int,
    val deepMin: Int?,
    val lightMin: Int?,
    val remMin: Int?,
    val awakeMin: Int?,
)

data class HeartRateSummary(
    val resting: Int?,
    val avg: Double?,
    val min: Int?,
    val max: Int?,
)

data class ExerciseSummary(
    val type: String,
    val start: String,
    val end: String,
    val durationMin: Int,
    val title: String?,
)

data class NutritionSummary(
    val kcal: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val carbsG: Double?,
)

data class DailySummary(
    val schema: String = SCHEMA,
    val date: String,
    val timezone: String,
    val steps: Long?,
    val distanceM: Double?,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val sleep: SleepSummary?,
    val heartRate: HeartRateSummary?,
    val hrvRmssdMs: Double?,
    val exercise: List<ExerciseSummary>?,
    val weightKg: Double?,
    val bodyFatPct: Double?,
    val nutrition: NutritionSummary?,
    val hydrationMl: Double?,
    val sources: List<String>,
)
