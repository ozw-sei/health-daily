package jp.healthsync.daily.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

data class SyncSettings(
    val webhookUrl: String,
    val authHeaderName: String,
    val authHeaderValue: String,
)

data class SendLogEntry(
    val at: String,
    val date: String,
    val status: Int?,
    val ok: Boolean,
    val detail: String,
)

/**
 * Webhook URL and the auth header live in EncryptedSharedPreferences.
 * Preference keys are AES-256-SIV and values are AES-256-GCM, with an
 * AES-256-GCM master key in the Android Keystore. The header value is never
 * written to the send log.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences by lazy { open(context.applicationContext) }

    fun load(): SyncSettings {
        val name = if (prefs.contains(KEY_HEADER_NAME)) {
            prefs.getString(KEY_HEADER_NAME, "").orEmpty()
        } else {
            AuthHeader.DEFAULT_NAME
        }
        return SyncSettings(
            webhookUrl = prefs.getString(KEY_URL, "").orEmpty(),
            authHeaderName = name,
            authHeaderValue = prefs.getString(KEY_HEADER_VALUE, "").orEmpty(),
        )
    }

    fun save(settings: SyncSettings) {
        prefs.edit()
            .putString(KEY_URL, settings.webhookUrl.trim())
            .putString(KEY_HEADER_NAME, settings.authHeaderName.trim())
            .putString(KEY_HEADER_VALUE, settings.authHeaderValue.trim())
            .commit()
    }

    fun logs(): List<SendLogEntry> {
        val raw = prefs.getString(KEY_LOG, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    SendLogEntry(
                        at = item.optString("at"),
                        date = item.optString("date"),
                        status = if (item.isNull("status")) null else item.optInt("status"),
                        ok = item.optBoolean("ok"),
                        detail = item.optString("detail"),
                    ),
                )
            }
        }
    }

    fun appendLog(entry: SendLogEntry) {
        val updated = listOf(entry) + logs()
        val array = JSONArray()
        updated.take(MAX_LOGS).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("at", item.at)
                    put("date", item.date)
                    put("status", item.status ?: JSONObject.NULL)
                    put("ok", item.ok)
                    put("detail", item.detail.take(MAX_DETAIL))
                },
            )
        }
        prefs.edit().putString(KEY_LOG, array.toString()).commit()
    }

    private fun open(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val FILE_NAME = "health_daily_secure"
        const val KEY_URL = "webhook_url"
        const val KEY_HEADER_NAME = "auth_header_name"
        const val KEY_HEADER_VALUE = "auth_header_value"
        const val KEY_LOG = "send_log"
        const val MAX_LOGS = 20
        const val MAX_DETAIL = 200
    }
}
