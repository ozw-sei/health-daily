package jp.healthsync.daily.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import jp.healthsync.daily.HealthSyncApp
import jp.healthsync.daily.health.HealthAvailability
import jp.healthsync.daily.health.HealthPermissions
import jp.healthsync.daily.settings.AuthHeader
import jp.healthsync.daily.settings.SendLogEntry
import jp.healthsync.daily.settings.SyncSettings
import jp.healthsync.daily.summary.TOKYO
import jp.healthsync.daily.sync.SendResult
import jp.healthsync.daily.sync.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val webhookUrl: String = "",
    val authHeaderName: String = AuthHeader.DEFAULT_NAME,
    val authHeaderValue: String = "",
    val dirty: Boolean = false,
    val availability: HealthAvailability = HealthAvailability.Unavailable,
    val grantedCount: Int = 0,
    val requestedCount: Int = HealthPermissions.read.size,
    val backgroundGranted: Boolean? = null,
    val sending: Boolean = false,
    val logs: List<SendLogEntry> = emptyList(),
    val banner: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as HealthSyncApp).graph
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            loadSettings()
            refreshHealth()
        }
    }

    fun onWebhookChange(value: String) = _state.update { it.copy(webhookUrl = value, dirty = true) }

    fun onHeaderNameChange(value: String) = _state.update { it.copy(authHeaderName = value, dirty = true) }

    fun onHeaderValueChange(value: String) = _state.update { it.copy(authHeaderValue = value, dirty = true) }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun refreshHealth() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { graph.health.snapshot() }
            val logs = withContext(Dispatchers.IO) { graph.settings.logs() }
            _state.update {
                it.copy(
                    availability = snapshot.availability,
                    grantedCount = snapshot.grantedCount,
                    requestedCount = snapshot.requestedCount,
                    backgroundGranted = snapshot.backgroundGranted,
                    logs = logs,
                )
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        val count = granted.count { it in HealthPermissions.read }
        _state.update { it.copy(banner = "読み取り権限を $count 件許可しました") }
        refreshHealth()
    }

    fun save() {
        viewModelScope.launch {
            persist()
            _state.update { it.copy(banner = "設定を保存しました") }
        }
    }

    fun sendToday() = sendOne(LocalDate.now(TOKYO))

    fun sendYesterday() = sendOne(LocalDate.now(TOKYO).minusDays(1))

    fun sendLast7Days() {
        viewModelScope.launch {
            if (_state.value.sending) return@launch
            _state.update { it.copy(sending = true, banner = "過去7日を送信しています…") }
            persist()
            val results = withContext(Dispatchers.IO) { graph.coordinator.sendRecentDays(7) }
            val ok = results.count { it is SendResult.Ok }
            val logs = withContext(Dispatchers.IO) { graph.settings.logs() }
            _state.update {
                it.copy(
                    sending = false,
                    logs = logs,
                    banner = "過去7日: 成功 $ok / 失敗 ${results.size - ok}",
                )
            }
        }
    }

    fun permissionsToRequest(): Set<String> = HealthPermissions.requestSet(getApplication())

    private fun sendOne(date: LocalDate) {
        viewModelScope.launch {
            if (_state.value.sending) return@launch
            _state.update { it.copy(sending = true, banner = "$date を送信しています…") }
            persist()
            val result = withContext(Dispatchers.IO) { graph.coordinator.sendDate(date) }
            val logs = withContext(Dispatchers.IO) { graph.settings.logs() }
            _state.update {
                it.copy(sending = false, logs = logs, banner = result.userMessage())
            }
        }
    }

    private suspend fun persist() {
        val current = _state.value
        val settings = SyncSettings(
            webhookUrl = current.webhookUrl.trim(),
            authHeaderName = current.authHeaderName.trim(),
            authHeaderValue = current.authHeaderValue.trim(),
        )
        withContext(Dispatchers.IO) { graph.settings.save(settings) }
        _state.update {
            it.copy(
                webhookUrl = settings.webhookUrl,
                authHeaderName = settings.authHeaderName,
                authHeaderValue = settings.authHeaderValue,
                dirty = false,
            )
        }
    }

    private suspend fun loadSettings() {
        val loaded = withContext(Dispatchers.IO) { graph.settings.load() }
        _state.update { current ->
            if (current.dirty) {
                current
            } else {
                current.copy(
                    webhookUrl = loaded.webhookUrl,
                    authHeaderName = loaded.authHeaderName,
                    authHeaderValue = loaded.authHeaderValue,
                )
            }
        }
    }
}
