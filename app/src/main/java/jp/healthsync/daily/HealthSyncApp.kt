package jp.healthsync.daily

import android.app.Application
import androidx.work.ExistingWorkPolicy
import jp.healthsync.daily.health.HealthDailyReader
import jp.healthsync.daily.net.WebhookClient
import jp.healthsync.daily.settings.SettingsStore
import jp.healthsync.daily.sync.SyncCoordinator
import jp.healthsync.daily.work.SyncScheduler

class AppGraph(context: android.content.Context) {
    val settings = SettingsStore(context)
    val health = HealthDailyReader(context)
    val webhook = WebhookClient()
    val coordinator = SyncCoordinator(health, webhook, settings)
}

class HealthSyncApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        SyncScheduler.enqueue(this, ExistingWorkPolicy.KEEP)
    }
}
