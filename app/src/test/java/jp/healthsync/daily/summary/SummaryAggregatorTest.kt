package jp.healthsync.daily.summary

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryAggregatorTest {
    private val date = LocalDate.of(2026, 10, 18)
    private val offset = ZoneOffset.ofHours(9)

    @Test
    fun fullDay_mapsMeasurementsAndJson() {
        val summary = SummaryAggregator.aggregate(fullDay())

        assertEquals(8123L, summary.steps)
        assertEquals(6200.5, summary.distanceM)
        assertEquals(340.2, summary.activeKcal)
        assertEquals(2140.0, summary.totalKcal)
        assertEquals("2026-10-17T23:40:00+09:00", summary.sleep?.start)
        assertEquals("2026-10-18T07:10:00+09:00", summary.sleep?.end)
        assertEquals(420, summary.sleep?.totalMin)
        assertEquals(110, summary.sleep?.deepMin)
        assertEquals(190, summary.sleep?.lightMin)
        assertEquals(120, summary.sleep?.remMin)
        assertEquals(30, summary.sleep?.awakeMin)
        assertEquals(55, summary.heartRate?.resting)
        assertEquals(80.0, summary.heartRate?.avg)
        assertEquals(60, summary.heartRate?.min)
        assertEquals(100, summary.heartRate?.max)
        assertEquals(45.0, summary.hrvRmssdMs)
        assertEquals(1, summary.exercise?.size)
        assertEquals("RUNNING", summary.exercise?.first()?.type)
        assertEquals(32, summary.exercise?.first()?.durationMin)
        assertEquals("朝ラン", summary.exercise?.first()?.title)
        assertEquals(70.4, summary.weightKg)
        assertEquals(18.3, summary.bodyFatPct)
        assertEquals(700.0, summary.nutrition?.kcal)
        assertEquals(40.5, summary.nutrition?.proteinG)
        assertEquals(20.0, summary.nutrition?.fatG)
        assertEquals(55.0, summary.nutrition?.carbsG)
        assertEquals(1800.0, summary.hydrationMl)
        assertEquals(
            listOf(
                "com.eufylife.android",
                "com.fitbit.FitbitMobile",
                "com.google.android.apps.fitness",
            ),
            summary.sources,
        )
        assertEquals(FULL_JSON, DailySummaryJson.encode(summary))
    }

    @Test
    fun emptyDay_usesNullsAndEmptySources() {
        val summary = SummaryAggregator.aggregate(DayInput(date = date))
        assertNull(summary.steps)
        assertNull(summary.sleep)
        assertNull(summary.heartRate)
        assertNull(summary.exercise)
        assertNull(summary.nutrition)
        assertEquals(emptyList<String>(), summary.sources)
        assertEquals(
            """{"schema":"health-daily/v1","date":"2026-10-18","timezone":"Asia/Tokyo","steps":null,"distance_m":null,"active_kcal":null,"total_kcal":null,"sleep":null,"heart_rate":null,"hrv_rmssd_ms":null,"exercise":null,"weight_kg":null,"body_fat_pct":null,"nutrition":null,"hydration_ml":null,"sources":[]}""",
            DailySummaryJson.encode(summary),
        )
    }

    @Test
    fun sleep_keepsSessionsThatEndOnTheDateAndMergesStages() {
        val night = session(
            start = "2026-10-17T14:00:00Z",
            end = "2026-10-17T22:00:00Z",
            stages = listOf(stage("2026-10-17T14:00:00Z", "2026-10-17T16:00:00Z", SleepStageKind.DEEP)),
        )
        val nap = session(
            start = "2026-10-18T03:00:00Z",
            end = "2026-10-18T04:00:00Z",
            stages = emptyList(),
        )
        val nextMorning = session(
            start = "2026-10-18T14:00:00Z",
            end = "2026-10-18T22:00:00Z",
        )
        val summary = SummaryAggregator.aggregate(
            DayInput(date = date, sleepSessions = listOf(nextMorning, nap, night)),
        )
        assertEquals("2026-10-17T23:00:00+09:00", summary.sleep?.start)
        assertEquals("2026-10-18T13:00:00+09:00", summary.sleep?.end)
        // 120 deep + 60 unstaged nap. The session that ends on the 19th is dropped.
        assertEquals(180, summary.sleep?.totalMin)
        assertEquals(120, summary.sleep?.deepMin)
        assertEquals(0, summary.sleep?.lightMin)
        assertEquals(0, summary.sleep?.awakeMin)
    }

    @Test
    fun sleepWithoutStages_leavesStageFieldsNull() {
        val summary = SummaryAggregator.aggregate(
            DayInput(
                date = date,
                sleepSessions = listOf(
                    session(start = "2026-10-17T15:00:00Z", end = "2026-10-17T15:01:30Z"),
                ),
            ),
        )
        assertEquals(2, summary.sleep?.totalMin)
        assertNull(summary.sleep?.deepMin)
        assertNull(summary.sleep?.awakeMin)
    }

    @Test
    fun unspecifiedSleepCountsTowardTotalOnly() {
        val summary = SummaryAggregator.aggregate(
            DayInput(
                date = date,
                sleepSessions = listOf(
                    session(
                        start = "2026-10-17T15:00:00Z",
                        end = "2026-10-17T16:00:00Z",
                        stages = listOf(
                            stage("2026-10-17T15:00:00Z", "2026-10-17T15:10:00Z", SleepStageKind.UNKNOWN),
                            stage("2026-10-17T15:10:00Z", "2026-10-17T15:30:00Z", SleepStageKind.ASLEEP_UNSPECIFIED),
                            stage("2026-10-17T15:30:00Z", "2026-10-17T16:00:00Z", SleepStageKind.DEEP),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(60, summary.sleep?.totalMin)
        assertEquals(30, summary.sleep?.deepMin)
        assertEquals(0, summary.sleep?.remMin)
    }

    @Test
    fun exercise_usesStartDateAndSortsAndEscapesTitle() {
        val summary = SummaryAggregator.aggregate(
            DayInput(
                date = date,
                exercises = listOf(
                    ExerciseInput(
                        type = "WALKING",
                        start = Instant.parse("2026-10-18T06:00:00Z"),
                        end = Instant.parse("2026-10-18T06:10:00Z"),
                        startOffset = offset,
                        endOffset = offset,
                        title = "夕方",
                    ),
                    ExerciseInput(
                        type = "RUNNING",
                        start = Instant.parse("2026-10-17T03:00:00Z"),
                        end = Instant.parse("2026-10-17T04:00:00Z"),
                        startOffset = offset,
                        endOffset = offset,
                        title = "yesterday",
                    ),
                    ExerciseInput(
                        type = "YOGA",
                        start = Instant.parse("2026-10-18T00:00:00Z"),
                        end = Instant.parse("2026-10-18T00:20:00Z"),
                        startOffset = offset,
                        endOffset = offset,
                        title = "5km \"easy\"",
                    ),
                ),
            ),
        )
        assertEquals(listOf("YOGA", "WALKING"), summary.exercise?.map { it.type })
        assertEquals(20, summary.exercise?.first()?.durationMin)
        assertEquals(
            """[{"type":"YOGA","start":"2026-10-18T09:00:00+09:00","end":"2026-10-18T09:20:00+09:00","duration_min":20,"title":"5km \"easy\""},{"type":"WALKING","start":"2026-10-18T15:00:00+09:00","end":"2026-10-18T15:10:00+09:00","duration_min":10,"title":"夕方"}]""",
            DailySummaryJson.encode(summary).substringAfter("\"exercise\":").substringBefore(",\"weight_kg\""),
        )
    }

    @Test
    fun latestWeightAndBodyFat_ignoreOtherDays() {
        val summary = SummaryAggregator.aggregate(
            DayInput(
                date = date,
                weightsKg = listOf(
                    TimedSample(Instant.parse("2026-10-17T03:00:00Z"), 80.0),
                    TimedSample(Instant.parse("2026-10-17T16:00:00Z"), 70.12),
                    TimedSample(Instant.parse("2026-10-18T00:00:00Z"), 70.4),
                ),
                bodyFatPct = listOf(
                    TimedSample(Instant.parse("2026-10-18T00:00:00Z"), 18.25),
                    TimedSample(Instant.parse("2026-10-18T23:00:00Z"), 99.0),
                ),
            ),
        )
        assertEquals(70.4, summary.weightKg)
        assertEquals(18.3, summary.bodyFatPct)
    }

    @Test
    fun nutritionAndHydration_sumOnlyRecordsStartingThatDay() {
        val summary = SummaryAggregator.aggregate(
            DayInput(
                date = date,
                nutrition = listOf(
                    NutritionInput(Instant.parse("2026-10-17T03:00:00Z"), 10.0, 1.0, 1.0, 1.0),
                    NutritionInput(Instant.parse("2026-10-18T01:00:00Z"), 500.0, 30.0, null, 40.0),
                    NutritionInput(Instant.parse("2026-10-18T06:00:00Z"), 200.4, 10.25, null, 15.0),
                ),
                hydration = listOf(
                    HydrationInput(Instant.parse("2026-10-17T10:00:00Z"), 500.0),
                    HydrationInput(Instant.parse("2026-10-18T01:00:00Z"), 100.4),
                    HydrationInput(Instant.parse("2026-10-18T02:00:00Z"), 100.5),
                ),
            ),
        )
        assertEquals(700.4, summary.nutrition?.kcal)
        assertEquals(40.3, summary.nutrition?.proteinG)
        assertNull(summary.nutrition?.fatG)
        assertEquals(55.0, summary.nutrition?.carbsG)
        assertEquals(201.0, summary.hydrationMl)
    }

    @Test
    fun heartAggregate_winsOverSamples_restingIsLatestThatDay() {
        val summary = SummaryAggregator.aggregate(
            DayInput(
                date = date,
                heartAggregate = HeartAggregate(avg = 72.0, min = 50, max = 140),
                heartSamples = listOf(TimedSample(Instant.parse("2026-10-18T01:00:00Z"), 200.0)),
                restingBpm = listOf(
                    TimedSample(Instant.parse("2026-10-17T10:00:00Z"), 40.0),
                    TimedSample(Instant.parse("2026-10-18T00:00:00Z"), 54.0),
                    TimedSample(Instant.parse("2026-10-18T02:00:00Z"), 57.2),
                ),
                hrvRmssdMs = listOf(
                    TimedSample(Instant.parse("2026-10-17T12:00:00Z"), 10.0),
                    TimedSample(Instant.parse("2026-10-18T01:00:00Z"), 40.0),
                    TimedSample(Instant.parse("2026-10-18T02:00:00Z"), 50.0),
                ),
            ),
        )
        assertEquals(72.0, summary.heartRate?.avg)
        assertEquals(50, summary.heartRate?.min)
        assertEquals(57, summary.heartRate?.resting)
        assertEquals(45.0, summary.hrvRmssdMs)
    }

    @Test
    fun heartSamples_usedWhenAggregateIsAbsent() {
        val summary = SummaryAggregator.aggregate(
            DayInput(
                date = date,
                heartSamples = listOf(
                    TimedSample(Instant.parse("2026-10-18T01:00:00Z"), 60.0),
                    TimedSample(Instant.parse("2026-10-18T02:00:00Z"), 80.0),
                    TimedSample(Instant.parse("2026-10-18T03:00:00Z"), 101.0),
                    TimedSample(Instant.parse("2026-10-17T03:00:00Z"), 10.0),
                ),
            ),
        )
        assertEquals(80.3, summary.heartRate?.avg)
        assertEquals(60, summary.heartRate?.min)
        assertEquals(101, summary.heartRate?.max)
        assertNull(summary.heartRate?.resting)
    }

    @Test
    fun restingAlone_stillProducesHeartObject() {
        val summary = SummaryAggregator.aggregate(
            DayInput(
                date = date,
                restingBpm = listOf(TimedSample(Instant.parse("2026-10-18T01:00:00Z"), 58.0)),
            ),
        )
        assertEquals(58, summary.heartRate?.resting)
        assertNull(summary.heartRate?.avg)
        assertNull(summary.heartRate?.min)
        assertNull(summary.heartRate?.max)
    }

    @Test
    fun sources_areTrimmedDedupedAndSorted() {
        val summary = SummaryAggregator.aggregate(
            DayInput(date = date, sources = setOf(" b.app ", "a.app", "", "a.app")),
        )
        assertEquals(listOf("a.app", "b.app"), summary.sources)
    }

    private fun fullDay(): DayInput {
        return DayInput(
            date = date,
            steps = 8123,
            distanceMeters = 6200.5,
            activeKcal = 340.2,
            totalKcal = 2140.0,
            sleepSessions = listOf(
                session(
                    start = "2026-10-17T14:40:00Z",
                    end = "2026-10-17T22:10:00Z",
                    stages = listOf(
                        stage("2026-10-17T14:40:00Z", "2026-10-17T15:10:00Z", SleepStageKind.AWAKE),
                        stage("2026-10-17T15:10:00Z", "2026-10-17T17:10:00Z", SleepStageKind.LIGHT),
                        stage("2026-10-17T17:10:00Z", "2026-10-17T19:00:00Z", SleepStageKind.DEEP),
                        stage("2026-10-17T19:00:00Z", "2026-10-17T21:00:00Z", SleepStageKind.REM),
                        stage("2026-10-17T21:00:00Z", "2026-10-17T22:10:00Z", SleepStageKind.LIGHT),
                    ),
                ),
            ),
            heartSamples = listOf(
                TimedSample(Instant.parse("2026-10-18T01:00:00Z"), 60.0),
                TimedSample(Instant.parse("2026-10-18T02:00:00Z"), 80.0),
                TimedSample(Instant.parse("2026-10-18T03:00:00Z"), 100.0),
            ),
            restingBpm = listOf(TimedSample(Instant.parse("2026-10-18T00:00:00Z"), 55.0)),
            hrvRmssdMs = listOf(
                TimedSample(Instant.parse("2026-10-18T01:00:00Z"), 40.0),
                TimedSample(Instant.parse("2026-10-18T02:00:00Z"), 50.0),
            ),
            exercises = listOf(
                ExerciseInput(
                    type = "RUNNING",
                    start = Instant.parse("2026-10-18T00:00:00Z"),
                    end = Instant.parse("2026-10-18T00:32:00Z"),
                    startOffset = offset,
                    endOffset = offset,
                    title = "朝ラン",
                ),
            ),
            weightsKg = listOf(
                TimedSample(Instant.parse("2026-10-18T00:00:00Z"), 70.12),
                TimedSample(Instant.parse("2026-10-18T01:00:00Z"), 70.4),
            ),
            bodyFatPct = listOf(TimedSample(Instant.parse("2026-10-18T01:00:00Z"), 18.25)),
            nutrition = listOf(
                NutritionInput(Instant.parse("2026-10-18T01:00:00Z"), 500.0, 30.0, 20.0, 40.0),
                NutritionInput(Instant.parse("2026-10-18T06:00:00Z"), 200.0, 10.5, null, 15.0),
            ),
            hydration = listOf(HydrationInput(Instant.parse("2026-10-18T01:00:00Z"), 1800.0)),
            sources = setOf(
                "com.fitbit.FitbitMobile",
                "com.google.android.apps.fitness",
                "com.eufylife.android",
            ),
        )
    }

    private fun session(
        start: String,
        end: String,
        stages: List<SleepStageInput> = emptyList(),
    ): SleepSessionInput {
        return SleepSessionInput(
            start = Instant.parse(start),
            end = Instant.parse(end),
            startOffset = offset,
            endOffset = offset,
            stages = stages,
        )
    }

    private fun stage(start: String, end: String, kind: SleepStageKind): SleepStageInput {
        return SleepStageInput(Instant.parse(start), Instant.parse(end), kind)
    }

    private companion object {
        val FULL_JSON = """
            {"schema":"health-daily/v1","date":"2026-10-18","timezone":"Asia/Tokyo","steps":8123,"distance_m":6200.5,"active_kcal":340.2,"total_kcal":2140,"sleep":{"start":"2026-10-17T23:40:00+09:00","end":"2026-10-18T07:10:00+09:00","total_min":420,"deep_min":110,"light_min":190,"rem_min":120,"awake_min":30},"heart_rate":{"resting":55,"avg":80,"min":60,"max":100},"hrv_rmssd_ms":45,"exercise":[{"type":"RUNNING","start":"2026-10-18T09:00:00+09:00","end":"2026-10-18T09:32:00+09:00","duration_min":32,"title":"朝ラン"}],"weight_kg":70.4,"body_fat_pct":18.3,"nutrition":{"kcal":700,"protein_g":40.5,"fat_g":20,"carbs_g":55},"hydration_ml":1800,"sources":["com.eufylife.android","com.fitbit.FitbitMobile","com.google.android.apps.fitness"]}
        """.trimIndent()
    }
}
