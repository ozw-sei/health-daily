package jp.healthsync.daily.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import jp.healthsync.daily.summary.DayInput
import jp.healthsync.daily.summary.ExerciseInput
import jp.healthsync.daily.summary.HeartAggregate
import jp.healthsync.daily.summary.HydrationInput
import jp.healthsync.daily.summary.NutritionInput
import jp.healthsync.daily.summary.SleepSessionInput
import jp.healthsync.daily.summary.SleepStageInput
import jp.healthsync.daily.summary.SleepStageKind
import jp.healthsync.daily.summary.TOKYO
import jp.healthsync.daily.summary.TimedSample
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

enum class HealthAvailability {
    Available,
    UpdateRequired,
    Unavailable,
}

data class HealthSnapshot(
    val availability: HealthAvailability,
    val grantedCount: Int,
    val requestedCount: Int,
    val backgroundGranted: Boolean?,
)

class HealthDailyReader(context: Context) {
    private val appContext = context.applicationContext

    fun availability(): HealthAvailability {
        return when (HealthConnectClient.getSdkStatus(appContext)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthAvailability.UpdateRequired
            else -> HealthAvailability.Unavailable
        }
    }

    suspend fun snapshot(): HealthSnapshot {
        val availability = availability()
        if (availability != HealthAvailability.Available) {
            return HealthSnapshot(
                availability = availability,
                grantedCount = 0,
                requestedCount = HealthPermissions.read.size,
                backgroundGranted = null,
            )
        }
        val client = HealthConnectClient.getOrCreate(appContext)
        val granted = client.permissionController.getGrantedPermissions()
        val backgroundFeature = client.features.getFeatureStatus(
            androidx.health.connect.client.HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
        )
        val backgroundGranted = if (
            backgroundFeature ==
            androidx.health.connect.client.HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) {
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted
        } else {
            null
        }
        return HealthSnapshot(
            availability = availability,
            grantedCount = granted.count { it in HealthPermissions.read },
            requestedCount = HealthPermissions.read.size,
            backgroundGranted = backgroundGranted,
        )
    }

    suspend fun readDay(date: LocalDate): DayInput {
        val client = HealthConnectClient.getOrCreate(appContext)
        val granted = client.permissionController.getGrantedPermissions()
        val zone = TOKYO
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val day = TimeRangeFilter.between(dayStart, dayEnd)
        // Sleep that ends this morning started yesterday.
        val sleepWindow = TimeRangeFilter.between(
            date.minusDays(1).atStartOfDay(zone).toInstant(),
            dayEnd,
        )
        val sources = linkedSetOf<String>()

        val steps = aggregateOne(client, granted, StepsRecord::class, StepsRecord.COUNT_TOTAL, day, sources)
        val distance = aggregateOne(
            client,
            granted,
            DistanceRecord::class,
            DistanceRecord.DISTANCE_TOTAL,
            day,
            sources,
        )?.inMeters
        val active = aggregateOne(
            client,
            granted,
            ActiveCaloriesBurnedRecord::class,
            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            day,
            sources,
        )?.inKilocalories
        val total = aggregateOne(
            client,
            granted,
            TotalCaloriesBurnedRecord::class,
            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
            day,
            sources,
        )?.inKilocalories

        val heartResult = aggregateGranted(
            client,
            granted,
            HeartRateRecord::class,
            setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX),
            day,
        )
        sources.addOrigins(heartResult)
        val heartAggregate = heartResult?.let {
            HeartAggregate(
                avg = it[HeartRateRecord.BPM_AVG]?.toDouble(),
                min = it[HeartRateRecord.BPM_MIN]?.toInt(),
                max = it[HeartRateRecord.BPM_MAX]?.toInt(),
            )
        }

        val sleepRecords = readGranted(client, granted, SleepSessionRecord::class, sleepWindow)
        sources.addRecords(sleepRecords)
        val resting = readGranted(client, granted, RestingHeartRateRecord::class, day)
        sources.addRecords(resting)
        val hrv = readGranted(client, granted, HeartRateVariabilityRmssdRecord::class, day)
        sources.addRecords(hrv)
        val exercises = readGranted(client, granted, ExerciseSessionRecord::class, day)
        sources.addRecords(exercises)
        val weights = readGranted(client, granted, WeightRecord::class, day)
        sources.addRecords(weights)
        val bodyFat = readGranted(client, granted, BodyFatRecord::class, day)
        sources.addRecords(bodyFat)
        val nutrition = readGranted(client, granted, NutritionRecord::class, day)
        sources.addRecords(nutrition)
        val hydration = readGranted(client, granted, HydrationRecord::class, day)
        sources.addRecords(hydration)

        return DayInput(
            date = date,
            zone = zone,
            steps = steps,
            distanceMeters = distance,
            activeKcal = active,
            totalKcal = total,
            sleepSessions = sleepRecords.map { record ->
                SleepSessionInput(
                    start = record.startTime,
                    end = record.endTime,
                    startOffset = record.startZoneOffset,
                    endOffset = record.endZoneOffset,
                    stages = record.stages.map { stage ->
                        SleepStageInput(
                            start = stage.startTime,
                            end = stage.endTime,
                            kind = sleepKind(stage.stage),
                        )
                    },
                )
            },
            heartAggregate = heartAggregate,
            restingBpm = resting.map { TimedSample(it.time, it.beatsPerMinute.toDouble()) },
            hrvRmssdMs = hrv.map { TimedSample(it.time, it.heartRateVariabilityMillis) },
            exercises = exercises.map { record ->
                ExerciseInput(
                    type = exerciseTypeName(record.exerciseType),
                    start = record.startTime,
                    end = record.endTime,
                    startOffset = record.startZoneOffset,
                    endOffset = record.endZoneOffset,
                    title = record.title,
                )
            },
            weightsKg = weights.map { TimedSample(it.time, it.weight.inKilograms) },
            bodyFatPct = bodyFat.map { TimedSample(it.time, it.percentage.value) },
            nutrition = nutrition.map { record ->
                NutritionInput(
                    start = record.startTime,
                    kcal = record.energy?.inKilocalories,
                    proteinG = record.protein?.inGrams,
                    fatG = record.totalFat?.inGrams,
                    carbsG = record.totalCarbohydrate?.inGrams,
                )
            },
            hydration = hydration.map { HydrationInput(it.startTime, it.volume.inMilliliters) },
            sources = sources,
        )
    }

    private suspend fun <T : Any> aggregateOne(
        client: HealthConnectClient,
        granted: Set<String>,
        recordType: KClass<out Record>,
        metric: androidx.health.connect.client.aggregate.AggregateMetric<T>,
        filter: TimeRangeFilter,
        sources: MutableSet<String>,
    ): T? {
        val result = aggregateGranted(client, granted, recordType, setOf(metric), filter)
        sources.addOrigins(result)
        return result?.get(metric)
    }

    private suspend fun aggregateGranted(
        client: HealthConnectClient,
        granted: Set<String>,
        recordType: KClass<out Record>,
        metrics: Set<androidx.health.connect.client.aggregate.AggregateMetric<*>>,
        filter: TimeRangeFilter,
    ): AggregationResult? {
        if (HealthPermission.getReadPermission(recordType) !in granted) return null
        return guard { client.aggregate(AggregateRequest(metrics, filter)) }
    }

    private suspend fun <T : Record> readGranted(
        client: HealthConnectClient,
        granted: Set<String>,
        recordType: KClass<T>,
        filter: TimeRangeFilter,
    ): List<T> {
        if (HealthPermission.getReadPermission(recordType) !in granted) return emptyList()
        return guard { readAll(client, recordType, filter) }.orEmpty()
    }

    private suspend fun <T : Record> readAll(
        client: HealthConnectClient,
        recordType: KClass<T>,
        filter: TimeRangeFilter,
    ): List<T> {
        val out = ArrayList<T>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = filter,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            out += response.records
            pageToken = response.pageToken
            pages++
        } while (!pageToken.isNullOrEmpty() && pages < MAX_PAGES)
        return out
    }

    private suspend fun <T> guard(block: suspend () -> T): T? {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun sleepKind(stage: Int): SleepStageKind = when (stage) {
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageKind.DEEP
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageKind.LIGHT
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageKind.REM
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        -> SleepStageKind.AWAKE
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageKind.ASLEEP_UNSPECIFIED
        else -> SleepStageKind.UNKNOWN
    }

    private fun MutableSet<String>.addOrigins(result: AggregationResult?) {
        result?.dataOrigins?.forEach { addPackage(it.packageName) }
    }

    private fun MutableSet<String>.addRecords(records: List<Record>) {
        records.forEach { addPackage(it.metadata.dataOrigin.packageName) }
    }

    private fun MutableSet<String>.addPackage(packageName: String) {
        if (packageName.isNotBlank()) add(packageName)
    }

    private companion object {
        const val PAGE_SIZE = 1000
        const val MAX_PAGES = 50
    }
}
