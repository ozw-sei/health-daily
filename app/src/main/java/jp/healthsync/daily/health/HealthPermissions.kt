package jp.healthsync.daily.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
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
import kotlin.reflect.KClass

object HealthPermissions {
    val recordTypes: List<KClass<out Record>> = listOf(
        StepsRecord::class,
        DistanceRecord::class,
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        ExerciseSessionRecord::class,
        SleepSessionRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        WeightRecord::class,
        BodyFatRecord::class,
        NutritionRecord::class,
        HydrationRecord::class,
    )

    val read: Set<String> = recordTypes.map { HealthPermission.getReadPermission(it) }.toSet()

    fun requestSet(context: Context): Set<String> {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return emptySet()
        }
        val client = HealthConnectClient.getOrCreate(context)
        val permissions = read.toMutableSet()
        val background = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
        )
        if (background == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
            permissions += HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        }
        return permissions
    }
}
